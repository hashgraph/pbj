// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test;

import com.google.protobuf.CodedOutputStream;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.test.NoToStringWrapper;
import com.hedera.pbj.test.proto.pbj.TimestampTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import static com.hedera.pbj.runtime.ProtoTestTools.INTEGER_TESTS_LIST;
import static com.hedera.pbj.runtime.ProtoTestTools.LONG_TESTS_LIST;
import static com.hedera.pbj.runtime.ProtoTestTools.getThreadLocalByteBuffer;
import static com.hedera.pbj.runtime.ProtoTestTools.getThreadLocalDataBuffer;
import static com.hedera.pbj.runtime.ProtoTestTools.getThreadLocalDataBuffer2;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit Test for TimestampTest model object. Generate based on protobuf schema.
 */
public final class TimestampTestTest {
	@ParameterizedTest
	@MethodSource("createModelTestArguments")
	public void testTimestampTestAgainstProtoC(final NoToStringWrapper<TimestampTest> modelObjWrapper) throws Exception {
		final TimestampTest modelObj = modelObjWrapper.getValue();
		// get reusable thread buffers
		final BufferedData dataBuffer = getThreadLocalDataBuffer();
		final BufferedData dataBuffer2 = getThreadLocalDataBuffer2();
		final ByteBuffer byteBuffer = getThreadLocalByteBuffer();

		// model to bytes with PBJ
		TimestampTest.PROTOBUF.write(modelObj,dataBuffer);
		// clamp limit to bytes written
		dataBuffer.limit(dataBuffer.position());

		// copy bytes to ByteBuffer
		dataBuffer.resetPosition();
		dataBuffer.readBytes(byteBuffer);
		byteBuffer.flip();

		// read proto bytes with ProtoC to make sure it is readable and no parse exceptions are thrown
		final com.hedera.pbj.test.proto.java.TimestampTest protoCModelObj = com.hedera.pbj.test.proto.java.TimestampTest.parseFrom(byteBuffer);

		// read proto bytes with PBJ parser
		dataBuffer.resetPosition();
		final TimestampTest modelObj2 = TimestampTest.PROTOBUF.parse(dataBuffer);

		// check the read back object is equal to written original one
		//assertEquals(modelObj.toString(), modelObj2.toString());
		assertEquals(modelObj, modelObj2);

		// model to bytes with ProtoC writer
		byteBuffer.clear();
		final CodedOutputStream codedOutput = CodedOutputStream.newInstance(byteBuffer);
		protoCModelObj.writeTo(codedOutput);
		codedOutput.flush();
		byteBuffer.flip();
		// copy to a data buffer
		dataBuffer2.writeBytes(byteBuffer);
		dataBuffer2.flip();

		// compare written bytes
		assertEquals(dataBuffer, dataBuffer2);

		// parse those bytes again with PBJ
		dataBuffer2.resetPosition();
		final TimestampTest modelObj3 = TimestampTest.PROTOBUF.parse(dataBuffer2);
		assertEquals(modelObj, modelObj3);
	}

	/**
	 * List of all valid arguments for testing, built as a static list, so we can reuse it.
	 */
	public static final List<TimestampTest> ARGUMENTS;

	static {
		final var secondsList = LONG_TESTS_LIST;
		final var nanosList = INTEGER_TESTS_LIST;
		// work out the longest of all the lists of args as that is how many test cases we need
		final int maxValues = IntStream.of(
				secondsList.size(),
				nanosList.size()
		).max().getAsInt();
		// create new stream of model objects using lists above as constructor params
		ARGUMENTS = IntStream.range(0,maxValues)
				.mapToObj(i -> new TimestampTest(
						secondsList.get(Math.min(i, secondsList.size()-1)),
						nanosList.get(Math.min(i, nanosList.size()-1))
				)).toList();
	}

	/**
	 * Create a stream of all test permutations of the TimestampTest class we are testing. This is reused by other tests
	 * as well that have model objects with fields of this type.
	 *
	 * @return stream of model objects for all test cases
	 */
	public static Stream<NoToStringWrapper<TimestampTest>> createModelTestArguments() {
		return ARGUMENTS.stream().map(NoToStringWrapper::new);
	}

}
