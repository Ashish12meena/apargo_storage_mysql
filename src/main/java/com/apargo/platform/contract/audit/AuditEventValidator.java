package com.apargo.platform.contract.audit;

import com.apargo.platform.contract.event.EventEnvironments;
import com.apargo.platform.contract.event.EventIds;
import com.apargo.platform.contract.event.EventSchemaVersion;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Checks an {@link AuditEventDto} against the contract. Returns every violation
 * rather than stopping at the first, so one log line explains the whole problem.
 *
 * <p>Pure and side-effect free; never throws for any input.
 */
public final class AuditEventValidator {

    /** Upper bound for any free-text field. */
    public static final int MAX_TEXT_LENGTH = 500;

    /** Upper bound for identifiers (ids, codes, names of services and modules). */
    public static final int MAX_ID_LENGTH = 128;

    /** Metadata is meant to be small: ids, enum names, counts, flags. */
    public static final int MAX_METADATA_ENTRIES = 30;

    /** Changes are scalar field diffs, not dumps. */
    public static final int MAX_CHANGES = 50;

    private static final Pattern EVENT_TYPE = Pattern.compile("^[A-Z][A-Z0-9]*(_[A-Z0-9]+)+$");
    private static final Pattern UPPER_NAME = Pattern.compile("^[A-Z][A-Z0-9_]*$");

    private AuditEventValidator() {
    }

    /** Every rule the event breaks; empty when it is valid. */
    public static List<String> validate(AuditEventDto event) {
        List<String> problems = new ArrayList<>();
        if (event == null) {
            problems.add("event is null");
            return problems;
        }

        if (!EventIds.isValid(event.eventId())) {
            problems.add("eventId must be a lower-case UUIDv7");
        }
        if (event.schemaVersion() == null || event.schemaVersion() != EventSchemaVersion.CURRENT) {
            problems.add("schemaVersion must be " + EventSchemaVersion.CURRENT);
        }
        requireId(problems, "sourceService", event.sourceService());
        if (!EventEnvironments.isValid(event.environment())) {
            problems.add("environment must be one of " + EventEnvironments.ALL);
        }
        if (event.module() == null || !UPPER_NAME.matcher(event.module()).matches()) {
            problems.add("module is required and must be UPPER_SNAKE_CASE");
        }
        if (event.eventType() == null || !EVENT_TYPE.matcher(event.eventType()).matches()) {
            problems.add("eventType is required and must be <ENTITY>_<PAST_VERB>");
        }
        if (event.status() == null) {
            problems.add("status is required");
        }
        if (event.orgId() == null || event.orgId() < 0) {
            problems.add("orgId is required and must be >= 0 (0 for platform-level data)");
        }
        if (event.projectId() != null && event.projectId() <= 0) {
            problems.add("projectId, when present, must be > 0");
        }
        validateActor(problems, event.actor());
        validateEntity(problems, event.entity());
        validateChanges(problems, event.changes());
        validateMetadata(problems, event.metadata());
        validateError(problems, event.status(), event.error());
        if (event.channel() == null) {
            problems.add("channel is required");
        }
        maxLength(problems, "requestId", event.requestId(), MAX_ID_LENGTH);
        requireId(problems, "traceId", event.traceId());
        maxLength(problems, "ip", event.ip(), MAX_ID_LENGTH);
        maxLength(problems, "userAgent", event.userAgent(), MAX_TEXT_LENGTH);
        if (event.occurredAt() == null) {
            problems.add("occurredAt is required");
        }
        return problems;
    }

    public static boolean isValid(AuditEventDto event) {
        return validate(event).isEmpty();
    }

    private static void validateActor(List<String> problems, AuditActorDto actor) {
        if (actor == null) {
            problems.add("actor is required");
            return;
        }
        if (actor.type() == null) {
            problems.add("actor.type is required");
        }
        requireId(problems, "actor.id", actor.id());
        maxLength(problems, "actor.name", actor.name(), MAX_TEXT_LENGTH);
        maxLength(problems, "actor.impersonatorId", actor.impersonatorId(), MAX_ID_LENGTH);
    }

    private static void validateEntity(List<String> problems, AuditEntityDto entity) {
        if (entity == null) {
            return;
        }
        if (entity.type() == null || !UPPER_NAME.matcher(entity.type()).matches()) {
            problems.add("entity.type is required and must be UPPER_SNAKE_CASE");
        }
        requireId(problems, "entity.id", entity.id());
        maxLength(problems, "entity.name", entity.name(), MAX_TEXT_LENGTH);
    }

    private static void validateChanges(List<String> problems, List<AuditChangeDto> changes) {
        if (changes == null) {
            return;
        }
        if (changes.size() > MAX_CHANGES) {
            problems.add("changes has more than " + MAX_CHANGES + " entries");
        }
        for (int i = 0; i < changes.size(); i++) {
            AuditChangeDto change = changes.get(i);
            if (change == null || isBlank(change.field())) {
                problems.add("changes[" + i + "].field is required");
                continue;
            }
            scalar(problems, "changes[" + i + "].oldValue", change.oldValue());
            scalar(problems, "changes[" + i + "].newValue", change.newValue());
        }
    }

    private static void validateMetadata(List<String> problems, Map<String, Object> metadata) {
        if (metadata == null) {
            return;
        }
        if (metadata.size() > MAX_METADATA_ENTRIES) {
            problems.add("metadata has more than " + MAX_METADATA_ENTRIES + " entries");
        }
        metadata.forEach((key, value) -> {
            if (isBlank(key)) {
                problems.add("metadata key must not be blank");
            } else {
                scalar(problems, "metadata." + key, value);
            }
        });
    }

    private static void validateError(List<String> problems, AuditEventStatus status, AuditErrorDto error) {
        if (status == AuditEventStatus.FAILURE && error == null) {
            problems.add("error is required when status is FAILURE");
            return;
        }
        if (status == AuditEventStatus.SUCCESS && error != null) {
            problems.add("error must be absent when status is SUCCESS");
            return;
        }
        if (error == null) {
            return;
        }
        if (error.category() == null) {
            problems.add("error.category is required");
        }
        requireId(problems, "error.code", error.code());
        maxLength(problems, "error.message", error.message(), MAX_TEXT_LENGTH);
        maxLength(problems, "error.reference", error.reference(), MAX_ID_LENGTH);
        if (error.details() != null) {
            error.details().forEach((key, value) -> scalar(problems, "error.details." + key, value));
        }
    }

    private static void scalar(List<String> problems, String field, Object value) {
        if (value == null || value instanceof Number || value instanceof Boolean || value instanceof Enum<?>) {
            return;
        }
        if (value instanceof CharSequence text) {
            maxLength(problems, field, text.toString(), MAX_TEXT_LENGTH);
            return;
        }
        problems.add(field + " must be a scalar (text, number, boolean or enum)");
    }

    private static void requireId(List<String> problems, String field, String value) {
        if (isBlank(value)) {
            problems.add(field + " is required");
        } else {
            maxLength(problems, field, value, MAX_ID_LENGTH);
        }
    }

    private static void maxLength(List<String> problems, String field, String value, int max) {
        if (value != null && value.length() > max) {
            problems.add(field + " is longer than " + max + " characters");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
