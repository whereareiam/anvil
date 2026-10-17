package me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2;

import com.github.steveice10.mc.protocol.data.game.level.sound.BuiltinSound;
import com.github.steveice10.mc.protocol.data.game.level.sound.CustomSound;
import com.github.steveice10.mc.protocol.data.game.level.sound.SoundCategory;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundKeepAlivePacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundSoundEntityPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.level.ClientboundCustomSoundPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.level.ClientboundSoundPacket;
import me.whereareiam.anvil.protocol.java.api.model.sound.SoundPosition;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.sound.ClientboundCustomSound;
import me.whereareiam.anvil.protocol.java.api.packet.sound.ClientboundSound;
import me.whereareiam.anvil.protocol.java.api.packet.sound.ClientboundSoundEntity;
import me.whereareiam.anvil.protocol.java.api.type.SoundSource;
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
	void decodesABuiltInSoundAtAPositionByItsNamespacedKeyWithoutASeed() {
		ClientboundSoundPacket sound = new ClientboundSoundPacket(BuiltinSound.ENTITY_PLAYER_LEVELUP,
				SoundCategory.PLAYER, 1.5D, 64D, -2D, 1F, 2F);

		assertEquals(ClientboundSound.builder()
				.sound("minecraft:entity.player.levelup")
				.source(SoundSource.PLAYERS)
				.position(new SoundPosition(1.5D, 64D, -2D))
				.volume(1F)
				.pitch(2F)
				.seed(0L)
				.build(), packets.decode(sound));
	}

	@Test
	void decodesANamedSoundKeepingItsNamespaceOrAddingTheMinecraftOne() {
		ClientboundCustomSoundPacket alert = new ClientboundCustomSoundPacket(new CustomSound("example:alert"),
				SoundCategory.RECORD, 0D, 70D, 0D, 0.5F, 1F);
		ClientboundCustomSoundPacket toast = new ClientboundCustomSoundPacket(new CustomSound("ui.toast.in"),
				SoundCategory.MASTER, 0D, 70D, 0D, 1F, 1F);

		assertEquals(ClientboundCustomSound.builder()
				.sound("example:alert")
				.source(SoundSource.RECORDS)
				.position(new SoundPosition(0D, 70D, 0D))
				.volume(0.5F)
				.pitch(1F)
				.build(), packets.decode(alert));
		assertEquals("minecraft:ui.toast.in", assertInstanceOf(ClientboundCustomSound.class,
				packets.decode(toast)).getSound());
	}

	@Test
	void decodesANamedSoundThatIsBuiltIn() {
		ClientboundCustomSoundPacket sound = new ClientboundCustomSoundPacket(BuiltinSound.BLOCK_NOTE_BLOCK_PLING,
				SoundCategory.BLOCK, 0D, 0D, 0D, 1F, 1F);

		assertEquals("minecraft:block.note_block.pling", assertInstanceOf(ClientboundCustomSound.class,
				packets.decode(sound)).getSound());
	}

	@Test
	void resolvesTheRegistryIdOfASoundFollowingAnEntityAndKeepsAnUnknownOneAsItsNumber() {
		int pling = BuiltinSound.BLOCK_NOTE_BLOCK_PLING.ordinal();
		ClientboundSoundEntityPacket known = new ClientboundSoundEntityPacket(pling, SoundCategory.BLOCK, 42, 1F, 1F);
		ClientboundSoundEntityPacket unknown = new ClientboundSoundEntityPacket(BuiltinSound.VALUES.length,
				SoundCategory.HOSTILE, 42, 1F, 1F);

		assertEquals(ClientboundSoundEntity.builder()
				.sound("minecraft:block.note_block.pling")
				.source(SoundSource.BLOCKS)
				.entityId(42)
				.volume(1F)
				.pitch(1F)
				.build(), packets.decode(known));
		assertEquals(Integer.toString(BuiltinSound.VALUES.length),
				assertInstanceOf(ClientboundSoundEntity.class, packets.decode(unknown)).getSound());
	}

	@Test
	void mapsEveryCategoryOfTheRelease() {
		List<SoundSource> sources = Arrays.stream(SoundCategory.values())
				.map(category -> new ClientboundSoundEntityPacket(0, category, 42, 1F, 1F))
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
	void carriesEverySoundPacketOfTheRelease() {
		assertEquals(Set.of(ClientboundSound.class, ClientboundCustomSound.class, ClientboundSoundEntity.class),
				packets.packets());
	}

	private static final class UnknownPacket implements ServerboundPacket {
	}
}
