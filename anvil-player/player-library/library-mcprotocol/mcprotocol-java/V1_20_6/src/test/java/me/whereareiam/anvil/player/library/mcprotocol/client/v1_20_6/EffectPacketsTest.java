package me.whereareiam.anvil.player.library.mcprotocol.client.v1_20_6;

import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.state.ClientboundRemoveMobEffect;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.state.ClientboundUpdateMobEffect;
import org.geysermc.mcprotocollib.protocol.data.game.entity.Effect;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundRemoveMobEffectPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundUpdateMobEffectPacket;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EffectPacketsTest {
	private final EffectPackets packets = new EffectPackets();

	@Test
	void decodesAnEffectByTheRegistryKeyOfTheLibrarysNumberedEffect() {
		assertEquals(ClientboundUpdateMobEffect.builder().entityId(new EntityId(7)).effect("minecraft:speed").amplifier(1)
						.duration(600).build(),
				packets.decode(new ClientboundUpdateMobEffectPacket(7, Effect.SPEED, 1, 600, false, true, true,
						false)));
		assertEquals(ClientboundUpdateMobEffect.builder().entityId(new EntityId(7)).effect("minecraft:jump_boost").duration(-1)
						.build(),
				packets.decode(new ClientboundUpdateMobEffectPacket(7, Effect.JUMP_BOOST, 0, -1, true, false, false,
						false)));
		assertEquals("minecraft:hero_of_the_village", ((ClientboundUpdateMobEffect) packets.decode(
				new ClientboundUpdateMobEffectPacket(7, Effect.HERO_OF_THE_VILLAGE, 0, 20, false, true, true, false)))
				.getEffect());
	}

	@Test
	void decodesARemovedEffect() {
		assertEquals(new ClientboundRemoveMobEffect(new EntityId(7), "minecraft:slow_falling"),
				packets.decode(new ClientboundRemoveMobEffectPacket(7, Effect.SLOW_FALLING)));
	}

	@Test
	void leavesPacketsOfOtherAreasToOtherTranslations() {
		assertNull(packets.decode(new ClientboundKeepAlivePacket(7)));
		assertNull(packets.encode(new UnknownPacket()));
	}

	@Test
	void carriesTheEffectPackets() {
		assertEquals(Set.of(ClientboundUpdateMobEffect.class, ClientboundRemoveMobEffect.class), packets.packets());
	}

	private static final class UnknownPacket implements ServerboundPacket {
	}
}
