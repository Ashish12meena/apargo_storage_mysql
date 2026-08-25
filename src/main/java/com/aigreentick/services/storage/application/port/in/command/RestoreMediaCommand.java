package com.aigreentick.services.storage.application.port.in.command;

import com.aigreentick.services.storage.domain.media.MediaId;
import com.aigreentick.services.storage.domain.shared.Actor;
import com.aigreentick.services.storage.domain.shared.TenantRef;

public record RestoreMediaCommand(MediaId mediaId, TenantRef tenant, Actor actor) {
}
