package me.whereareiam.anvil.protocol.mcprotocol.worker.fixture;

import me.whereareiam.anvil.capability.api.channel.OperationRegistry;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.messages.model.MessageText;
import me.whereareiam.anvil.capability.protocol.api.model.EventDescriptor;
import me.whereareiam.anvil.capability.protocol.api.player.worker.PlayerBindingContext;
import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerBinding;
import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerExtension;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Release-neutral worker extension that echoes and emits messages, installed in every MCProtocolLib worker.
 */
public final class EventWorkerExtension implements WorkerExtension<Object> {
	public static final String ID = "test.events";
	public static final EventDescriptor<MessageText> CHANGED = new EventDescriptor<>("test.events.changed", MessageText.class);
	public static final ChannelOperation<MessageText, Void> EMIT = new ChannelOperation<>("test.events.emit", MessageText.class, Void.class);
	public static final ChannelOperation<MessageText, MessageText> ECHO = new ChannelOperation<>("test.events.echo", MessageText.class, MessageText.class);

	public @NotNull String id() { return ID; }
	public @NotNull Optional<String> libraryId() { return Optional.of("mcprotocol"); }
	public @NotNull Class<Object> nativeSessionType() { return Object.class; }
	public @NotNull WorkerBinding bind(@NotNull PlayerBindingContext<Object> player, @NotNull OperationRegistry operations) {
		operations.register(EMIT, message -> { player.emit(CHANGED, message); return null; });
		operations.register(ECHO, message -> message);
		return () -> { };
	}
}
