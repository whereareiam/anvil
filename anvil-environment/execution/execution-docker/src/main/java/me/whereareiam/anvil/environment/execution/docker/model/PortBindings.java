package me.whereareiam.anvil.environment.execution.docker.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.util.Map;

/** Host bindings requested for one Docker container. A null endpoint is not published. */
@Value
@Builder
public class PortBindings {
	@Nullable InetSocketAddress game;
	@Nullable InetSocketAddress agent;

	/**
	 * Host addresses the container's listener ports are published on, by container port.
	 */
	@NotNull
	@Singular
	Map<Integer, InetSocketAddress> listeners;
}
