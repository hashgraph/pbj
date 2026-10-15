// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.test;

import static org.junit.jupiter.api.Assertions.*;

import com.hedera.pbj.runtime.CopyBuilderTracked;
import com.hedera.pbj.runtime.DiffCodec;
import com.hedera.pbj.runtime.DiffSupport;
import com.hedera.pbj.runtime.PartialApplier;
import com.hedera.pbj.test.proto.pbj.CatalogSelectedRoot;
import com.hedera.pbj.test.proto.pbj.DiffCatalog;
import com.hedera.pbj.test.proto.pbj.DiffChild;
import com.hedera.pbj.test.proto.pbj.PlainDiffRoot;
import com.hedera.pbj.test.proto.pbj.UnrelatedDiffModel;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DiffSelectionTest {
    @Test
    void catalogSelectsRootsAndOnlyEqualityForDependenciesWithTrackingOffByDefault() throws Exception {
        assertInstanceOf(DiffCodec.class, PlainDiffRoot.PROTOBUF);
        assertInstanceOf(DiffCodec.class, CatalogSelectedRoot.PROTOBUF);
        assertFalse(CopyBuilderTracked.class.isAssignableFrom(PlainDiffRoot.class));
        assertTrue(DiffSupport.Value.class.isAssignableFrom(DiffChild.class));
        assertFalse(CopyBuilderTracked.class.isAssignableFrom(DiffChild.class));
        assertFalse(DiffCodec.class.isInstance(DiffChild.PROTOBUF));
        assertFalse(DiffCodec.class.isInstance(DiffCatalog.PROTOBUF));
        assertFalse(DiffSupport.Value.class.isAssignableFrom(UnrelatedDiffModel.class));
        assertThrows(
                NoSuchMethodException.class, () -> DiffChild.class.getMethod("diff", DiffChild.class, DiffChild.class));
        final DiffChild oldChild =
                DiffChild.newBuilder().values(Float.intBitsToFloat(0x7fc00001)).build();
        final DiffChild newChild =
                DiffChild.newBuilder().values(Float.intBitsToFloat(0x7fc00002)).build();
        final PlainDiffRoot prior = PlainDiffRoot.newBuilder()
                .scalar(oldChild)
                .repeatedChild(List.of(oldChild))
                .byName(Map.of("x", oldChild))
                .build();
        final PlainDiffRoot next = prior.copyBuilder()
                .scalar(newChild)
                .repeatedChild(List.of(newChild))
                .byName(Map.of("x", newChild))
                .build();
        assertEquals(7L, PlainDiffRoot.diff(prior, next));
        final DiffCodec<PlainDiffRoot> codec = (DiffCodec<PlainDiffRoot>) PlainDiffRoot.PROTOBUF;
        assertTrue(codec.canDiff(prior));
        assertFalse(codec.canDiff(null));
        final Object mask = codec.diff(prior, next);
        final PlainDiffRoot partial = codec.partial(next, mask);
        final int[] clears = codec.clearedFields(prior, next, mask);
        assertEquals(
                PlainDiffRoot.PROTOBUF.toBytes(next),
                PartialApplier.splice(
                        PlainDiffRoot.PROTOBUF.toBytes(prior), PlainDiffRoot.PROTOBUF.toBytes(partial), clears));
        assertTrue(codec.applyPartial(prior, partial, clears).$equalsForDiff(next));
    }

    @Test
    void olderImportedModelsCannotHideDistinctWireValuesBehindEquals() {
        // Represents a model from a dependency compiled without encoding-aware equality.
        record OldModel(float value) {}
        final OldModel a = new OldModel(Float.intBitsToFloat(0x7fc00001));
        final OldModel b = new OldModel(Float.intBitsToFloat(0x7fc00002));
        assertEquals(a, b);
        assertFalse(DiffSupport.equal(a, b));
        assertTrue(DiffSupport.equal(a, a));
    }
}
