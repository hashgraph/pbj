package sample.target.model;

import com.hedera.hashgraph.protoparse.EnumWithProtoOrdinal;

public enum Suit implements EnumWithProtoOrdinal {
	ACES, SPADES, CLUBS, DIAMONDS;

	@Override
	public int protoOrdinal() {
		return ordinal();
	}

	public static Suit fromOrdinal(final int ordinal) {
		return switch (ordinal) {
			case 0 -> ACES;
			case 1 -> SPADES;
			case 2 -> CLUBS;
			case 3 -> DIAMONDS;
			default -> throw new AssertionError("Test error, cannot happen");
		};
	}
}
