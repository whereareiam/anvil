package me.whereareiam.anvil.capability.commands;

import me.whereareiam.anvil.capability.commands.channel.CommandsChannel;
import me.whereareiam.anvil.capability.protocol.api.player.TestProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.edition.java.protocol.JavaProtocol;
import me.whereareiam.anvil.player.api.channel.TestProtocolChannel;
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
		assertEquals(CommandsChannel.ID, provider.descriptor().id());
		assertTrue(provider.descriptor().requiredCapabilities().isEmpty());
		assertEquals(Set.of(JavaProtocol.class), provider.requiredProtocols());
		assertEquals(Commands.class, provider.capability());
	}

	@Test
	void observesCommandTreesWithoutRequestingOnCreation() {
		TestProtocolChannel channel = new TestProtocolChannel(Set.of(CommandsChannel.ID));
		Commands commands = new CommandsProvider().create(new TestProtocolPlayerCapabilityContext(channel));

		assertEquals(List.of(CommandsChannel.TREE.id(), CommandsChannel.RESET.id()), channel.subscriptions());
		assertTrue(channel.requests().isEmpty());
		assertTrue(commands.tree().isEmpty());
	}
}
