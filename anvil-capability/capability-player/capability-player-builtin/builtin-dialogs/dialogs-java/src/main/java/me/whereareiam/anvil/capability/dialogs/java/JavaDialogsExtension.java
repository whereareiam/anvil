package me.whereareiam.anvil.capability.dialogs.java;

import me.whereareiam.anvil.capability.dialogs.channel.DialogsChannel;
import me.whereareiam.anvil.capability.dialogs.model.DialogRegistry;
import me.whereareiam.anvil.capability.dialogs.model.click.DialogClick;
import me.whereareiam.anvil.capability.dialogs.model.dialog.Dialog;
import me.whereareiam.anvil.capability.dialogs.model.dialog.RegistryDialog;
import me.whereareiam.anvil.capability.dialogs.type.DialogAfterAction;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.protocol.api.worker.OperationRegistry;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.protocol.api.worker.WorkerBinding;
import me.whereareiam.anvil.protocol.api.worker.WorkerExtension;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import me.whereareiam.anvil.protocol.java.api.model.configuration.RegistryEntry;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ServerboundChatCommand;
import me.whereareiam.anvil.protocol.java.api.packet.configuration.ClientboundRegistryData;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundStartConfiguration;
import me.whereareiam.anvil.protocol.java.api.packet.dialog.ClientboundClearDialog;
import me.whereareiam.anvil.protocol.java.api.packet.dialog.ClientboundShowDialog;
import me.whereareiam.anvil.protocol.java.api.packet.dialog.ServerboundCustomClickAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Installs the built-in dialogs capability for players that speak the Java Edition protocol: it emits each dialog the
 * player's current connection is shown, read from its NBT by {@link DialogReader}, each dialog the server clears, the
 * {@code minecraft:dialog} registry the server sends during configuration, and a reset whenever the client replaces
 * its screens, which it does when it joins a level, respawns or returns to configuration, and when a new connection
 * starts. The custom actions of the shown dialog and of the registry stay in the worker, in {@link ShownActions},
 * until the server replaces or clears the dialog, sends a new registry, or the client closes its screens or the
 * connection ends; the capability names them by handle. Pressed buttons are sent as an unsigned command or the custom
 * click of the named action, whose payload {@link ClickPayload} builds from the declared payload and the typed input
 * values. Releases before Minecraft 1.21.6 have no dialogs, so
 * the worker reports the capability unavailable for them.
 */
public final class JavaDialogsExtension implements WorkerExtension<Object> {
	private static final String FEATURE = "dialogs";
	private static final String DIALOG_REGISTRY = "minecraft:dialog";
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
		protocol.require(FEATURE, PACKETS);

		ShownActions actions = new ShownActions();
		DialogReader shownReader = new DialogReader(actions::retainShown);
		DialogReader registryReader = new DialogReader(actions::retainRegistered);
		operations.register(DialogsChannel.CLICK, click -> {
			protocol.send(packet(click, actions));
			return null;
		});

		Subscription connections = player.bindConnection(() -> {
			actions.releaseAll();
			player.emit(DialogsChannel.RESET, null);
			return actions::releaseAll;
		});
		Subscription binding = Subscription.all(
				protocol.listen(ClientboundShowDialog.class, show -> {
					actions.releaseShown();
					player.emit(DialogsChannel.SHOWN, shown(show, shownReader));
				}),
				protocol.listen(ClientboundClearDialog.class, ignored -> {
					actions.releaseShown();
					player.emit(DialogsChannel.CLEARED, null);
				}),
				protocol.listen(ClientboundLogin.class, ignored -> reset(player, actions)),
				protocol.listen(ClientboundRespawn.class, ignored -> reset(player, actions)),
				protocol.listen(ClientboundStartConfiguration.class, ignored -> reset(player, actions)),
				protocol.listen(ClientboundRegistryData.class, registry -> {
					if (!registry.getRegistry().equals(DIALOG_REGISTRY)) return;

					actions.releaseRegistry();
					player.emit(DialogsChannel.REGISTRY, registry(registry, registryReader));
				}),
				connections,
				actions::releaseAll
		);

		return binding::close;
	}

	/**
	 * Closes every screen as the client does, which releases the shown dialog's actions.
	 */
	private static void reset(PlayerBindingContext<Object> player, ShownActions actions) {
		actions.releaseShown();
		player.emit(DialogsChannel.RESET, null);
	}

	/**
	 * Builds what the client sends for a click: the command, or the custom click of the kept action the click names,
	 * with its declared payload and the click's values.
	 *
	 * @throws IllegalStateException when the named action is no longer kept
	 */
	private static ServerboundPacket packet(DialogClick click, ShownActions actions) {
		if (click.getCommand() != null) return new ServerboundChatCommand(click.getCommand());
		if (click.getAction() == null)
			throw new IllegalArgumentException("A click is a command or names a custom action");

		ShownActions.CustomAction action = actions.find(click.getAction());

		return new ServerboundCustomClickAction(action.id(), ClickPayload.of(action.payload(), click.getValues()));
	}

	/**
	 * Reads a shown dialog. A dialog the server refers to by registry ID carries only that ID, with an empty title and
	 * the default closing behaviour, until the host resolves it from the registry.
	 */
	private static Dialog shown(ClientboundShowDialog packet, DialogReader reader) {
		Dialog.DialogBuilder shown = packet.getDialog() == null
				? reference(packet.getRegistryId())
				: reader.read(packet.getDialog()).toBuilder();
		return shown.configuration(packet.isConfiguration()).build();
	}

	private static Dialog.DialogBuilder reference(@Nullable Integer registryId) {
		return Dialog.builder()
				.type("")
				.registryId(registryId)
				.title("")
				.externalTitle("")
				.closeWithEscape(true)
				.afterAction(DialogAfterAction.CLOSE);
	}
	/**
	 * Reads the dialog registry, keeping its order, so registry IDs still index it.
	 */
	private static DialogRegistry registry(ClientboundRegistryData packet, DialogReader reader) {
		List<RegistryDialog> dialogs = new ArrayList<>();
		for (RegistryEntry entry : packet.getEntries()) {
			Dialog dialog = entry.getData() == null ? null : reader.read(entry.getData());
			dialogs.add(new RegistryDialog(entry.getKey(), dialog));
		}

		return new DialogRegistry(dialogs);
	}
}
