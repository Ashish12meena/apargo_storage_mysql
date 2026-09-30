package com.apargo.services.storage.application.port.in.command;

import com.apargo.services.storage.domain.media.MediaId;
import com.apargo.services.storage.domain.shared.Actor;
import com.apargo.services.storage.domain.shared.TenantRef;

public record RestoreMediaCommand(MediaId mediaId, TenantRef tenant, Actor actor) {
}
