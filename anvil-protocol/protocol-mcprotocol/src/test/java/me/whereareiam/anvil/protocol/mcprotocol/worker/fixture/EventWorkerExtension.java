package me.whereareiam.anvil.protocol.mcprotocol.worker.fixture;

import me.whereareiam.anvil.capability.protocol.api.model.EventDescriptor;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerBinding;
import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerExtension;
import me.whereareiam.anvil.capability.api.channel.OperationRegistry;
import me.whereareiam.anvil.capability.protocol.api.player.worker.PlayerBindingContext;
import me.whereareiam.anvil.capability.messages.model.MessageText;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.jetbrains.annotations.NotNull;

public final class EventWorkerExtension implements WorkerExtension<ClientSession> {
	public static final EventDescriptor<MessageText> CHANGED = new EventDescriptor<>("test.events.changed", MessageText.class);
	public static final ChannelOperation<MessageText, Void> EMIT = new ChannelOperation<>("test.events.emit", MessageText.class, Void.class);
	public static final ChannelOperation<MessageText, MessageText> ECHO = new ChannelOperation<>("test.events.echo", MessageText.class, MessageText.class);

	public @NotNull String id() { return "test.events"; }
	public @NotNull String backendId() { return "mcprotocol"; }
	public @NotNull Class<ClientSession> backendType() { return ClientSession.class; }
	public @NotNull WorkerBinding bind(@NotNull PlayerBindingContext<ClientSession> player, @NotNull OperationRegistry operations) {
		operations.register(EMIT, message -> { player.emit(CHANGED, message); return null; });
		operations.register(ECHO, message -> message);
		return () -> { };
	}
}
