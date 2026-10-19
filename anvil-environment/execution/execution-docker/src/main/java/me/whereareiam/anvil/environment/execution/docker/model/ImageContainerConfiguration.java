package me.whereareiam.anvil.environment.execution.docker.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

/**
 * Immutable specification of a container that runs its own image, assembled by an image target.
 */
@Value
@Builder
public class ImageContainerConfiguration {
	@NotNull String name;
	@NotNull String network;
	@NotNull String alias;
	@NotNull String image;

	/**
	 * Host addresses the container's ports are published on, by container port.
	 */
	@NotNull
	@Singular
	Map<Integer, InetSocketAddress> ports;

	@NotNull
	@Singular("environment")
	Map<String, String> environment;

	/**
	 * The command that replaces the image's own; empty for the image's own.
	 */
	@NotNull
	@Singular("argument")
	List<String> command;
}
