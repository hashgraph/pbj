package com.hedera.pbj.intergration.jmh;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import com.hedera.pbj.runtime.MalformedProtobufException;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.stream.ReadableStreamingData;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.*;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("unused")
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 4, time = 2)
@Measurement(iterations = 5, time = 2)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
public class VarIntBench {

	ByteBuffer buffer = ByteBuffer.allocate(256*1024);
	final ByteBuffer bufferDirect = ByteBuffer.allocateDirect(256*1024);
	final BufferedData dataBuffer = BufferedData.wrap(buffer);
	final BufferedData dataBufferDirect = BufferedData.wrap(bufferDirect, true);

	Bytes bytes = null;

	ByteArrayInputStream bais = null;
	ReadableStreamingData rsd = null;

	Bytes bytes = null;

	ByteArrayInputStream bais = null;
	ReadableStreamingData rsd = null;

	public VarIntBench() {
		try {
			CodedOutputStream cout = CodedOutputStream.newInstance(buffer);
			Random random = new Random(9387498731984L);
			for (int i = 0; i < 600; i++) {
				cout.writeUInt64NoTag(random.nextLong(0,128));
			}
			for (int i = 0; i < 150; i++) {
				cout.writeUInt64NoTag(random.nextLong(128,256));
			}
			for (int i = 0; i < 150; i++) {
				cout.writeUInt64NoTag(random.nextLong(256,Integer.MAX_VALUE));
			}
			for (int i = 0; i < 150; i++) {
				cout.writeUInt64NoTag(random.nextLong(0,Long.MAX_VALUE));
			}
			cout.flush();
			// copy to direct buffer
			buffer.flip();
			bufferDirect.put(buffer);
			byte[] bts = new byte[buffer.limit()];
			for (int i = 0; i < buffer.limit(); i++) {
				bts[i] = buffer.get(i);
			}
			bytes = Bytes.wrap(bts);
			bais = new ByteArrayInputStream(bts.clone());
			rsd = new ReadableStreamingData(bais);
		} catch (IOException e){
			e.printStackTrace();
		}
	}

	@Benchmark
	@OperationsPerInvocation(1050)
	public void dataBuffer(Blackhole blackhole) throws IOException {
		dataBuffer.reset();
		for (int i = 0; i < 1050; i++) {
		    int i1 = dataBuffer.readVarInt(false);
			blackhole.consume(dataBuffer.readVarInt(false));
		}
	}
	@Benchmark
	@OperationsPerInvocation(1050)
	public void dataBufferDirect(Blackhole blackhole) throws IOException {
		dataBufferDirect.reset();
		for (int i = 0; i < 1050; i++) {
			blackhole.consume(dataBufferDirect.readVarInt(false));
		}
	}

	int[] pos = new int[1];

	@Benchmark
	@OperationsPerInvocation(1050)
	public void dataBytes(Blackhole blackhole) throws IOException {
		pos[0] = 0;
		for (int i = 0; i < 1050; i++) {
			blackhole.consume(bytes.readVarInt(pos, false));
		}
	}

	@Benchmark
	@OperationsPerInvocation(1050)
	public void dataInputStream(Blackhole blackhole) throws IOException {
		bais.reset();
		for (int i = 0; i < 1050; i++) {
			blackhole.consume(rsd.readVarInt(false));
		}
	}

	@Benchmark
	@OperationsPerInvocation(1050)
	public void richard(Blackhole blackhole) throws MalformedProtobufException {
		buffer.clear();
		for (int i = 0; i < 1050; i++) {
			blackhole.consume(readVarintRichard(buffer));
		}
	}
	@Benchmark
	@OperationsPerInvocation(1050)
	public void google(Blackhole blackhole) throws IOException {
		buffer.clear();
		for (int i = 0; i < 1050; i++) {
			final CodedInputStream codedInputStream = CodedInputStream.newInstance(buffer);
			blackhole.consume(codedInputStream.readRawVarint64());
		}
	}
	@Benchmark
	@OperationsPerInvocation(1050)
	public void googleDirect(Blackhole blackhole) throws IOException {
		bufferDirect.clear();
		for (int i = 0; i < 1050; i++) {
			final CodedInputStream codedInputStream = CodedInputStream.newInstance(bufferDirect);
			blackhole.consume(codedInputStream.readRawVarint64());
		}
	}
	@Benchmark
	@OperationsPerInvocation(1050)
	public void googleSlowPath(Blackhole blackhole) throws MalformedProtobufException {
		buffer.clear();
		for (int i = 0; i < 1050; i++) {
			blackhole.consume(readRawVarint64SlowPath(buffer));
		}
	}
	@Benchmark
	@OperationsPerInvocation(1050)
	public void googleSlowPathDirect(Blackhole blackhole) throws MalformedProtobufException {
		bufferDirect.clear();
		for (int i = 0; i < 1050; i++) {
			blackhole.consume(readRawVarint64SlowPath(bufferDirect));
		}
	}
	private static long readRawVarint64SlowPath(ByteBuffer buf) throws MalformedProtobufException {
		long result = 0;
		for (int shift = 0; shift < 64; shift += 7) {
			final byte b = buf.get();
			result |= (long) (b & 0x7F) << shift;
			if ((b & 0x80) == 0) {
				return result;
			}
		}
		throw new MalformedProtobufException("Malformed varInt");
	}


	private static final int VARINT_CONTINUATION_MASK = 0b1000_0000;
	private static final int VARINT_DATA_MASK = 0b0111_1111;
	private static final int NUM_BITS_PER_VARINT_BYTE = 7;

	public static long readVarintRichard(ByteBuffer buf) throws MalformedProtobufException {
		// Protobuf encodes smaller integers with fewer bytes than larger integers. It takes a full byte
		// to encode 7 bits of information. So, if all 64 bits of a long are in use (for example, if the
		// leading bit is 1, or even all bits are 1) then it will take 10 bytes to transmit what would
		// have otherwise been 8 bytes of data!
		//
		// Thus, at most, reading a varint should involve reading 10 bytes of data.
		//
		// The leading bit of each byte is a continuation bit. If set, another byte will follow.
		// If we read 10 bytes in sequence with a continuation bit set, then we have a malformed
		// byte stream.
		// The bytes come least to most significant 7 bits. So the first byte we read represents
		// the lowest 7 bytes, then the next byte is the next highest 7 bytes, etc.

		// Keeps track of the number of bytes that have been read. If we read 10 in a row all with
		// the leading continuation bit set, then throw a malformed protobuf exception.
		int numBytesRead = 0;
		// The final value.
		long value = 0;
		// The amount to shift the bits we read by before AND with the value
		long shift = 0;
		// The byte to read from the stream
		int b;

		while ((b = buf.get()) != -1) {
			// Keep track of the number of bytes read
			numBytesRead++;
			// Checks whether the continuation bit is set
			final boolean continuationBitSet = (b & VARINT_CONTINUATION_MASK) != 0;
			// Strip off the continuation bit by keeping only the data bits
			b &= VARINT_DATA_MASK;
			// Shift the data bits left into position to AND with the value
			final long toBeAdded = (long) b << shift;
			value |= toBeAdded;
			// Increment the shift for the next data bits (if there are more bits)
			shift += NUM_BITS_PER_VARINT_BYTE;

			if (continuationBitSet) {
				// msb is set, so there is another byte following this one. If we've just read our 10th byte,
				// then we have a malformed protobuf stream
				if (numBytesRead == 10) {
					throw new MalformedProtobufException("");
				}
			} else {
				break;
			}
		}
		return value;
	}

}
