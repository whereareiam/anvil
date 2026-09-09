package me.whereareiam.anvil.launcher.assembly.worker;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.capability.binding.WorkerCapabilities;
import me.whereareiam.anvil.protocol.api.worker.NativeBinding;
import me.whereareiam.anvil.protocol.api.worker.NativeOperations;
import me.whereareiam.anvil.protocol.api.worker.NativePlayer;
import me.whereareiam.anvil.protocol.api.worker.NativeWorkerExtension;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Transfers each typed capability binding's lifecycle to its native protocol player owner.
 */
@RequiredArgsConstructor
final class CapabilityWorkerExtension<B> implements NativeWorkerExtension<B> {
	private final @NotNull WorkerCapabilities<B> capabilities;

	@Override
	public @NotNull Set<String> capabilities() {
		return capabilities.capabilities();
	}

	@Override
	public @NotNull NativeBinding bind(@NotNull NativePlayer<B> player, @NotNull NativeOperations operations) {
		return capabilities.bind(new NativePlayerBindingContext<>(player), new ProtocolOperationRegistry(operations))::close;
	}
}
