package me.whereareiam.anvil.capability.commands;

import me.whereareiam.anvil.capability.commands.channel.CommandsChannel;
import me.whereareiam.anvil.capability.protocol.api.player.TestProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.protocol.api.channel.TestProtocolChannel;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandsProviderTest {
	@Test
	void describesTheCommandsCapabilityForTheLibrariesWithAWorkerSide() {
		CommandsProvider provider = new CommandsProvider();

		assertEquals("me.whereareiam.anvil.commands", CommandsChannel.ID);
		assertEquals(CommandsChannel.ID, provider.descriptor().getId());
		assertTrue(provider.descriptor().getRequiredCapabilities().isEmpty());
		assertEquals(Set.of(JavaProtocol.class), provider.requiredProtocols());
		assertEquals(Commands.class, provider.capability());
	}

	@Test
	void observesCommandTreesWithoutRequestingOnCreation() {
		TestProtocolChannel channel = new TestProtocolChannel(Set.of(CommandsChannel.ID));
		Commands commands = new CommandsProvider().create(new TestProtocolPlayerCapabilityContext(channel));

		assertEquals(List.of(CommandsChannel.TREE.getId(), CommandsChannel.RESET.getId()), channel.subscriptions());
		assertTrue(channel.requests().isEmpty());
		assertTrue(commands.tree().isEmpty());
	}
}
