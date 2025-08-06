// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.integration.jmh.hashing;

import edu.umd.cs.findbugs.annotations.NonNull;

public interface HashFunction {
    long applyAsLong(@NonNull final byte[] bytes, int start, int length);
}
