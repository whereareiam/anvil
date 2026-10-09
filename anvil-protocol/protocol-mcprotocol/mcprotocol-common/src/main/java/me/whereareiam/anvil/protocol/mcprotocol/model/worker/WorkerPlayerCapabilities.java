package me.whereareiam.anvil.protocol.mcprotocol.model.worker;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;

/**
 * Capabilities bound for one newly created worker player, returned by its creation request.
 * A capability whose binding failed to link is listed as unavailable with the missing member.
 */
@Value
@Builder
@Jacksonized
public class WorkerPlayerCapabilities {
	/**
	 * Capabilities bound for the player.
	 */
	@Singular
	@NotNull Set<String> capabilities;
	/**
	 * Capabilities the worker cannot install, with the reason for each.
	 */
	@Singular("unavailableCapability")
	@NotNull Map<String, String> unavailable;
}
