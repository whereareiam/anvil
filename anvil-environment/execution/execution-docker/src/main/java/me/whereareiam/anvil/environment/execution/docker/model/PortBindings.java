package me.whereareiam.anvil.environment.execution.docker.model;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.api.type.network.ListenerTransport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;

/** Host bindings requested for one Docker container. A null endpoint is not published. */
@Value
@Builder
public class PortBindings {
	@Nullable InetSocketAddress game;
	@Nullable InetSocketAddress agent;

	/**
	 * Transport the game port is exposed and published with; the agent port is always TCP.
	 */
	@NotNull ListenerTransport gameTransport;
}
