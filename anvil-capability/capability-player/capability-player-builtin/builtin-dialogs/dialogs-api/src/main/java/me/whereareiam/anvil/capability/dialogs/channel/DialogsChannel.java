package me.whereareiam.anvil.capability.dialogs.channel;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.capability.dialogs.model.DialogRegistry;
import me.whereareiam.anvil.capability.dialogs.model.click.DialogClick;
import me.whereareiam.anvil.capability.dialogs.model.dialog.Dialog;
import me.whereareiam.anvil.protocol.api.model.channel.EventDescriptor;
import me.whereareiam.anvil.protocol.api.model.channel.OperationDescriptor;

/**
 * The channel contract between the host dialogs capability and its worker extensions: the capability ID both register
 * under, the operation that sends a pressed button and the events the worker emits about the dialogs the player's
 * current connection is shown.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DialogsChannel {
	/**
	 * Capability ID of the host provider and of every edition's worker extension.
	 */
	public static final String ID = "me.whereareiam.anvil.dialogs";
	/**
	 * Sends what the client sends for a pressed dialog button.
	 */
	public static final OperationDescriptor<DialogClick, Void> CLICK = new OperationDescriptor<>(
			"dialogs.click", DialogClick.class, Void.class
	);
	/**
	 * A dialog the player's current connection was shown.
	 */
	public static final EventDescriptor<Dialog> SHOWN = new EventDescriptor<>("dialogs.shown", Dialog.class);
	/**
	 * The server cleared the open dialog.
	 */
	public static final EventDescriptor<Void> CLEARED = new EventDescriptor<>("dialogs.cleared", Void.class);
	/**
	 * The client replaced every screen, or a new connection started without any.
	 */
	public static final EventDescriptor<Void> RESET = new EventDescriptor<>("dialogs.reset", Void.class);
	/**
	 * The dialog registry the server sent during configuration.
	 */
	public static final EventDescriptor<DialogRegistry> REGISTRY = new EventDescriptor<>(
			"dialogs.registry", DialogRegistry.class
	);
}
