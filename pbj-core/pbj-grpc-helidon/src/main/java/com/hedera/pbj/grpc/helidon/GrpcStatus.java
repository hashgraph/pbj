package com.hedera.pbj.grpc.helidon;

import io.helidon.http.Header;
import io.helidon.http.HeaderName;
import io.helidon.http.HeaderNames;
import io.helidon.http.HeaderValues;

/**
 * Status headers for defined grpc states.
 *
 * @see <a href="https://grpc.github.io/grpc/core/md_doc_statuscodes.html">GRPC Status codes</a>
 */
public final class GrpcStatus {
    /**
     * grpc status header name.
     */
    public static final HeaderName STATUS_NAME = HeaderNames.createFromLowercase("grpc-status");
    /**
     * grpc status message header name.
     */
    public static final HeaderName MESSAGE_NAME = HeaderNames.createFromLowercase("grpc-message");
    /**
     * The operation completed successfully.
     */
    public static final Header OK = HeaderValues.createCached(STATUS_NAME, 0);
    /**
     * The operation was cancelled (typically by the caller).
     */
    public static final Header CANCELLED = HeaderValues.createCached(STATUS_NAME, 1);
    /**
     * Unknown error.  An example of where this error may be returned is
     * if a Status value received from another address space belongs to
     * an error-space that is not known in this address space.  Also
     * errors raised by APIs that do not return enough error information
     * may be converted to this error.
     */
    public static final Header UNKNOWN = HeaderValues.createCached(STATUS_NAME, 2);
    /**
     * Client specified an invalid argument.  Note that this differs
     * from FAILED_PRECONDITION.  INVALID_ARGUMENT indicates arguments
     * that are problematic regardless of the state of the system
     * (e.g., a malformed file name).
     */
    public static final Header INVALID_ARGUMENT = HeaderValues.createCached(STATUS_NAME, 3);
    /**
     * Deadline expired before operation could complete.  For operations
     * that change the state of the system, this error may be returned
     * even if the operation has completed successfully.  For example, a
     * successful response from a server could have been delayed long
     * enough for the deadline to expire.
     */
    public static final Header DEADLINE_EXCEEDED = HeaderValues.createCached(STATUS_NAME, 4);
    /**
     * Some requested entity (e.g., file or directory) was not found.
     */
    public static final Header NOT_FOUND = HeaderValues.createCached(STATUS_NAME, 5);
    /**
     * Some entity that we attempted to create (e.g., file or directory) already exists.
     */
    public static final Header ALREADY_EXISTS = HeaderValues.createCached(STATUS_NAME, 6);
    /**
     * The caller does not have permission to execute the specified
     * operation.  PERMISSION_DENIED must not be used for rejections
     * caused by exhausting some resource (use RESOURCE_EXHAUSTED
     * instead for those errors).  PERMISSION_DENIED must not be
     * used if the caller cannot be identified (use UNAUTHENTICATED
     * instead for those errors).
     */
    public static final Header PERMISSION_DENIED = HeaderValues.createCached(STATUS_NAME, 7);
    /**
     * Some resource has been exhausted, perhaps a per-user quota, or
     * perhaps the entire file system is out of space.
     */
    public static final Header RESOURCE_EXHAUSTED = HeaderValues.createCached(STATUS_NAME, 8);
    /**
     * Operation was rejected because the system is not in a state
     * required for the operation's execution.  For example, directory
     * to be deleted may be non-empty, an rmdir operation is applied to
     * a non-directory, etc.
     *
     * <p>A litmus test that may help a service implementor in deciding
     * between FAILED_PRECONDITION, ABORTED, and UNAVAILABLE:
     * (a) Use UNAVAILABLE if the client can retry just the failing call.
     * (b) Use ABORTED if the client should retry at a higher-level
     * (e.g., restarting a read-modify-write sequence).
     * (c) Use FAILED_PRECONDITION if the client should not retry until
     * the system state has been explicitly fixed.  E.g., if an "rmdir"
     * fails because the directory is non-empty, FAILED_PRECONDITION
     * should be returned since the client should not retry unless
     * they have first fixed up the directory by deleting files from it.
     */
    public static final Header FAILED_PRECONDITION = HeaderValues.createCached(STATUS_NAME, 9);
    /**
     * The operation was aborted, typically due to a concurrency issue
     * like sequencer check failures, transaction aborts, etc.
     *
     * <p>See litmus test above for deciding between FAILED_PRECONDITION,
     * ABORTED, and UNAVAILABLE.
     */
    public static final Header ABORTED = HeaderValues.createCached(STATUS_NAME, 10);
    /**
     * Operation was attempted past the valid range.  E.g., seeking or
     * reading past end of file.
     *
     * <p>Unlike INVALID_ARGUMENT, this error indicates a problem that may
     * be fixed if the system state changes. For example, a 32-bit file
     * system will generate INVALID_ARGUMENT if asked to read at an
     * offset that is not in the range [0,2^32-1], but it will generate
     * OUT_OF_RANGE if asked to read from an offset past the current
     * file size.
     *
     * <p>There is a fair bit of overlap between FAILED_PRECONDITION and OUT_OF_RANGE.
     * We recommend using OUT_OF_RANGE (the more specific error) when it applies
     * so that callers who are iterating through
     * a space can easily look for an OUT_OF_RANGE error to detect when they are done.
     */
    public static final Header OUT_OF_RANGE = HeaderValues.createCached(STATUS_NAME, 11);
    /**
     * Operation is not implemented or not supported/enabled in this service.
     */
    public static final Header UNIMPLEMENTED = HeaderValues.createCached(STATUS_NAME, 12);
    /**
     * Internal errors.  Means some invariants expected by underlying
     * system has been broken.  If you see one of these errors,
     * something is very broken.
     */
    public static final Header INTERNAL = HeaderValues.createCached(STATUS_NAME, 13);
    /**
     * The service is currently unavailable.  This is a most likely a
     * transient condition and may be corrected by retrying with
     * a backoff. Note that it is not always safe to retry
     * non-idempotent operations.
     *
     * <p>See litmus test above for deciding between FAILED_PRECONDITION,
     * ABORTED, and UNAVAILABLE.
     */
    public static final Header UNAVAILABLE = HeaderValues.createCached(STATUS_NAME, 14);
    /**
     * Unrecoverable data loss or corruption.
     */
    public static final Header DATA_LOSS = HeaderValues.createCached(STATUS_NAME, 15);
    /**
     * The request does not have valid authentication credentials for the
     * operation.
     */
    public static final Header UNAUTHENTICATED = HeaderValues.createCached(STATUS_NAME, 16);

    private GrpcStatus() {
    }
}
