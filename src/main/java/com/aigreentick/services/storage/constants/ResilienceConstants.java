package com.aigreentick.services.storage.constants;

/**
 * Resilience4j instance names — must match application-resilience.yml keys.
 */
public final class ResilienceConstants {

    private ResilienceConstants() {}

    // WhatsApp media operations
    public static final String CB_WHATSAPP_MEDIA = "whatsappMediaCB";
    public static final String RT_WHATSAPP_MEDIA = "whatsappMediaRetry";
    public static final String RL_WHATSAPP_MEDIA = "whatsappMediaRL";

    // Facebook resumable upload operations
    public static final String CB_FB_UPLOAD = "facebookUploadCB";
    public static final String RT_FB_UPLOAD = "facebookUploadRetry";
    public static final String RL_FB_UPLOAD = "facebookUploadRL";

    // Organisation service calls
    public static final String CB_ORGANISATION = "organisationCB";
}