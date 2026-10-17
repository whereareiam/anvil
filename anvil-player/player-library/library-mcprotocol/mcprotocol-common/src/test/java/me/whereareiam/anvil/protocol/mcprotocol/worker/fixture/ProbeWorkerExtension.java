package me.whereareiam.anvil.protocol.mcprotocol.worker.fixture;

import me.whereareiam.anvil.capability.api.channel.OperationRegistry;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.protocol.api.player.worker.PlayerBindingContext;
import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerBinding;
import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerExtension;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Capability wiring that takes its port's adapter from the worker the way built-in capabilities do, while binding
 * a player, and answers with the adapter's name.
 */
public final class ProbeWorkerExtension implements WorkerExtension<Object> {
	public static final String ID = "test.probe";
	public static final ChannelOperation<Void, String> NAME = new ChannelOperation<>("test.probe.name", Void.class, String.class);

	public @NotNull String id() { return ID; }
	public @NotNull Optional<String> libraryId() { return Optional.of("mcprotocol"); }
	public @NotNull Class<Object> nativeSessionType() { return Object.class; }
	public @NotNull WorkerBinding bind(@NotNull PlayerBindingContext<Object> player, @NotNull OperationRegistry operations) {
		ProbePackets packets = player.adapter(ProbePackets.class);
		operations.register(NAME, ignored -> packets.name());
		return () -> { };
	}
}
