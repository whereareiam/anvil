package me.whereareiam.anvil.capability.dialogs.packet;

import me.whereareiam.anvil.capability.dialogs.model.dialog.Dialog;
import me.whereareiam.anvil.capability.dialogs.model.dialog.RegistryDialog;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Receives what one release's packets tell a client about dialogs, translated by {@link DialogsPackets} into
 * library-neutral values: the dialogs the server shows and clears, the moments the client replaces every screen,
 * and the dialog registry.
 *
 * <pre>{@code
 * Runnable detach = packets.listen(session, new DialogsPacketListener() {
 *     public void shown(Dialog dialog) { player.emit(DialogsOperations.SHOWN, dialog); }
 *     public void cleared() { player.emit(DialogsOperations.CLEARED, null); }
 *     public void reset() { player.emit(DialogsOperations.RESET, null); }
 *     public void registry(List<RegistryDialog> dialogs) { player.emit(DialogsOperations.REGISTRY, new DialogRegistry(dialogs)); }
 * });
 * }</pre>
 */
public interface DialogsPacketListener {
	/**
	 * Reports that the server showed a dialog, in the game or, when {@link Dialog#isConfiguration()} says so, during
	 * the configuration phase.
	 *
	 * @param dialog the dialog as the packet described it
	 */
	void shown(@NotNull Dialog dialog);

	/**
	 * Reports that the server cleared the open dialog, which returns the client to the screen below it.
	 */
	void cleared();

	/**
	 * Reports that the client replaced every screen, as it does when it joins a level, respawns, changes servers
	 * behind a proxy or starts configuring again, closing every dialog.
	 */
	void reset();

	/**
	 * Reports the dialog registry the server sent during configuration, in registry order, so the index of an entry
	 * is the registry ID a dialog packet refers to.
	 *
	 * @param dialogs the registry's entries
	 */
	void registry(@NotNull List<RegistryDialog> dialogs);
}
