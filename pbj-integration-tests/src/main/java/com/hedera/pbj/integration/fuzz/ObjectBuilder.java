package com.hedera.pbj.integration.fuzz;

import com.hedera.pbj.runtime.EnumWithProtoMetadata;
import com.hedera.pbj.runtime.OneOf;
import com.hedera.pbj.runtime.io.buffer.Bytes;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A factory for Protobuf model objects.
 *
 * The implementation uses Java Reflection to fetch a constructor,
 * assuming there's only a single one, and then builds a list of
 * argument values using random numbers generator. The arguments
 * list branches for every found OneOf field.
 *
 * The factory returns a list of random objects for every branch.
 *
 * For example, an
 *
 *     AccountID(long shardNum, long realmNum,
 *         OneOf<AccountID.AccountOneOfType> account)
 *
 * will yield 2 random objects, one for each branch declared in
 * the AccountOneOfType enum.
 *
 * A support for field types is currently limited to just a few types.
 * We will be adding support for more field types as we use this
 * factory for more and more models. The factory throws an exception
 * when it encounters an unsupported type, so it's easy to detect this
 * and then add support for new types in the future.
 */
public class ObjectBuilder {
    private static final Random RND = new Random();

    /**
     * Takes a Protobuf model class as an argument and return a list
     * of random objects of this class for every possible OneOf branch.
     * If the class doesn't have a single OneOf field, then a list
     * with a single random object is returned.
     *
     * A random object is defined as an object obtained by calling
     * the class' constructor and passing random values for its
     * arguments.
     */
    public static <T> List<T> build(Class<T> clz) {
        try {
            Constructor<?>[] constructors = clz.getConstructors();
            if (constructors.length > 1) {
                throw new FuzzTestException("Multiple constructors are not supported for "
                        + clz.getName() + " : " + constructors);
            }

            Constructor<T> ctor = (Constructor<T>) constructors[0];
            Class<?>[] parameterTypes = ctor.getParameterTypes();
            if (parameterTypes.length == 0) {
                throw new FuzzTestException("The model class doesn't have any fields: " + clz.getName());
            }

            Type[] genericTypes = ctor.getGenericParameterTypes();

            // For OneOf types, we'll branch the list of arguments in place
            List<List> arguments = new ArrayList<>();
            arguments.add(new ArrayList<>());

            for (int i = 0; i < parameterTypes.length; i++) {
                BranchingValue values = BranchingValue.from(parameterTypes[i], genericTypes[i], clz);

                if (values.values().size() == 1) {
                    for (int j = 0; j < arguments.size(); j++) {
                        arguments.get(j).add(values.values().get(0));
                    }
                } else {
                    // branch "in place" by building a new arguments list...
                    List<List> newArguments = new ArrayList<>();
                    for (int j = 0; j < arguments.size(); j++) {
                        List list = arguments.get(j);

                        for (int k = 0; k < values.values().size(); k++) {
                            List newList = new ArrayList(list);
                            newList.add(values.values().get(k));
                            newArguments.add(newList);
                        }
                    }

                    // ...and then replacing it:
                    arguments = newArguments;
                }
            }

            // Finally, we've built lists of arguments lists for every model variation
            // based on its OneOf fields. Now construct a new model object for each
            // arguments' list:
            List<T> objects = new ArrayList<>();
            for (int j = 0; j < arguments.size(); j++) {
                objects.add(ctor.newInstance(arguments.get(j).toArray()));
            }

            return objects;
        } catch (InstantiationException
                 | IllegalAccessException
                 | InvocationTargetException
                 | NoSuchMethodException e
        ) {
            throw new FuzzTestException("Java Reflection failed", e);
        }
    }

    /**
     * A helper class that represents one or several possible values
     * for a given field type.
     */
    private static record BranchingValue(List<?> values) {

        static BranchingValue from(Class<?> argClz, Type argType, Class<?> objClz)
                throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
            if (argClz == long.class || argClz == Long.class) {
                return new BranchingValue(List.of(RND.nextLong()));
            } else if (argClz == Bytes.class) {
                int size = 1 + RND.nextInt(31);
                byte[] bytes = new byte[size];
                for (int i = 0; i < bytes.length; i++) {
                    bytes[i] = (byte) RND.nextInt(256);
                }
                return new BranchingValue(List.of(Bytes.wrap(bytes)));
            } else if (argClz == OneOf.class) {
                ParameterizedType pType = (ParameterizedType) argType;
                Class<?> oneOfClz = (Class<?>) pType.getActualTypeArguments()[0];
                // oneOfClz is an enum that implements com.hedera.pbj.runtime.EnumWithProtoMetadata.
                // For example, the AccountID.AccountOneOfType enum does that.
                List branches = new ArrayList();
                for (Object obj : oneOfClz.getEnumConstants()) {
                    EnumWithProtoMetadata oneOfEnum = (EnumWithProtoMetadata) obj;
                    if (oneOfEnum.protoOrdinal() <= 0) {
                        // UNSET variation. We don't support this branch
                        // because in many cases this results in an invalid object.
                        continue;
                    }

                    // Fetch a getter from the main model class for this OneOf variation field.
                    Method fieldGetter = objClz.getDeclaredMethod(snakeToCamelCase(oneOfEnum.protoName()));
                    Class<?> returnType = fieldGetter.getReturnType();

                    // Call itself recursively to get a random field value for this OneOf variation.
                    // Note that we don't support nested OneOf's currently.
                    BranchingValue branchValue = BranchingValue.from(returnType, null, null);
                    if (branchValue.values().size() > 1) {
                        throw new FuzzTestException("Nested branching is not supported. Got: " + branchValue);
                    }
                    branches.add(new OneOf<>(oneOfEnum, branchValue.values().get(0)));
                }

                return new BranchingValue(branches);
            }

            // We get here if we don't currently support the type of the field, which is represented by argClz.
            // We'll need to add an extra if/else branch above to handle this new type.
            throw new FuzzTestException("Unsupported value type with argClz = "
                    + argClz + "  , type = " + argType + "  for objClz = " + objClz);
        }

        /** Convert some_name to someName. */
        private static String snakeToCamelCase(final String snakeName) {
            StringBuilder sb = new StringBuilder();

            for (int i = 0; i < snakeName.length(); i++) {
                if (snakeName.charAt(i) == '_') {
                    i++;
                    if (i < snakeName.length()) {
                        sb.append(Character.toUpperCase(snakeName.charAt(i)));
                    }
                } else {
                    sb.append(snakeName.charAt(i));
                }
            }

            return sb.toString();
        }

    }
}
