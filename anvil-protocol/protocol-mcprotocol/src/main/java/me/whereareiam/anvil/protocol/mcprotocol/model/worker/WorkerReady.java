package me.whereareiam.anvil.protocol.mcprotocol.model.worker;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Native protocol and installed adapters announced after successful worker initialization.
 */
@Value
@Builder
@Jacksonized
public class WorkerReady implements WorkerMessage {
	public static final String EVENT = "ready";
	@Builder.Default
	@NotNull String event = EVENT;
	int protocol;
	@Singular
	@NotNull Set<String> capabilities;
}
