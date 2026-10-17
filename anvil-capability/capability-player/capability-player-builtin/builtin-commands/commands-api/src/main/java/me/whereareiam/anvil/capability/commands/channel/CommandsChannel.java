package me.whereareiam.anvil.capability.commands.channel;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.capability.commands.model.CommandTree;
import me.whereareiam.anvil.protocol.api.model.channel.EventDescriptor;

/**
 * The channel contract between the host commands capability and its worker extensions: the capability ID both
 * register under and the events the worker emits. Commands register no operations; the host checks commands against
 * the trees it receives.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CommandsChannel {
	/**
	 * Capability ID of the host provider and of every edition's worker extension.
	 */
	public static final String ID = "me.whereareiam.anvil.commands";
	/**
	 * A command tree the player's current connection received.
	 */
	public static final EventDescriptor<CommandTree> TREE = new EventDescriptor<>("commands.tree", CommandTree.class);
	/**
	 * A new connection started, which holds no command tree until the server sends one.
	 */
	public static final EventDescriptor<Void> RESET = new EventDescriptor<>("commands.reset", Void.class);
}
