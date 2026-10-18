package me.whereareiam.anvil.protocol.mcprotocol.client.v1_19_4;

import com.github.steveice10.mc.protocol.data.game.level.sound.BuiltinSound;
import com.github.steveice10.mc.protocol.data.game.level.sound.CustomSound;
import com.github.steveice10.mc.protocol.data.game.level.sound.SoundCategory;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundKeepAlivePacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundSoundEntityPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.level.ClientboundSoundPacket;
import me.whereareiam.anvil.protocol.java.api.model.world.Vec3d;
import me.whereareiam.anvil.protocol.java.api.packet.sound.ClientboundSound;
import me.whereareiam.anvil.protocol.java.api.packet.sound.ClientboundSoundEntity;
import me.whereareiam.anvil.protocol.java.api.type.SoundSource;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
				.position(new Vec3d(1.5D, 64D, -2D))
				.volume(1F)
				.pitch(2F)
				.seed(7L)
				.build(), packets.decode(sound));
	}

	@Test
	void decodesANamedSoundAtAPositionKeepingItsNamespace() {
		CustomSound alert = new CustomSound("example:alert", false, 16F);
		ClientboundSoundPacket sound = new ClientboundSoundPacket(alert, SoundCategory.RECORD, 0D, 70D, 0D, 0.5F, 1F,
				0L);

		assertEquals(ClientboundSound.builder()
				.sound("example:alert")
				.source(SoundSource.RECORDS)
				.position(new Vec3d(0D, 70D, 0D))
				.volume(0.5F)
				.pitch(1F)
				.build(), packets.decode(sound));
	}

	@Test
	void decodesASoundFollowingAnEntityResolvedByTheLibrary() {
		ClientboundSoundEntityPacket sound = new ClientboundSoundEntityPacket(BuiltinSound.ENTITY_PLAYER_LEVELUP,
				SoundCategory.MASTER, 42, 1F, 0.5F, 3L);

		assertEquals(ClientboundSoundEntity.builder()
				.sound("minecraft:entity.player.levelup")
				.source(SoundSource.MASTER)
				.entityId(42)
				.volume(1F)
				.pitch(0.5F)
				.seed(3L)
				.build(), packets.decode(sound));
	}

	@Test
	void leavesPacketsOfOtherAreasToOtherTranslations() {
		assertNull(packets.decode(new ClientboundKeepAlivePacket(7)));
	}

	@Test
	void carriesBothSoundPackets() {
		assertEquals(Set.of(ClientboundSound.class, ClientboundSoundEntity.class), packets.packets());
	}
}
