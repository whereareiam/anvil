package me.whereareiam.anvil.player.library.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.state.ClientboundRemoveMobEffect;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.state.ClientboundUpdateMobEffect;
import me.whereareiam.anvil.player.library.mcprotocol.client.translation.PacketDemand;
import me.whereareiam.anvil.player.library.mcprotocol.client.translation.PacketTable;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.entity.Effect;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundRemoveMobEffectPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.ClientboundUpdateMobEffectPacket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The release's packet table decodes the effects of entities by the keys of the library's effects, the effects this
 * release added included.
 */
class EffectPacketsTest {
	private final PacketTable<Packet> packets = McProtocolClientAdapter.packets(PacketDemand.EVERY);

	@Test
	void decodesAnAddedEffectByItsKey() {
		assertEquals(ClientboundUpdateMobEffect.builder().entityId(new EntityId(42)).effect("minecraft:jump_boost").amplifier(1)
						.duration(600).build(),
				packets.decode(new ClientboundUpdateMobEffectPacket(42, Effect.JUMP_BOOST, 1, 600, true, false, true,
						false)));
	}

	@Test
	void decodesARemovedEffectByItsKey() {
		assertEquals(new ClientboundRemoveMobEffect(new EntityId(42), "minecraft:speed"),
				packets.decode(new ClientboundRemoveMobEffectPacket(42, Effect.SPEED)));
		assertEquals(new ClientboundRemoveMobEffect(new EntityId(42), "minecraft:breath_of_the_nautilus"),
				packets.decode(new ClientboundRemoveMobEffectPacket(42, Effect.BREATH_OF_THE_NAUTILUS)));
	}
}
