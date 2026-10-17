package me.whereareiam.anvil.capability.dialogs.java;

import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * The custom actions one player's worker keeps, so their payloads never leave it: those of the dialog the server
 * shows the player and of the dialog registry its current connection sent, each under the handle the capability's
 * {@code DialogAction} carries and a click names. Handles are unique for the player, across its connections, so a
 * click naming an action of a dialog or connection that is gone finds nothing. Methods synchronize on the instance,
 * since packets and clicks arrive on different threads.
 */
final class ShownActions {
	private final Map<Long, CustomAction> shown = new HashMap<>();
	private final Map<Long, CustomAction> registry = new HashMap<>();
	private long next;

	/**
	 * Keeps a custom action of the dialog the server shows until that dialog is gone.
	 *
	 * @param id the custom click's ID
	 * @param payload the payload or additions the action declared, or {@code null} for none
	 * @return the action's handle
	 */
	synchronized long retainShown(@NotNull String id, @Nullable NbtTag payload) {
		return retain(shown, id, payload);
	}

	/**
	 * Keeps a custom action of a dialog of the dialog registry until a new registry replaces it or the connection
	 * ends.
	 *
	 * @param id the custom click's ID
	 * @param payload the payload or additions the action declared, or {@code null} for none
	 * @return the action's handle
	 */
	synchronized long retainRegistered(@NotNull String id, @Nullable NbtTag payload) {
		return retain(registry, id, payload);
	}

	/**
	 * Releases the actions of the shown dialog, which the server replaced or cleared, or the client closed with every
	 * other screen.
	 */
	synchronized void releaseShown() {
		shown.clear();
	}

	/**
	 * Releases the actions of the dialog registry, which a new registry replaces.
	 */
	synchronized void releaseRegistry() {
		registry.clear();
	}

	/**
	 * Releases every action, as the connection that sent them ended.
	 */
	synchronized void releaseAll() {
		shown.clear();
		registry.clear();
	}

	/**
	 * Finds a kept action by its handle.
	 *
	 * @param handle the handle a click names
	 * @return the action
	 * @throws IllegalStateException when the action is no longer kept
	 */
	synchronized @NotNull CustomAction find(long handle) {
		CustomAction action = shown.containsKey(handle) ? shown.get(handle) : registry.get(handle);
		if (action != null) return action;

		throw new IllegalStateException("The pressed custom action " + handle
				+ " is no longer shown: the server replaced or cleared its dialog, or the connection that showed it ended");
	}

	private long retain(Map<Long, CustomAction> actions, String id, @Nullable NbtTag payload) {
		long handle = ++next;
		actions.put(handle, new CustomAction(id, payload));

		return handle;
	}

	/**
	 * A kept custom action: its click ID and the payload or additions it declared, or {@code null} for none.
	 */
	record CustomAction(@NotNull String id, @Nullable NbtTag payload) {
	}
}
