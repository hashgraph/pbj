// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.runtime.io;

import com.hedera.pbj.runtime.FieldDefinition;
import com.hedera.pbj.runtime.FieldType;
import com.hedera.pbj.runtime.ProtoParserTools;
import com.hedera.pbj.runtime.ProtoWriterTools;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.pbj.runtime.io.stream.ReadableStreamingData;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

@SuppressWarnings("unused")
@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 4, time = 2)
@Measurement(iterations = 5, time = 2)
@OutputTimeUnit(TimeUnit.SECONDS)
@BenchmarkMode(Mode.Throughput)
public class WriteBytesBench {

	public static final FieldDefinition BYTES_FIELD = new FieldDefinition("bytesField", FieldType.BYTES, false, false, false, 17);
	final static Bytes sampleData;
	final static byte[] sampleWrittenData;

	static {
		final Random random = new Random(6262266);
		byte[] data = new byte[1024*16];
		random.nextBytes(data);
		sampleData = Bytes.wrap(data);

		ByteArrayOutputStream bout = new ByteArrayOutputStream();
		try (WritableStreamingData out = new WritableStreamingData(bout)) {
			for (int i = 0; i < 100; i++) {
				random.nextBytes(data);
				ProtoWriterTools.writeBytes(out, BYTES_FIELD, sampleData);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		sampleWrittenData = bout.toByteArray();
	}

	Path tempFileWriting;
	Path tempFileReading;
	OutputStream fout;
	WritableStreamingData dataOut;

	@Setup
	public void prepare() {
		try {
			tempFileWriting = Files.createTempFile("WriteBytesBench", "dat");
			tempFileWriting.toFile().deleteOnExit();
			fout = Files.newOutputStream(tempFileWriting);
			dataOut = new WritableStreamingData(fout);
			tempFileReading = Files.createTempFile("WriteBytesBench", "dat");
			tempFileReading.toFile().deleteOnExit();
			Files.write(tempFileReading, sampleWrittenData);
		} catch (IOException e) {
			e.printStackTrace();
			throw new UncheckedIOException(e);
		}
	}

	@TearDown
	public void cleanUp() {
		try {
			dataOut.close();
			fout.close();
		} catch (IOException e){
			e.printStackTrace();
			throw new UncheckedIOException(e);
		}
	}

	@Benchmark
	public void writeBytes(Blackhole blackhole) throws IOException {
		ProtoWriterTools.writeBytes(dataOut, BYTES_FIELD, sampleData);
	}

	@Benchmark
	@OperationsPerInvocation(100)
	public void readBytes(Blackhole blackhole) throws IOException {
		try (ReadableStreamingData in = new ReadableStreamingData(Files.newInputStream(tempFileReading)) ) {
			for (int i = 0; i < 100; i++) {
				blackhole.consume(in.readVarInt(false));
				blackhole.consume(ProtoParserTools.readBytes(in));
			}
		}
	}
}
