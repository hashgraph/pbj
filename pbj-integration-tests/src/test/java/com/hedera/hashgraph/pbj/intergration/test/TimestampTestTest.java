package com.hedera.hashgraph.pbj.intergration.test;

import com.google.protobuf.CodedOutputStream;
import com.hedera.hashgraph.pbj.integration.NonSynchronizedByteArrayInputStream;
import com.hedera.hashgraph.pbj.runtime.io.DataBuffer;
import com.hedera.hashgraph.pbj.runtime.io.DataInputStream;
import com.hedera.hashgraph.pbj.runtime.test.NoToStringWrapper;
import com.hederahashgraph.api.proto.pbj.test.TimestampTest;
import com.hederahashgraph.api.proto.pbj.test.parser.TimestampTestProtoParser;
import com.hederahashgraph.api.proto.pbj.test.writer.TimestampTestWriter;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.hedera.hashgraph.pbj.runtime.ProtoTestTools.INTEGER_TESTS_LIST;
import static com.hedera.hashgraph.pbj.runtime.ProtoTestTools.LONG_TESTS_LIST;
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
    	final DataBuffer dataBuffer = DataBuffer.allocate(1024*1024,false);
    	final DataBuffer dataBuffer2 = DataBuffer.allocate(1024*1024,false);
    	final ByteBuffer byteBuffer = ByteBuffer.allocate(1024*1024);
    
    	// model to bytes with PBJ
    	TimestampTestWriter.write(modelObj,dataBuffer);

    	// clamp limit to bytes written and reset position
    	dataBuffer.flip();

		System.out.println("dataBuffer = " + dataBuffer.toString());
    
    	// copy bytes to ByteBuffer
    	dataBuffer.readBytes(byteBuffer, 0, (int)dataBuffer.getRemaining());
    	byteBuffer.flip();
    
    	// read proto bytes with ProtoC to make sure it is readable and no parse exceptions are thrown
    	final com.hederahashgraph.api.proto.java.test.TimestampTest protoCModelObj = com.hederahashgraph.api.proto.java.test.TimestampTest.parseFrom(byteBuffer);
    
    	// read proto bytes with PBJ parser
    	dataBuffer.resetPosition();
    	final TimestampTest modelObj2 = TimestampTestProtoParser.parse(dataBuffer);
    
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
    	final TimestampTest modelObj3 = TimestampTestProtoParser.parse(dataBuffer2);
    	assertEquals(modelObj, modelObj3);

		// test with input stream
		byteBuffer.position(0);
		byte[] protoBytes = new byte[byteBuffer.remaining()];
		byteBuffer.get(protoBytes);
		NonSynchronizedByteArrayInputStream bin = new NonSynchronizedByteArrayInputStream(protoBytes);
		TimestampTestProtoParser.parse(new DataInputStream(bin));
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
