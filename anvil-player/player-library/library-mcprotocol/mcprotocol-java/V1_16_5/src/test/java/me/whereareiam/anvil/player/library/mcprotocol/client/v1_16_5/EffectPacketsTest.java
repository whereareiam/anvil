package me.whereareiam.anvil.player.library.mcprotocol.client.v1_16_5;

import com.github.steveice10.mc.protocol.data.game.entity.Effect;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerKeepAlivePacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.ServerEntityEffectPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.entity.ServerEntityRemoveEffectPacket;
import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.state.ClientboundRemoveMobEffect;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.state.ClientboundUpdateMobEffect;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class EffectPacketsTest {
	private final EffectPackets packets = new EffectPackets();

	@Test
	void decodesAnEffectByItsVanillaKey() {
		assertEquals(ClientboundUpdateMobEffect.builder().entityId(new EntityId(7)).effect("minecraft:speed").amplifier(1)
						.duration(600).build(),
				packets.decode(new ServerEntityEffectPacket(7, Effect.FASTER_MOVEMENT, 1, 600, false, true)));
		assertEquals(ClientboundUpdateMobEffect.builder().entityId(new EntityId(7)).effect("minecraft:haste").amplifier(0)
						.duration(200).build(),
				packets.decode(new ServerEntityEffectPacket(7, Effect.FASTER_DIG, 0, 200, true, false)));
	}

	@Test
	void decodesTheRemovalOfAnEffectByItsVanillaKey() {
		assertEquals(new ClientboundRemoveMobEffect(new EntityId(7), "minecraft:jump_boost"),
				packets.decode(new ServerEntityRemoveEffectPacket(7, Effect.JUMP)));
	}

	@Test
	void namesEveryEffectOfTheLibraryByTheVanillaKeyOfItsNumber() {
		assertEquals(List.of(
				"minecraft:speed", "minecraft:slowness", "minecraft:haste", "minecraft:mining_fatigue",
				"minecraft:strength", "minecraft:instant_health", "minecraft:instant_damage", "minecraft:jump_boost",
				"minecraft:nausea", "minecraft:regeneration", "minecraft:resistance", "minecraft:fire_resistance",
				"minecraft:water_breathing", "minecraft:invisibility", "minecraft:blindness", "minecraft:night_vision",
				"minecraft:hunger", "minecraft:weakness", "minecraft:poison", "minecraft:wither",
				"minecraft:health_boost", "minecraft:absorption", "minecraft:saturation", "minecraft:glowing",
				"minecraft:levitation", "minecraft:luck", "minecraft:unluck", "minecraft:slow_falling",
				"minecraft:conduit_power", "minecraft:dolphins_grace", "minecraft:bad_omen",
				"minecraft:hero_of_the_village"
		), Arrays.stream(Effect.values()).map(this::key).toList());
	}

	@Test
	void leavesPacketsOfOtherAreasToOtherTranslations() {
		assertNull(packets.decode(new ServerKeepAlivePacket(7)));
		assertNull(packets.encode(new UnknownPacket()));
	}

	@Test
	void carriesTheEffectsOfEntities() {
		assertEquals(Set.of(ClientboundUpdateMobEffect.class, ClientboundRemoveMobEffect.class), packets.packets());
	}

	private String key(Effect effect) {
		return assertInstanceOf(ClientboundRemoveMobEffect.class,
				packets.decode(new ServerEntityRemoveEffectPacket(7, effect))).effect();
	}

	private static final class UnknownPacket implements ServerboundPacket {
	}
}
