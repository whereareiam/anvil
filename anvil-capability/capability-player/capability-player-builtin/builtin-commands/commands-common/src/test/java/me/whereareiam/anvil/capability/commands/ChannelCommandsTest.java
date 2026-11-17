package me.whereareiam.anvil.capability.commands;

import me.whereareiam.anvil.capability.commands.channel.CommandsChannel;
import me.whereareiam.anvil.capability.commands.model.CommandTree;
import me.whereareiam.anvil.capability.commands.model.CommandTreeNode;
import me.whereareiam.anvil.capability.commands.type.CommandCheck;
import me.whereareiam.anvil.capability.commands.type.CommandNodeType;
import me.whereareiam.anvil.protocol.api.channel.TestProtocolChannel;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChannelCommandsTest {
	private static final Duration TIMEOUT = Duration.ofSeconds(3);
	private static final CommandTree LOGIN = new CommandTree(List.of(
			CommandTreeNode.builder().type(CommandNodeType.ROOT).child(1).build(),
			CommandTreeNode.builder().type(CommandNodeType.LITERAL).name("login").executable(true).build()
	), 0);

	@Test
	void keepsTheLastTreeOfTheCurrentConnection() {
		TestProtocolChannel channel = new TestProtocolChannel(Set.of(CommandsChannel.ID));
		ChannelCommands commands = new ChannelCommands(channel);
		assertEquals(Optional.empty(), commands.tree());

		channel.publish(CommandsChannel.TREE, LOGIN);
		assertEquals(Optional.of(LOGIN), commands.tree());
		assertEquals(LOGIN, commands.tree(tree -> tree.literal("login").isPresent(), TIMEOUT));

		channel.publish(CommandsChannel.RESET, null);
		assertEquals(Optional.empty(), commands.tree());
		assertEquals("Player did not receive a matching command tree",
				assertThrows(IllegalStateException.class, () -> commands.tree(tree -> true, TIMEOUT)).getMessage());
	}

	@Test
	void checksCommandsWithOrWithoutTheirSlashAgainstTheTree() {
		TestProtocolChannel channel = new TestProtocolChannel(Set.of(CommandsChannel.ID));
		ChannelCommands commands = new ChannelCommands(channel);
		assertEquals(CommandCheck.PARSE_ERRORS, commands.check("login"));

		channel.publish(CommandsChannel.TREE, LOGIN);

		assertEquals(CommandCheck.NO_ISSUES, commands.check("login"));
		assertEquals(CommandCheck.NO_ISSUES, commands.check("/login"));
		assertEquals(CommandCheck.PARSE_ERRORS, commands.check("logout"));
		assertEquals(List.of(), channel.requests());
	}
}
