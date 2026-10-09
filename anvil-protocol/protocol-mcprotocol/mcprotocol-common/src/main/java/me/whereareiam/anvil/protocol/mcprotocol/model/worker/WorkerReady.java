package me.whereareiam.anvil.protocol.mcprotocol.model.worker;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;

/**
 * Loaded release, native protocol, selected segments and capability installation announced after worker
 * initialization. Capabilities that cannot be installed in this worker are listed with the reason for each.
 */
@Value
@Builder
@Jacksonized
public class WorkerReady implements WorkerMessage {
	/**
	 * Event name of the announcement.
	 */
	public static final String EVENT = "ready";
	/**
	 * Event name, always {@value #EVENT}.
	 */
	@Builder.Default
	@NotNull String event = EVENT;
	/**
	 * Key version of the release the worker loaded.
	 */
	@NotNull String release;
	/**
	 * Native protocol number the worker verified.
	 */
	int protocol;
	/**
	 * Start version of each selected segment, keyed by segment owner.
	 */
	@Singular
	@NotNull Map<String, String> segments;
	/**
	 * Capabilities the worker installs for new players.
	 */
	@Singular
	@NotNull Set<String> capabilities;
	/**
	 * Capabilities the worker cannot install, with the reason for each.
	 */
	@Singular("unavailableCapability")
	@NotNull Map<String, String> unavailable;
}
