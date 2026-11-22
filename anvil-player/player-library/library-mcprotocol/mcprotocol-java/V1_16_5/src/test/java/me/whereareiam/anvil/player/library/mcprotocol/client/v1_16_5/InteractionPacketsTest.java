package me.whereareiam.anvil.player.library.mcprotocol.client.v1_16_5;

import com.github.steveice10.mc.protocol.data.game.entity.player.Animation;
import com.github.steveice10.mc.protocol.data.game.entity.player.Hand;
import com.github.steveice10.mc.protocol.packet.ingame.client.player.ClientPlayerSwingArmPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerKeepAlivePacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.ServerEntityAnimationPacket;
import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.packet.interaction.ClientboundAnimate;
import me.whereareiam.anvil.edition.java.protocol.packet.interaction.ServerboundSwing;
import me.whereareiam.anvil.edition.java.protocol.type.interaction.AnimateAction;
import me.whereareiam.anvil.edition.java.protocol.type.interaction.InteractionHand;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class InteractionPacketsTest {
	private final InteractionPackets packets = new InteractionPackets();

	@Test
	void readsTheAnimationsTheServerPlaysOfEntitiesWithTheOffHandsSwingAsTheLibraryNamesIt() {
		assertEquals(new ClientboundAnimate(new EntityId(42), AnimateAction.SWING_MAIN_HAND),
				packets.decode(new ServerEntityAnimationPacket(42, Animation.SWING_ARM)));
		assertEquals(new ClientboundAnimate(new EntityId(42), AnimateAction.HURT),
				packets.decode(new ServerEntityAnimationPacket(42, Animation.DAMAGE)));
		assertEquals(new ClientboundAnimate(new EntityId(42), AnimateAction.SWING_OFF_HAND),
				packets.decode(new ServerEntityAnimationPacket(42, Animation.EAT_FOOD)));
		assertEquals(new ClientboundAnimate(new EntityId(7), AnimateAction.MAGIC_CRITICAL_HIT),
				packets.decode(new ServerEntityAnimationPacket(7, Animation.ENCHANTMENT_CRITICAL_HIT)));
		assertNull(packets.decode(new ServerKeepAlivePacket(7)));
	}

	@Test
	void swingsTheArmOfEachHand() {
		assertEquals(Hand.OFF_HAND, assertInstanceOf(ClientPlayerSwingArmPacket.class,
				packets.encode(new ServerboundSwing(InteractionHand.OFF_HAND))).getHand());
	}
}
