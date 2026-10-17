package me.whereareiam.anvil.capability.dialogs;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.capability.dialogs.model.click.DialogClick;
import me.whereareiam.anvil.capability.dialogs.model.dialog.Dialog;
import me.whereareiam.anvil.capability.dialogs.screen.DialogRegistry;
import me.whereareiam.anvil.protocol.api.model.channel.EventDescriptor;
import me.whereareiam.anvil.protocol.api.model.channel.OperationDescriptor;

/**
 * Typed dialogs operations and events shared by the host dialogs capability and the worker's dialogs binding.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DialogsOperations {
	/**
	 * Sends what the client sends for a pressed dialog button.
	 */
	public static final OperationDescriptor<DialogClick, Void> CLICK = new OperationDescriptor<>(
			"dialogs.click", DialogClick.class, Void.class
	);
	/**
	 * A dialog the player's current native session was shown.
	 */
	public static final EventDescriptor<Dialog> SHOWN = new EventDescriptor<>("dialogs.shown", Dialog.class);
	/**
	 * The server cleared the open dialog.
	 */
	public static final EventDescriptor<Void> CLEARED = new EventDescriptor<>("dialogs.cleared", Void.class);
	/**
	 * The client replaced every screen, or a new native session started without any.
	 */
	public static final EventDescriptor<Void> RESET = new EventDescriptor<>("dialogs.reset", Void.class);
	/**
	 * The dialog registry the server sent during configuration.
	 */
	public static final EventDescriptor<DialogRegistry> REGISTRY = new EventDescriptor<>(
			"dialogs.registry", DialogRegistry.class
	);
}
