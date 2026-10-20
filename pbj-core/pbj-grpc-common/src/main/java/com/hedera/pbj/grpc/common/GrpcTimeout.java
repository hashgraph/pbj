// SPDX-License-Identifier: Apache-2.0
package com.hedera.pbj.grpc.common;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Parses the value of the {@code grpc-timeout} header. The specification defines it as:
 *
 * <pre>
 *      Timeout → "grpc-timeout" TimeoutValue TimeoutUnit
 *      TimeoutValue → {positive integer as ASCII string of at most 8 digits}
 *      TimeoutUnit → Hour / Minute / Second / Millisecond / Microsecond / Nanosecond
 *             Hour → "H"
 *             Minute → "M"
 *             Second → "S"
 *             Millisecond → "m"
 *             Microsecond → "u"
 *             Nanosecond → "n"
 * </pre>
 */
public final class GrpcTimeout {
    /** The name of the header carrying the timeout. */
    public static final String HEADER_NAME = "grpc-timeout";

    /** The regular expression used to parse the grpc-timeout header. */
    private static final Pattern GRPC_TIMEOUT_PATTERN = Pattern.compile("(\\d{1,8})([HMSmun])");

    private GrpcTimeout() {}

    /**
     * Parses a {@code grpc-timeout} header value.
     *
     * @param timeout the header value
     * @return the timeout in nanoseconds, or empty if the value is not valid, in which case the timeout should be
     *     ignored
     */
    @NonNull
    public static OptionalLong parseNanos(@NonNull final String timeout) {
        final var matcher = GRPC_TIMEOUT_PATTERN.matcher(timeout);
        if (!matcher.matches()) {
            return OptionalLong.empty();
        }
        final var num = Integer.parseInt(matcher.group(1));
        final var unit =
                switch (matcher.group(2)) {
                    case "H" -> TimeUnit.HOURS;
                    case "M" -> TimeUnit.MINUTES;
                    case "S" -> TimeUnit.SECONDS;
                    case "m" -> TimeUnit.MILLISECONDS;
                    case "u" -> TimeUnit.MICROSECONDS;
                    // The pattern only allows "n" here
                    default -> TimeUnit.NANOSECONDS;
                };
        return OptionalLong.of(TimeUnit.NANOSECONDS.convert(num, unit));
    }
}
