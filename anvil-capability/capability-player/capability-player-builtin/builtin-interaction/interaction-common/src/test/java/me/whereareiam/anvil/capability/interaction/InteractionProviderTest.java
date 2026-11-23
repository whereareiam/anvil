package me.whereareiam.anvil.capability.interaction;

import me.whereareiam.anvil.capability.interaction.channel.InteractionChannel;
import me.whereareiam.anvil.capability.interaction.model.ItemUse;
import me.whereareiam.anvil.capability.interaction.type.Hand;
import me.whereareiam.anvil.capability.protocol.api.player.TestProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.edition.java.protocol.JavaProtocol;
import me.whereareiam.anvil.player.api.channel.TestProtocolChannel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InteractionProviderTest {
	@Test
	void describesTheInteractionCapabilityForTheLibrariesWithAWorkerSide() {
		InteractionProvider provider = new InteractionProvider();

		assertEquals("me.whereareiam.anvil.interaction", InteractionChannel.ID);
		assertEquals(InteractionChannel.ID, provider.descriptor().id());
		assertTrue(provider.descriptor().requiredCapabilities().isEmpty());
		assertEquals(Set.of(JavaProtocol.class), provider.requiredProtocols());
		assertEquals(Interaction.class, provider.capability());
	}

	@Test
	void sendsEachInteractionThroughThePlayersChannelWithoutRequestingOnCreation() {
		TestProtocolChannel channel = new TestProtocolChannel(Set.of(InteractionChannel.ID));
		channel.answer(InteractionChannel.ITEM, use -> null);
		Interaction interaction = new InteractionProvider().create(new TestProtocolPlayerCapabilityContext(channel));
		assertTrue(channel.requests().isEmpty());

		interaction.useItem(Hand.OFF);

		assertEquals(List.of(InteractionChannel.ITEM.id()), channel.requests());
		assertEquals(List.of(new ItemUse(Hand.OFF)), channel.payloads());
	}
}
