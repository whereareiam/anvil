package me.whereareiam.anvil.capability.dialogs;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.capability.api.channel.OperationRegistry;
import me.whereareiam.anvil.capability.dialogs.model.click.DialogClick;
import me.whereareiam.anvil.capability.dialogs.model.dialog.RegistryDialog;
import me.whereareiam.anvil.capability.dialogs.nbt.ClickPayload;
import me.whereareiam.anvil.capability.dialogs.nbt.DialogReader;
import me.whereareiam.anvil.capability.dialogs.packet.DialogsPacketListener;
import me.whereareiam.anvil.capability.dialogs.packet.DialogsPackets;
import me.whereareiam.anvil.capability.dialogs.packet.RegistryDialogEntry;
import me.whereareiam.anvil.capability.dialogs.screen.DialogRegistry;
import me.whereareiam.anvil.capability.protocol.api.player.channel.Subscription;
import me.whereareiam.anvil.capability.protocol.api.player.worker.PlayerBindingContext;
import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerBinding;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Worker-side dialogs behavior over the packets of one library release: it registers the click operation for a
 * player, sends clicks through the player's current native session, and emits what a native session reports about
 * dialogs while it is still the player's current one. Each new native session starts by resetting the screens,
 * because a client that reconnects has none open. The binding reads the dialog NBT the release reports and builds
 * the payload of each custom click, so a release's packets only carry NBT trees.
 *
 * @param <S> native session type of the library release
 */
@RequiredArgsConstructor
public final class DialogsBinding<S> {
	private final @NotNull DialogsPackets<S> packets;

	/**
	 * Installs the dialogs operations for one player and observes every native session the player connects with.
	 *
	 * @param player native lifecycle, session access and event delivery of the player
	 * @param operations typed operation registration for the player
	 * @return binding that removes the native listener of the current session when the player is destroyed
	 */
	public @NotNull WorkerBinding bind(@NotNull PlayerBindingContext<Object> player, @NotNull OperationRegistry operations) {
		operations.register(DialogsOperations.CLICK, click -> {
			click(session(player), click);
			return null;
		});

		Subscription dialogs = player.bindNativeSession(nativeSession -> listen(player, nativeSession));
		return dialogs::close;
	}

	private Subscription listen(PlayerBindingContext<Object> player, Object nativeSession) {
		S session = packets.sessionType().cast(nativeSession);
		player.emit(DialogsOperations.RESET, null);
		Runnable detach = packets.listen(session, new DialogsPacketListener() {
			@Override
			public void shown(@NotNull Map<String, Object> dialog, boolean configuration) {
				if (player.isCurrentNativeSession(nativeSession))
					player.emit(DialogsOperations.SHOWN, DialogReader.read(dialog).toBuilder().configuration(configuration).build());
			}

			@Override
			public void shownFromRegistry(int registryId) {
				if (player.isCurrentNativeSession(nativeSession)) player.emit(DialogsOperations.SHOWN, DialogReader.registered(registryId));
			}

			@Override
			public void cleared() {
				if (player.isCurrentNativeSession(nativeSession)) player.emit(DialogsOperations.CLEARED, null);
			}

			@Override
			public void reset() {
				if (player.isCurrentNativeSession(nativeSession)) player.emit(DialogsOperations.RESET, null);
			}

			@Override
			public void registry(@NotNull List<RegistryDialogEntry> entries) {
				if (!player.isCurrentNativeSession(nativeSession)) return;

				List<RegistryDialog> dialogs = entries.stream()
						.map(entry -> new RegistryDialog(entry.getName(), entry.getDialog() == null ? null : DialogReader.read(entry.getDialog())))
						.toList();
				player.emit(DialogsOperations.REGISTRY, new DialogRegistry(dialogs));
			}
		});
		return detach::run;
	}

	private void click(S session, DialogClick click) {
		if (click.getCommand() != null) {
			packets.command(session, click.getCommand());
			return;
		}

		packets.customClick(session, click.getCustomId(), ClickPayload.of(click));
	}

	private S session(PlayerBindingContext<Object> player) {
		return packets.sessionType().cast(player.nativeSession());
	}
}
