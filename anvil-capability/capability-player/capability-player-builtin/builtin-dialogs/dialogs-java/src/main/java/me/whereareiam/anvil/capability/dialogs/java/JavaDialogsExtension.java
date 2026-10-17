package me.whereareiam.anvil.capability.dialogs.java;

import me.whereareiam.anvil.capability.dialogs.channel.DialogsChannel;
import me.whereareiam.anvil.capability.dialogs.model.click.DialogClick;
import me.whereareiam.anvil.capability.dialogs.model.click.DialogValue;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.protocol.api.worker.OperationRegistry;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.protocol.api.worker.WorkerBinding;
import me.whereareiam.anvil.protocol.api.worker.WorkerExtension;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import me.whereareiam.anvil.protocol.java.api.model.dialog.DialogInputValue;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ServerboundChatCommand;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundStartConfiguration;
import me.whereareiam.anvil.protocol.java.api.packet.dialog.ClientboundClearDialog;
import me.whereareiam.anvil.protocol.java.api.packet.dialog.ClientboundRegistryData;
import me.whereareiam.anvil.protocol.java.api.packet.dialog.ClientboundShowDialog;
import me.whereareiam.anvil.protocol.java.api.packet.dialog.ServerboundCustomClickAction;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/**
 * Installs the built-in dialogs capability for players that speak the Java Edition protocol: it emits each dialog the
 * player's current connection is shown, mapped by {@link ProtocolDialogs}, each dialog the server clears, the dialog
 * registry the server sends during configuration, and a reset whenever the client replaces its screens, which it does
 * when it joins a level, respawns or returns to configuration, and when a new connection starts. Pressed buttons are
 * sent as an unsigned command or a custom click with the action's declared payload and the typed input values, which
 * the player's release puts into the payload. Releases before Minecraft 1.21.6 have no dialogs, so the worker
 * reports the capability unavailable for them.
 */
public final class JavaDialogsExtension implements WorkerExtension<Object> {
	private static final List<Class<? extends JavaPacket>> PACKETS = List.of(
			ClientboundShowDialog.class,
			ClientboundClearDialog.class,
			ClientboundRegistryData.class,
			ServerboundCustomClickAction.class,
			ServerboundChatCommand.class,
			ClientboundLogin.class,
			ClientboundRespawn.class,
			ClientboundStartConfiguration.class
	);

	@Override
	public @NotNull String id() {
		return DialogsChannel.ID;
	}

	@Override
	public @NotNull Optional<String> libraryId() {
		return Optional.empty();
	}

	@Override
	public @NotNull Class<Object> nativeSessionType() {
		return Object.class;
	}

	/**
	 * Installs the click operation and the dialog listeners for one player.
	 *
	 * @throws ProtocolUnavailableException when the player does not speak the Java Edition protocol or its release
	 * has no dialogs
	 */
	@Override
	public @NotNull WorkerBinding bind(@NotNull PlayerBindingContext<Object> player, @NotNull OperationRegistry operations) {
		JavaProtocol protocol = player.protocol(JavaProtocol.class);
		for (Class<? extends JavaPacket> packet : PACKETS) {
			if (!protocol.supports(packet))
				throw new ProtocolUnavailableException("the player's release has no dialogs; it does not carry "
						+ packet.getName());
		}

		operations.register(DialogsChannel.CLICK, click -> {
			protocol.send(packet(click));
			return null;
		});

		Subscription connections = player.bindNativeSession(ignored -> {
			player.emit(DialogsChannel.RESET, null);
			return () -> { };
		});
		List<Subscription> listening = List.of(
				protocol.listen(ClientboundShowDialog.class, show ->
						player.emit(DialogsChannel.SHOWN, ProtocolDialogs.shown(show))),
				protocol.listen(ClientboundClearDialog.class, ignored -> player.emit(DialogsChannel.CLEARED, null)),
				protocol.listen(ClientboundLogin.class, ignored -> player.emit(DialogsChannel.RESET, null)),
				protocol.listen(ClientboundRespawn.class, ignored -> player.emit(DialogsChannel.RESET, null)),
				protocol.listen(ClientboundStartConfiguration.class, ignored -> player.emit(DialogsChannel.RESET, null)),
				protocol.listen(ClientboundRegistryData.class, registry ->
						player.emit(DialogsChannel.REGISTRY, ProtocolDialogs.registry(registry)))
		);

		return () -> {
			try (connections) {
				listening.forEach(Subscription::close);
			}
		};
	}

	private static ServerboundPacket packet(DialogClick click) {
		if (click.getCommand() != null) return new ServerboundChatCommand(click.getCommand());

		List<DialogInputValue> values = click.getValues().stream()
				.map(JavaDialogsExtension::value)
				.toList();
		return new ServerboundCustomClickAction(click.getCustomId(), click.getPayload(), values);
	}

	private static DialogInputValue value(DialogValue value) {
		return switch (value.getType()) {
			case BOOLEAN -> DialogInputValue.checkbox(value.getKey(), Boolean.parseBoolean(value.getValue()));
			case NUMBER_RANGE -> DialogInputValue.slider(value.getKey(), Float.parseFloat(value.getValue()));
			default -> DialogInputValue.text(value.getKey(), value.getValue());
		};
	}
}
