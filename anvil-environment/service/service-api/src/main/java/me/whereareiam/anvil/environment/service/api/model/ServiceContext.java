package me.whereareiam.anvil.environment.service.api.model;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.environment.service.api.HostPorts;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

/**
 * Where the services of one scenario run.
 */
@Value
@Builder(toBuilder = true)
public class ServiceContext {
	/**
	 * The host ports services publish on.
	 */
	@NotNull HostPorts ports;

	/**
	 * The host address services publish their ports on.
	 */
	@NotNull
	@Builder.Default
	String bindAddress = "127.0.0.1";

	/**
	 * The container network the scenario's processes run in, which services join under their names; null when the
	 * processes run on the host and reach services at their published ports.
	 */
	@Nullable String network;

	/**
	 * How long a service may take to become ready.
	 */
	@NotNull
	@Builder.Default
	Duration startup = Duration.ofMinutes(2);

	/**
	 * Whether images must be present already instead of being pulled.
	 */
	boolean offline;
}
