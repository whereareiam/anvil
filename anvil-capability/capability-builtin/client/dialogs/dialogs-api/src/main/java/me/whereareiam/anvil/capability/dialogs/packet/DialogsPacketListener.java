package me.whereareiam.anvil.capability.dialogs.packet;

import me.whereareiam.anvil.capability.protocol.api.model.nbt.NbtCompound;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Receives what one release's packets tell a client about dialogs, as {@link DialogsPackets} reports it: the dialogs
 * the server shows and clears, the moments the client replaces every screen, and the dialog registry. Dialogs arrive
 * as the compounds the vanilla dialog codec wrote, in the library-neutral NBT model.
 *
 * <pre>{@code
 * Runnable detach = packets.listen(session, new DialogsPacketListener() {
 *     public void shown(NbtCompound dialog, boolean configuration) { player.emit(SHOWN, read(dialog, configuration)); }
 *     public void shownFromRegistry(int registryId) { player.emit(SHOWN, registered(registryId)); }
 *     public void cleared() { player.emit(CLEARED, null); }
 *     public void reset() { player.emit(RESET, null); }
 *     public void registry(List<RegistryDialogEntry> entries) { player.emit(REGISTRY, read(entries)); }
 * });
 * }</pre>
 */
public interface DialogsPacketListener {
	/**
	 * Reports that the server showed a dialog it sent in full.
	 *
	 * @param dialog the dialog compound
	 * @param configuration whether the server showed it during the configuration phase rather than in the game
	 */
	void shown(@NotNull NbtCompound dialog, boolean configuration);

	/**
	 * Reports that the server showed, in the game, a dialog of the registry it sent during configuration.
	 *
	 * @param registryId the index of the dialog in the registry
	 */
	void shownFromRegistry(int registryId);

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
	 * @param entries the registry's entries
	 */
	void registry(@NotNull List<RegistryDialogEntry> entries);
}
