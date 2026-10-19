package me.whereareiam.anvil.launcher.adapter.service;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.environment.execution.api.PortReservations;
import me.whereareiam.anvil.environment.service.api.HostPorts;
import org.jetbrains.annotations.NotNull;

/**
 * Hands services the host ports of the engine's own reservations, so a service never publishes on a port reserved
 * for a process.
 */
@RequiredArgsConstructor
public final class EngineHostPorts implements HostPorts {
	private final @NotNull PortReservations ports;

	@Override
	public int reserve(@NotNull String bindAddress) {
		return ports.reserve(bindAddress);
	}

	@Override
	public void release(int port) {
		ports.release(port);
	}
}
