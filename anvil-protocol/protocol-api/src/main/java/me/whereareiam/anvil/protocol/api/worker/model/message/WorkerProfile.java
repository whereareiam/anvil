package me.whereareiam.anvil.protocol.api.worker.model.message;

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
	/**
	 * Minecraft username the player logs in with.
	 */
	@NotNull String name;
	/**
	 * Profile UUID the player logs in with; offline, the one derived from the username.
	 */
	@NotNull UUID uniqueId;
}
