package me.whereareiam.anvil.capability.commands;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.capability.commands.model.CommandTree;
import me.whereareiam.anvil.capability.protocol.api.model.EventDescriptor;

/**
 * Typed commands events shared by the host commands capability and the worker's commands binding.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CommandsOperations {
	/**
	 * A command tree the player's current native session received.
	 */
	public static final EventDescriptor<CommandTree> TREE = new EventDescriptor<>("commands.tree", CommandTree.class);
	/**
	 * A new native session started, which holds no command tree until the server sends one.
	 */
	public static final EventDescriptor<Void> RESET = new EventDescriptor<>("commands.reset", Void.class);
}
