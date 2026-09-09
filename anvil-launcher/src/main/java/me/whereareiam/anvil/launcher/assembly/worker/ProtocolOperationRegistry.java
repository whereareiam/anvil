package me.whereareiam.anvil.launcher.assembly.worker;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.capability.api.channel.OperationRegistry;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.binding.JsonCapabilityCodec;
import me.whereareiam.anvil.protocol.api.worker.NativeOperations;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * Converts typed capability handlers to encoded operations for one native player binding.
 * The capability binding owns registration policy; the native player owns dispatch lifetime.
 */
@RequiredArgsConstructor
final class ProtocolOperationRegistry implements OperationRegistry {
	private final @NotNull NativeOperations operations;
	private final JsonCapabilityCodec codec = new JsonCapabilityCodec();

	@Override
	public <Q, R> void register(@NotNull ChannelOperation<Q, R> operation, @NotNull Function<Q, R> handler) {
		operations.register(operation.getId(), codec.handler(operation, handler));
	}
}
