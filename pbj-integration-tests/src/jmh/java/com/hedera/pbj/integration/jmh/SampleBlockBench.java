package com.hedera.pbj.integration.jmh;

import com.google.protobuf.CodedOutputStream;
import com.hedera.hapi.block.stream.BlockItem;
import com.hedera.hapi.block.stream.protoc.Block;
import com.hedera.pbj.integration.NonSynchronizedByteArrayInputStream;
import com.hedera.pbj.integration.NonSynchronizedByteArrayOutputStream;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.stream.ReadableStreamingData;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import java.util.Comparator;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

/**
 * Benchmarks for parsing and writing a sample block using PBJ and Google Protobuf.
 */
@SuppressWarnings("unused")
@Fork(1)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
@State(Scope.Benchmark)
public class SampleBlockBench {
	// test block
	private static final com.hedera.hapi.block.stream.Block TEST_BLOCK;
	private static final Block TEST_BLOCK_GOOGLE;
	// input bytes
	private static final byte[] TEST_BLOCK_PROTOBUF_BYTES;
	private static final ByteBuffer PROTOBUF_BYTE_BUFFER;
	private static final BufferedData PROTOBUF_DATA_BUFFER;
	private static final ByteBuffer PROTOBUF_BYTE_BUFFER_DIRECT;
	private static final BufferedData PROTOBUF_DATA_BUFFER_DIRECT;
	private static final NonSynchronizedByteArrayInputStream PROTOBUF_INPUT_STREAM;
	// load test block from resources
	static {
		// load the protobuf bytes
		try (var in = new BufferedInputStream(new GZIPInputStream(
				Objects.requireNonNull(SampleBlockBench.class.getResourceAsStream("/000000000000000000000000000000497558.blk.gz"))))) {
			TEST_BLOCK_PROTOBUF_BYTES = in.readAllBytes();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		// load using PBJ
		try {
			TEST_BLOCK = com.hedera.hapi.block.stream.Block.PROTOBUF.parse(Bytes.wrap(TEST_BLOCK_PROTOBUF_BYTES));
		} catch (ParseException e) {
			throw new RuntimeException(e);
		}
		// load using google protoc as well
		try {
			TEST_BLOCK_GOOGLE = Block.parseFrom(TEST_BLOCK_PROTOBUF_BYTES);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		// input buffers
		PROTOBUF_BYTE_BUFFER = ByteBuffer.wrap(TEST_BLOCK_PROTOBUF_BYTES);
		PROTOBUF_DATA_BUFFER = BufferedData.wrap(TEST_BLOCK_PROTOBUF_BYTES);
		PROTOBUF_BYTE_BUFFER_DIRECT = ByteBuffer.allocateDirect(TEST_BLOCK_PROTOBUF_BYTES.length);
		PROTOBUF_BYTE_BUFFER_DIRECT.put(TEST_BLOCK_PROTOBUF_BYTES);
		PROTOBUF_DATA_BUFFER_DIRECT = BufferedData.wrap(PROTOBUF_BYTE_BUFFER_DIRECT);
		PROTOBUF_INPUT_STREAM = new NonSynchronizedByteArrayInputStream(TEST_BLOCK_PROTOBUF_BYTES);
		ReadableStreamingData din = new ReadableStreamingData(PROTOBUF_INPUT_STREAM);
	}

	// output buffers
	private final NonSynchronizedByteArrayOutputStream bout = new NonSynchronizedByteArrayOutputStream();
	private final BufferedData outDataBuffer = BufferedData.allocate(TEST_BLOCK_PROTOBUF_BYTES.length);
	private final BufferedData outDataBufferDirect = BufferedData.allocateOffHeap(TEST_BLOCK_PROTOBUF_BYTES.length);
	private final ByteBuffer bbout = ByteBuffer.allocate(TEST_BLOCK_PROTOBUF_BYTES.length);
	private final ByteBuffer bboutDirect = ByteBuffer.allocateDirect(TEST_BLOCK_PROTOBUF_BYTES.length);

	/** Same as parsePbjByteBuffer because DataBuffer.wrap(byte[]) uses ByteBuffer today, added this because makes result plotting easier */
	@Benchmark
	public void parsePbjByteArray(Blackhole blackhole) throws ParseException {
		PROTOBUF_DATA_BUFFER.resetPosition();
		blackhole.consume(com.hedera.hapi.block.stream.Block.PROTOBUF.parse(PROTOBUF_DATA_BUFFER));
	}

	@Benchmark
	public void parsePbjByteBuffer(Blackhole blackhole) throws ParseException {
		PROTOBUF_DATA_BUFFER.resetPosition();
		blackhole.consume(com.hedera.hapi.block.stream.Block.PROTOBUF.parse(PROTOBUF_DATA_BUFFER));
	}

	@Benchmark
	public void parsePbjByteBufferDirect(Blackhole blackhole)
			throws ParseException {
		PROTOBUF_DATA_BUFFER_DIRECT.resetPosition();
		blackhole.consume(com.hedera.hapi.block.stream.Block.PROTOBUF.parse(PROTOBUF_DATA_BUFFER_DIRECT));
	}

	@Benchmark
	public void parsePbjInputStream(Blackhole blackhole) throws ParseException {
		PROTOBUF_INPUT_STREAM.resetPosition();
		blackhole.consume(com.hedera.hapi.block.stream.Block.PROTOBUF.parse(new ReadableStreamingData(PROTOBUF_INPUT_STREAM)));
	}

	@Benchmark
	public void parseProtoCByteArray(Blackhole blackhole) throws IOException {
		blackhole.consume(Block.parseFrom(TEST_BLOCK_PROTOBUF_BYTES));
	}

	@Benchmark
	public void parseProtoCByteBufferDirect(Blackhole blackhole) throws IOException {
		PROTOBUF_BYTE_BUFFER_DIRECT.position(0);
		blackhole.consume(Block.parseFrom(PROTOBUF_BYTE_BUFFER_DIRECT));
	}

	@Benchmark
	public void parseProtoCByteBuffer(Blackhole blackhole) throws IOException {
		blackhole.consume(Block.parseFrom(PROTOBUF_BYTE_BUFFER));
	}

	@Benchmark
	public void parseProtoCInputStream(Blackhole blackhole) throws IOException {
		PROTOBUF_INPUT_STREAM.resetPosition();
		blackhole.consume(Block.parseFrom(PROTOBUF_INPUT_STREAM));
	}

	/** Same as writePbjByteBuffer because DataBuffer.wrap(byte[]) uses ByteBuffer today, added this because makes result plotting easier */
	@Benchmark
	public void writePbjByteArray(Blackhole blackhole) throws IOException {
		outDataBuffer.reset();
		com.hedera.hapi.block.stream.Block.PROTOBUF.write(TEST_BLOCK, outDataBuffer);
		blackhole.consume(outDataBuffer);
	}

	/** Added as should be same as above but creates new byte[] and does extra measure. But this is used a lot */
	@Benchmark
	public void writePbjToBytes(Blackhole blackhole) {
		final Bytes bytes = com.hedera.hapi.block.stream.Block.PROTOBUF.toBytes(TEST_BLOCK);
		blackhole.consume(bytes);
	}

	@Benchmark
	public void writePbjByteBuffer(Blackhole blackhole) throws IOException {
		outDataBuffer.reset();
		com.hedera.hapi.block.stream.Block.PROTOBUF.write(TEST_BLOCK, outDataBuffer);
		blackhole.consume(outDataBuffer);
	}

	@Benchmark
	public void writePbjByteDirect(Blackhole blackhole) throws IOException {
		outDataBufferDirect.reset();
		com.hedera.hapi.block.stream.Block.PROTOBUF.write(TEST_BLOCK, outDataBufferDirect);
		blackhole.consume(outDataBufferDirect);
	}

	@Benchmark
	public void writePbjOutputStream(Blackhole blackhole) throws IOException {
		bout.reset();
		com.hedera.hapi.block.stream.Block.PROTOBUF.write(TEST_BLOCK, new WritableStreamingData(bout));
		blackhole.consume(bout.toByteArray());
	}

	@Benchmark
	public void writeProtoCByteArray(Blackhole blackhole) {
		blackhole.consume(TEST_BLOCK_GOOGLE.toByteArray());
	}

	@Benchmark
	public void writeProtoCByteBuffer(Blackhole blackhole) throws IOException {
		CodedOutputStream cout = CodedOutputStream.newInstance(bbout);
		TEST_BLOCK_GOOGLE.writeTo(cout);
		blackhole.consume(bbout);
	}

	@Benchmark
	public void writeProtoCByteBufferDirect(Blackhole blackhole) throws IOException {
		CodedOutputStream cout = CodedOutputStream.newInstance(bboutDirect);
		TEST_BLOCK_GOOGLE.writeTo(cout);
		blackhole.consume(bbout);
	}

	@Benchmark
	public void writeProtoCOutputStream(Blackhole blackhole) throws IOException {
		bout.reset();
		TEST_BLOCK_GOOGLE.writeTo(bout);
		blackhole.consume(bout.toByteArray());
	}

	/**
	 * Handy test main method for performance profiling
	 *
	 * @param args no args needed
	 */
	public static void main(String[] args) {
		for (int i = 0; i < 1000; i++) {
			final Bytes result = com.hedera.hapi.block.stream.Block.PROTOBUF.toBytes(TEST_BLOCK);
//			TEST_BLOCK_GOOGLE.toByteArray();
		}
//		var biggsetItem = TEST_BLOCK.items().stream().sorted(Comparator.comparingLong(BlockItem.PROTOBUF::measureRecord)).toList().getLast();
//		final Bytes result = com.hedera.hapi.block.stream.BlockItem.PROTOBUF.toBytes(biggsetItem);

	}
}
