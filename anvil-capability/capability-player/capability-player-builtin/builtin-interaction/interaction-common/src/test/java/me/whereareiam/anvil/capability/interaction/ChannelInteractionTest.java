package me.whereareiam.anvil.capability.interaction;

import me.whereareiam.anvil.capability.interaction.channel.InteractionChannel;
import me.whereareiam.anvil.capability.interaction.model.BlockUse;
import me.whereareiam.anvil.capability.interaction.model.EntityUse;
import me.whereareiam.anvil.capability.interaction.model.ItemUse;
import me.whereareiam.anvil.capability.interaction.type.BlockFace;
import me.whereareiam.anvil.capability.interaction.type.EntityInteraction;
import me.whereareiam.anvil.capability.interaction.type.Hand;
import me.whereareiam.anvil.capability.world.model.block.BlockPosition;
import me.whereareiam.anvil.player.api.channel.TestProtocolChannel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChannelInteractionTest {
	private final TestProtocolChannel channel = new TestProtocolChannel(Set.of(InteractionChannel.ID));
	private final ChannelInteraction interaction = new ChannelInteraction(channel);

	ChannelInteractionTest() {
		channel.answer(InteractionChannel.ITEM, use -> null);
		channel.answer(InteractionChannel.BLOCK, use -> null);
		channel.answer(InteractionChannel.ENTITY, use -> null);
	}

	@Test
	void requestsAnItemUseWithTheHand() {
		interaction.useItem(Hand.MAIN);

		assertEquals(List.of("interaction.item"), channel.requests());
		assertEquals(List.of(new ItemUse(Hand.MAIN)), channel.payloads());
	}

	@Test
	void requestsABlockUseWithTheCoordinatesFaceAndHand() {
		interaction.block(BlockPosition.builder().x(4).y(-60).z(-7).build(), BlockFace.WEST, Hand.OFF);

		assertEquals(List.of("interaction.block"), channel.requests());
		assertEquals(List.of(new BlockUse(4, -60, -7, BlockFace.WEST, Hand.OFF)), channel.payloads());
	}

	@Test
	void requestsAnEntityInteractionWithItsKindAndHand() {
		interaction.entity(42, EntityInteraction.ATTACK, Hand.MAIN);
		interaction.entity(43, EntityInteraction.INTERACT, Hand.OFF);

		assertEquals(List.of("interaction.entity", "interaction.entity"), channel.requests());
		assertEquals(List.of(new EntityUse(42, EntityInteraction.ATTACK, Hand.MAIN),
				new EntityUse(43, EntityInteraction.INTERACT, Hand.OFF)), channel.payloads());
	}
}
