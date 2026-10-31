// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.compiler.impl;

import com.hedera.pbj.compiler.impl.grammar.Protobuf3Parser.OptionCommentContext;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The fast code generation options set at one level: a message comment, a file comment, or the Gradle project. Each
 * option is {@code null} when the level does not set it.
 *
 * <p>Options resolve, for each message, from the message's comments, then its file's comments, then the project, then
 * the built-in default. At each level a specific option wins over {@code performance}, which sets all of them: to their
 * fastest values for {@link Performance#MAX}, and to their defaults for {@link Performance#DEFAULT}.
 *
 * @param fastParse generate a byte-array parser with in-order fast paths ({@code pbj.fast_parse})
 * @param fastWrite generate a back-to-front writer into a reusable buffer ({@code pbj.fast_write})
 * @param views generate zero-copy views over encoded bytes ({@code pbj.views})
 * @param oneofStorage how the model stores oneof fields ({@code pbj.oneof_storage})
 * @param performance shorthand for all four ({@code pbj.performance})
 */
public record PerformanceOptions(
        @Nullable Boolean fastParse,
        @Nullable Boolean fastWrite,
        @Nullable Boolean views,
        @Nullable OneofStorage oneofStorage,
        @Nullable Performance performance) {

    /** Comment option name for {@link #fastParse()}. */
    public static final String FAST_PARSE = "pbj.fast_parse";
    /** Comment option name for {@link #fastWrite()}. */
    public static final String FAST_WRITE = "pbj.fast_write";
    /** Comment option name for {@link #views()}. */
    public static final String VIEWS = "pbj.views";
    /** Comment option name for {@link #oneofStorage()}. */
    public static final String ONEOF_STORAGE = "pbj.oneof_storage";
    /** Comment option name for {@link #performance()}. */
    public static final String PERFORMANCE = "pbj.performance";

    /** A level that sets nothing. */
    public static final PerformanceOptions NONE = new PerformanceOptions(null, null, null, null, null);

    /** How a model stores oneof fields. */
    public enum OneofStorage {
        /** A {@code OneOf} object per oneof field, as PBJ always has. */
        BOXED,
        /** A kind field and a value field, so parsing and construction allocate no {@code OneOf}. */
        UNBOXED
    }

    /** Shorthand for every option. */
    public enum Performance {
        /** Every option at its default. */
        DEFAULT,
        /** Every option on. */
        MAX
    }

    /**
     * Read the options set by a list of option comments, from a file or a message.
     *
     * @param comments the option comments
     * @param where the file or message they belong to, for error messages
     * @return the options they set
     * @throws PbjCompilerException if an option has an invalid value
     */
    public static PerformanceOptions fromComments(final List<OptionCommentContext> comments, final String where) {
        final Map<String, String> values = LookupHelper.optionCommentValues(comments);
        return of(
                values.get(FAST_PARSE),
                values.get(FAST_WRITE),
                values.get(VIEWS),
                values.get(ONEOF_STORAGE),
                values.get(PERFORMANCE),
                where);
    }

    /**
     * Parse options from their text values, as written in comments or the Gradle {@code pbj { }} block. A
     * {@code null} value leaves that option unset.
     *
     * @param fastParse "true", "false" or null
     * @param fastWrite "true", "false" or null
     * @param views "true", "false" or null
     * @param oneofStorage "boxed", "unboxed" or null
     * @param performance "default", "max" or null
     * @param where the file, message or project the values come from, for error messages
     * @return the options
     * @throws PbjCompilerException if a value is invalid
     */
    public static PerformanceOptions of(
            @Nullable final String fastParse,
            @Nullable final String fastWrite,
            @Nullable final String views,
            @Nullable final String oneofStorage,
            @Nullable final String performance,
            final String where) {
        return new PerformanceOptions(
                bool(FAST_PARSE, fastParse, where),
                bool(FAST_WRITE, fastWrite, where),
                bool(VIEWS, views, where),
                choice(ONEOF_STORAGE, oneofStorage, OneofStorage.class, where),
                choice(PERFORMANCE, performance, Performance.class, where));
    }

    private static @Nullable Boolean bool(final String option, @Nullable final String value, final String where) {
        if (value == null) return null;
        return switch (value.trim()) {
            case "true" -> Boolean.TRUE;
            case "false" -> Boolean.FALSE;
            default ->
                throw new PbjCompilerException(option + " must be true or false, not \"" + value + "\", in " + where);
        };
    }

    private static <E extends Enum<E>> @Nullable E choice(
            final String option, @Nullable final String value, final Class<E> type, final String where) {
        if (value == null) return null;
        for (final E constant : type.getEnumConstants()) {
            if (constant.name().toLowerCase(java.util.Locale.ROOT).equals(value.trim())) {
                return constant;
            }
        }
        final List<String> allowed = java.util.Arrays.stream(type.getEnumConstants())
                .map(c -> "\"" + c.name().toLowerCase(java.util.Locale.ROOT) + "\"")
                .toList();
        throw new PbjCompilerException(option + " must be one of " + allowed + ", not \"" + value + "\", in " + where);
    }

    /**
     * Resolve one option over levels, most specific first: the first level that sets the option, or sets
     * {@code performance}, decides it.
     *
     * @param levels the levels, most specific first
     * @param option reads the option from a level
     * @param max the option's value for {@link Performance#MAX}
     * @param builtIn the option's default
     * @return the resolved value
     * @param <T> the option's type
     */
    public static <T> T resolve(
            final List<PerformanceOptions> levels,
            final Function<PerformanceOptions, T> option,
            final T max,
            final T builtIn) {
        for (final PerformanceOptions level : levels) {
            final T value = option.apply(level);
            if (value != null) return value;
            if (level.performance() != null) return level.performance() == Performance.MAX ? max : builtIn;
        }
        return builtIn;
    }
}
