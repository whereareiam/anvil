package me.whereareiam.anvil.capability.messages;

import me.whereareiam.anvil.capability.messages.channel.MessagesChannel;
import me.whereareiam.anvil.capability.messages.model.MessageText;
import me.whereareiam.anvil.capability.protocol.api.player.TestProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.edition.java.protocol.JavaProtocol;
import me.whereareiam.anvil.player.api.channel.TestProtocolChannel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessagesProviderTest {
	@Test
	void describesTheMessagesCapabilityForTheLibrariesWithAWorkerSide() {
		MessagesProvider provider = new MessagesProvider();

		assertEquals("me.whereareiam.anvil.messages", MessagesChannel.ID);
		assertEquals(MessagesChannel.ID, provider.descriptor().id());
		assertTrue(provider.descriptor().requiredCapabilities().isEmpty());
		assertEquals(Set.of(JavaProtocol.class), provider.requiredProtocols());
		assertEquals(Messages.class, provider.capability());
	}

	@Test
	void observesTheChatAndSendsThroughThePlayersChannelWithoutRequestingOnCreation() {
		TestProtocolChannel channel = new TestProtocolChannel(Set.of(MessagesChannel.ID));
		channel.answer(MessagesChannel.CHAT, text -> null);
		channel.answer(MessagesChannel.COMMAND, text -> null);
		Messages messages = new MessagesProvider().create(new TestProtocolPlayerCapabilityContext(channel));
		assertEquals(List.of(MessagesChannel.CHAT_ADDED.id(), MessagesChannel.CHAT_DROPPED.id(),
				MessagesChannel.CHAT_DELETED.id(), MessagesChannel.CHAT_CLEARED.id()), channel.subscriptions());
		assertTrue(channel.requests().isEmpty());

		messages.chat("hello");
		messages.command("/list");

		assertEquals(List.of(MessagesChannel.CHAT.id(), MessagesChannel.COMMAND.id()), channel.requests());
		assertEquals(List.of(new MessageText("hello"), new MessageText("list")), channel.payloads());
	}
}
