package com.apargo.platform.contract.event;

/**
 * Kafka topics and record headers shared by all producers and consumers.
 *
 * <p>Services read the topic name from configuration ({@code audit.topics.audit})
 * and use these values only as the default.
 */
public final class EventTopics {

    /** Default topic for {@link EventCategory#AUDIT} events. */
    public static final String DEFAULT_AUDIT_TOPIC = "apargo.audit.event";

    /** Default topic for {@link EventCategory#ACCESS} events. */
    public static final String DEFAULT_ACCESS_TOPIC = "apargo.access.event";

    private EventTopics() {
    }

    /** Kafka record header names. Every value is UTF-8 text. */
    public static final class Headers {

        public static final String EVENT_ID = "eventId";
        public static final String EVENT_TYPE = "eventType";
        public static final String SCHEMA_VERSION = "schemaVersion";
        public static final String SOURCE_SERVICE = "sourceService";
        /** W3C trace context of the action that produced the event. */
        public static final String TRACEPARENT = "traceparent";

        private Headers() {
        }
    }
}
