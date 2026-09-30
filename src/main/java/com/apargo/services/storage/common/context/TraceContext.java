package com.apargo.services.storage.common.context;

import com.apargo.services.storage.common.constants.MdcKeys;
import org.slf4j.MDC;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * W3C trace-context helpers (https://www.w3.org/TR/trace-context/).
 *
 * <p>The trace id is backend-only: created by the backend, carried in the MDC,
 * returned as {@code meta.traceId}, forwarded to internal services as
 * {@code traceparent}, and written to audit events. It is never returned as a
 * response header and never sent to external providers.
 */
public final class TraceContext {

    /** {@code traceparent} request header name. */
    public static final String TRACEPARENT_HEADER = "traceparent";

    private static final String VERSION = "00";
    private static final String FLAGS_SAMPLED = "01";
    private static final String INVALID_TRACE_ID = "0".repeat(32);
    private static final String INVALID_SPAN_ID = "0".repeat(16);

    private static final Pattern TRACE_ID = Pattern.compile("^[0-9a-f]{32}$");
    private static final Pattern TRACEPARENT =
            Pattern.compile("^([0-9a-f]{2})-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})$");

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private TraceContext() {
    }

    /** A new random trace id: 32 lower-case hex characters, never all zeros. */
    public static String newTraceId() {
        String id;
        do {
            id = randomHex(16);
        } while (INVALID_TRACE_ID.equals(id));
        return id;
    }

    /** A new random span id: 16 lower-case hex characters, never all zeros. */
    public static String newSpanId() {
        String id;
        do {
            id = randomHex(8);
        } while (INVALID_SPAN_ID.equals(id));
        return id;
    }

    /** The trace id of a well-formed {@code traceparent}, or empty. */
    public static Optional<String> traceIdFrom(String traceparent) {
        if (traceparent == null) {
            return Optional.empty();
        }
        var matcher = TRACEPARENT.matcher(traceparent.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String version = matcher.group(1);
        String traceId = matcher.group(2);
        String spanId = matcher.group(3);
        if ("ff".equals(version) || INVALID_TRACE_ID.equals(traceId) || INVALID_SPAN_ID.equals(spanId)) {
            return Optional.empty();
        }
        return Optional.of(traceId);
    }

    /** A {@code traceparent} value for an outbound call or message in this trace. */
    public static String traceparent(String traceId) {
        return VERSION + "-" + traceId + "-" + newSpanId() + "-" + FLAGS_SAMPLED;
    }

    public static boolean isValidTraceId(String traceId) {
        return traceId != null && TRACE_ID.matcher(traceId).matches() && !INVALID_TRACE_ID.equals(traceId);
    }

    /** The trace id of the current request or job, or null outside one. */
    public static String currentTraceIdOrNull() {
        return MDC.get(MdcKeys.TRACE_ID);
    }

    private static String randomHex(int bytes) {
        byte[] buffer = new byte[bytes];
        RANDOM.nextBytes(buffer);
        return HEX.formatHex(buffer);
    }
}
