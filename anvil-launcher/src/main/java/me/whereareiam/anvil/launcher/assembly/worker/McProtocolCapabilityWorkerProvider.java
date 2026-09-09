package me.whereareiam.anvil.launcher.assembly.worker;

import me.whereareiam.anvil.capability.binding.WorkerCapabilities;
import me.whereareiam.anvil.protocol.api.worker.NativeWorkerExtension;
import me.whereareiam.anvil.protocol.api.worker.NativeWorkerProvider;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.jetbrains.annotations.NotNull;

/**
 * Connects capability extension discovery to the exact MCProtocol worker's native SDK.
 * This provider is loaded in the worker JVM, where the selected SDK is available.
 */
public final class McProtocolCapabilityWorkerProvider implements NativeWorkerProvider<ClientSession> {
	@Override
	public @NotNull String id() {
		return "anvil.capabilities";
	}

	@Override
	public @NotNull String backendId() {
		return "mcprotocol";
	}

	@Override
	public @NotNull Class<ClientSession> backendType() {
		return ClientSession.class;
	}

	@Override
	public @NotNull NativeWorkerExtension<ClientSession> create(int protocolNumber) {
		return new CapabilityWorkerExtension<>(WorkerCapabilities.discover(backendId(), backendType(), protocolNumber));
	}
}
