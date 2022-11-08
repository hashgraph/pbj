package sample.target.model;

import com.hedera.hashgraph.protoparse.OneOf;

public record Fruits(OneOf<FruitKind, Object> fruit) {
    public enum FruitKind {
        APPLE,
        BANANA
    }
}
