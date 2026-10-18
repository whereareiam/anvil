package me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5;

import com.github.steveice10.mc.protocol.data.MagicValues;
import com.github.steveice10.mc.protocol.data.game.world.sound.BuiltinSound;
import com.github.steveice10.mc.protocol.data.game.world.sound.CustomSound;
import com.github.steveice10.mc.protocol.data.game.world.sound.SoundCategory;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerEntitySoundEffectPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.ServerPlayBuiltinSoundPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.world.ServerPlaySoundPacket;
import me.whereareiam.anvil.protocol.java.api.model.world.Vec3d;
import me.whereareiam.anvil.protocol.java.api.packet.sound.ClientboundSound;
import me.whereareiam.anvil.protocol.java.api.packet.sound.ClientboundSoundEntity;
import me.whereareiam.anvil.protocol.java.api.type.SoundSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SoundPacketsTest {
	private final SoundPackets packets = new SoundPackets();

	@Test
	void decodesBuiltInAndNamedSoundsAtAPosition() {
		assertEquals(ClientboundSound.builder()
				.sound("minecraft:entity.player.levelup")
				.source(SoundSource.PLAYERS)
				.position(new Vec3d(1.5D, 64D, -2D))
				.volume(1F)
				.pitch(2F)
				.build(), packets.decode(new ServerPlayBuiltinSoundPacket(BuiltinSound.ENTITY_PLAYER_LEVELUP,
				SoundCategory.PLAYER, 1.5D, 64D, -2D, 1F, 2F)));
		assertEquals(ClientboundSound.builder()
				.sound("example:alert")
				.source(SoundSource.RECORDS)
				.position(new Vec3d(0D, 70D, 0D))
				.volume(0.5F)
				.pitch(1F)
				.build(), packets.decode(new ServerPlaySoundPacket(new CustomSound("example:alert"),
				SoundCategory.RECORD, 0D, 70D, 0D, 0.5F, 1F)));
	}

	@Test
	void resolvesTheSoundFollowingAnEntityThroughTheLibrarysMapping() {
		int levelUp = MagicValues.value(Integer.class, BuiltinSound.ENTITY_PLAYER_LEVELUP);

		assertEquals(ClientboundSoundEntity.builder()
				.sound("minecraft:entity.player.levelup")
				.source(SoundSource.MASTER)
				.entityId(42)
				.volume(1F)
				.pitch(0.5F)
				.build(), packets.decode(new ServerEntitySoundEffectPacket(levelUp, SoundCategory.MASTER, 42, 1F,
				0.5F)));
		assertEquals("999999", ((ClientboundSoundEntity) packets.decode(new ServerEntitySoundEffectPacket(999999,
				SoundCategory.MASTER, 42, 1F, 1F))).getSound());
	}
}
