package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_1;

import me.whereareiam.anvil.protocol.java.api.model.sound.SoundPosition;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.sound.ClientboundSound;
import me.whereareiam.anvil.protocol.java.api.packet.sound.ClientboundSoundEntity;
import me.whereareiam.anvil.protocol.java.api.type.SoundSource;
import org.geysermc.mcprotocollib.protocol.data.game.level.sound.BuiltinSound;
import org.geysermc.mcprotocollib.protocol.data.game.level.sound.CustomSound;
import org.geysermc.mcprotocollib.protocol.data.game.level.sound.SoundCategory;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSoundEntityPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSoundPacket;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

class SoundPacketsTest {
	private final SoundPackets packets = new SoundPackets();

	@Test
	void decodesABuiltInSoundAtAPositionByItsNamespacedKeyWithItsSeed() {
		ClientboundSoundPacket sound = new ClientboundSoundPacket(BuiltinSound.ENTITY_PLAYER_LEVELUP,
				SoundCategory.PLAYER, 1.5D, 64D, -2D, 1F, 2F, 7L);

		assertEquals(ClientboundSound.builder()
				.sound("minecraft:entity.player.levelup")
				.source(SoundSource.PLAYERS)
				.position(new SoundPosition(1.5D, 64D, -2D))
				.volume(1F)
				.pitch(2F)
				.seed(7L)
				.build(), packets.decode(sound));
	}

	@Test
	void decodesANamedSoundAtAPositionKeepingItsNamespace() {
		CustomSound alert = new CustomSound("example:alert", false, 16F);
		ClientboundSoundPacket sound = new ClientboundSoundPacket(alert, SoundCategory.RECORD, 0D, 70D, 0D, 0.5F, 1F, 0L);

		assertEquals(ClientboundSound.builder()
				.sound("example:alert")
				.source(SoundSource.RECORDS)
				.position(new SoundPosition(0D, 70D, 0D))
				.volume(0.5F)
				.pitch(1F)
				.build(), packets.decode(sound));
	}

	@Test
	void decodesASoundFollowingAnEntityAddingTheMinecraftNamespace() {
		CustomSound toast = new CustomSound("ui.toast.in", false, 16F);
		ClientboundSoundEntityPacket sound = new ClientboundSoundEntityPacket(toast, SoundCategory.MASTER, 42, 1F, 0.5F, 3L);

		assertEquals(ClientboundSoundEntity.builder()
				.sound("minecraft:ui.toast.in")
				.source(SoundSource.MASTER)
				.entityId(42)
				.volume(1F)
				.pitch(0.5F)
				.seed(3L)
				.build(), packets.decode(sound));
	}

	@Test
	void mapsEveryCategoryOfTheReleaseByName() {
		List<SoundSource> sources = Arrays.stream(SoundCategory.values())
				.map(category -> new ClientboundSoundEntityPacket(BuiltinSound.ENTITY_PLAYER_LEVELUP, category, 42, 1F, 1F, 0L))
				.map(sound -> assertInstanceOf(ClientboundSoundEntity.class, packets.decode(sound)).getSource())
				.toList();

		assertEquals(List.of(
				SoundSource.MASTER,
				SoundSource.MUSIC,
				SoundSource.RECORDS,
				SoundSource.WEATHER,
				SoundSource.BLOCKS,
				SoundSource.HOSTILE,
				SoundSource.NEUTRAL,
				SoundSource.PLAYERS,
				SoundSource.AMBIENT,
				SoundSource.VOICE
		), sources);
	}

	@Test
	void leavesPacketsOfOtherAreasToOtherTranslations() {
		assertNull(packets.decode(new ClientboundKeepAlivePacket(7)));
		assertNull(packets.encode(new UnknownPacket()));
	}

	@Test
	void carriesBothSoundPacketsOfTheRelease() {
		assertEquals(Set.of(ClientboundSound.class, ClientboundSoundEntity.class), packets.packets());
	}

	private static final class UnknownPacket implements ServerboundPacket {
	}
}
