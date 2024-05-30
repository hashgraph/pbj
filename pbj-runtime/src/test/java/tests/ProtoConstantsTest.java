package tests;

import com.hedera.pbj.runtime.ProtoConstants;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

public class ProtoConstantsTest {

    @ParameterizedTest
    @EnumSource(ProtoConstants.class)
    void protoConstantsGetTest(final ProtoConstants c) {
        final int ord = c.ordinal();
        Assertions.assertEquals(c, ProtoConstants.get(ord));
        Assertions.assertEquals(ProtoConstants.values()[ord], ProtoConstants.get(ord));
    }
}
