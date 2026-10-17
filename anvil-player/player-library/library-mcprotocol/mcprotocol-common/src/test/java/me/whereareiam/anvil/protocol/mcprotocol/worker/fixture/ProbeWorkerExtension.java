package me.whereareiam.anvil.protocol.mcprotocol.worker.fixture;

import me.whereareiam.anvil.protocol.api.model.channel.OperationDescriptor;
import me.whereareiam.anvil.protocol.api.worker.OperationRegistry;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.protocol.api.worker.WorkerBinding;
import me.whereareiam.anvil.protocol.api.worker.WorkerExtension;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Capability wiring that takes its port's adapter from the worker the way built-in capabilities do, while binding
 * a player, and answers with the adapter's name.
 */
public final class ProbeWorkerExtension implements WorkerExtension<Object> {
	public static final String ID = "test.probe";
	public static final OperationDescriptor<Void, String> NAME = new OperationDescriptor<>("test.probe.name", Void.class, String.class);

	public @NotNull String id() { return ID; }
	public @NotNull Optional<String> libraryId() { return Optional.of("mcprotocol"); }
	public @NotNull Class<Object> nativeSessionType() { return Object.class; }
	public @NotNull WorkerBinding bind(@NotNull PlayerBindingContext<Object> player, @NotNull OperationRegistry operations) {
		ProbePackets packets = player.adapter(ProbePackets.class);
		operations.register(NAME, ignored -> packets.name());
		return () -> { };
	}
}
