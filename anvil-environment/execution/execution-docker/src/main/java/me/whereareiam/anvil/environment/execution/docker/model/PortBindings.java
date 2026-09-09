package me.whereareiam.anvil.environment.execution.docker.model;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;

/** Host bindings requested for one Docker container. A null endpoint is not published. */
@Value
@Builder
public class PortBindings {
	@Nullable InetSocketAddress game;
	@Nullable InetSocketAddress agent;
}
