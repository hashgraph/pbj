package sample.target.model;

import com.hedera.hashgraph.pbj.runtime.OneOf;

public record Fruits(OneOf<FruitKind> fruit) {
    public enum FruitKind {
        APPLE,
        BANANA
    }
}
