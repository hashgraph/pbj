package com.hedera.hashgraph.protoparse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.List;

public interface ParseListener {
	default void intField(int fieldNum, int value) {

	}

	default void longField(int fieldNum, long value) {

	}

	default void booleanField(int fieldNum, boolean value) {

	}

	default void enumField(int fieldNum, int ordinal) {

	}

	default void floatField(int fieldNum, float value) {

	}

	default void doubleField(int fieldNum, double value) {

	}

	default void objectField(int fieldNum, InputStream protoStream) throws IOException, MalformedProtobufException {
	}

	default void stringField(int fieldNum, String value) {

	}

	default void bytesField(int fieldNum, ByteBuffer value) {

	}

	default void intList(int fieldNum, List<Integer> value) {

	}

	default void longList(int fieldNum, List<Long> value) {

	}

	default void booleanList(int fieldNum, List<Boolean> value) {

	}

	default void floatList(int fieldNum, List<Float> value) {

	}

	default void doubleList(int fieldNum, List<Double> value) {

	}

	default void enumList(int fieldNum, List<Integer> value) {

	}
}
