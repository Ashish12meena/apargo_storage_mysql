package com.apargo.services.storage.common.audit;

import com.apargo.platform.contract.audit.AuditActorDto;
import com.apargo.platform.contract.audit.AuditChannel;
import com.apargo.platform.contract.identity.ActorType;
import com.apargo.services.storage.common.constants.MdcKeys;
import com.apargo.services.storage.common.context.TraceContext;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * The single owner of the audit actor, channel, request id and trace id.
 * Business code never reads headers or the MDC for audit; the event factory asks
 * this class.
 *
 * <p>Resolution, first match wins (Audit Events Implementation Guide §6):
 * <pre>
 *   scheduled job (jobName)                    SYSTEM  / job name   WORKER
 *   X-User-Id present                          USER    / user id    WEB  (WORKER on a pool thread)
 *   /internal/** call, no user (caller)        SERVICE / caller     API  (WORKER on a pool thread)
 *   anything else                              SERVICE / unknown    API  (WORKER on a pool thread)
 * </pre>
 *
 * <p>{@code ip} and {@code userAgent} stay null: the address this service sees
 * is the gateway's, and forwarded headers are caller-controlled. They are filled
 * in once the gateway provides trusted values.
 */
@Component
@Slf4j
public class AuditContextProvider {

    /** Actor id when nothing identifies the caller. */
    public static final String UNKNOWN_ACTOR = "unknown";

    public AuditContext current() {
        String traceId = MDC.get(MdcKeys.TRACE_ID);
        if (!TraceContext.isValidTraceId(traceId)) {
            // Every request and job sets one; reaching here means work ran outside
            // both. The event still needs a trace id to be valid.
            traceId = TraceContext.newTraceId();
            log.debug("audit context had no trace id; generated {}", traceId);
        }
        String requestId = blankToNull(MDC.get(MdcKeys.REQUEST_ID));
        boolean worker = Boolean.parseBoolean(MDC.get(MdcKeys.WORKER));

        String jobName = blankToNull(MDC.get(MdcKeys.JOB_NAME));
        if (jobName != null) {
            return new AuditContext(AuditActorDto.of(ActorType.SYSTEM, jobName), AuditChannel.WORKER,
                    requestId, traceId, null, null);
        }

        String userId = blankToNull(MDC.get(MdcKeys.USER_ID));
        if (userId != null) {
            return new AuditContext(AuditActorDto.of(ActorType.USER, userId),
                    worker ? AuditChannel.WORKER : AuditChannel.WEB, requestId, traceId, null, null);
        }

        String internalCaller = blankToNull(MDC.get(MdcKeys.INTERNAL_CALLER));
        String serviceId = internalCaller != null ? internalCaller : UNKNOWN_ACTOR;
        return new AuditContext(AuditActorDto.of(ActorType.SERVICE, serviceId),
                worker ? AuditChannel.WORKER : AuditChannel.API, requestId, traceId, null, null);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
