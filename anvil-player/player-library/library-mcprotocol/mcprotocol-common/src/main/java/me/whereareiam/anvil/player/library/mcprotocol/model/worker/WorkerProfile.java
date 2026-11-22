package me.whereareiam.anvil.player.library.mcprotocol.model.worker;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Resolved profile a worker player logs in with.
 */
@Value
@Builder
@Jacksonized
public class WorkerProfile {
	@NotNull String name;
	@NotNull UUID uniqueId;
}
