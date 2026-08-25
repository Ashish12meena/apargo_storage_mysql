package com.aigreentick.services.storage.application.port.in.result;

public record QuotaView(long orgId, Long projectId, long maxBytes, long usedBytes,
                        long remainingBytes, double utilisation) {
}
