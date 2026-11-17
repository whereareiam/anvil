package me.whereareiam.anvil.capability.sounds;

import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.capability.sounds.channel.SoundsChannel;
import me.whereareiam.anvil.capability.sounds.model.HeardSound;
import me.whereareiam.anvil.capability.sounds.model.SoundPosition;
import me.whereareiam.anvil.capability.sounds.type.SoundCategory;
import me.whereareiam.anvil.protocol.api.channel.TestProtocolChannel;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelSoundsTest {
	private static final Duration TIMEOUT = Duration.ofSeconds(3);
	private static final HeardSound LEVEL_UP = sound("minecraft:entity.player.levelup", 1F);
	private static final HeardSound PLING = sound("minecraft:block.note_block.pling", 2F);
	private static final HeardSound ALERT = sound("example:alert", 1F);

	private final TestProtocolChannel channel = new TestProtocolChannel(Set.of(SoundsChannel.ID));
	private final ChannelSounds sounds = new ChannelSounds(channel);

	@Test
	void keepsEveryHeardSoundInOrder() {
		assertTrue(sounds.history().isEmpty());

		channel.publish(SoundsChannel.HEARD, LEVEL_UP);
		channel.publish(SoundsChannel.HEARD, ALERT);

		assertEquals(List.of(LEVEL_UP, ALERT), sounds.history());
		assertThrows(UnsupportedOperationException.class, () -> sounds.history().add(PLING));
	}

	@Test
	void matchesAKeyWithTheMinecraftNamespaceWhenItHasNone() {
		assertTrue(LEVEL_UP.is("entity.player.levelup"));
		assertTrue(LEVEL_UP.is("minecraft:entity.player.levelup"));
		assertTrue(ALERT.is("example:alert"));
		assertFalse(ALERT.is("alert"));
		assertFalse(LEVEL_UP.is("entity.player"));
	}

	@Test
	void watchesOnlySoundsHeardSinceItAndContinuesAfterEachMatch() {
		channel.publish(SoundsChannel.HEARD, PLING);
		SoundWatch watch = sounds.watch();

		assertEquals("Player did not hear a matching sound", assertThrows(IllegalStateException.class,
				() -> watch.heard("block.note_block.pling", TIMEOUT)).getMessage());

		channel.publish(SoundsChannel.HEARD, LEVEL_UP);
		channel.publish(SoundsChannel.HEARD, PLING);
		channel.publish(SoundsChannel.HEARD, sound("minecraft:block.note_block.pling", 0.5F));
		assertEquals(List.of(LEVEL_UP, PLING, sound("minecraft:block.note_block.pling", 0.5F)), watch.unread());
		assertEquals(PLING, watch.heard(sound -> sound.is("block.note_block.pling") && sound.getPitch() > 1F, TIMEOUT));
		assertEquals(sound("minecraft:block.note_block.pling", 0.5F), watch.heard("block.note_block.pling", TIMEOUT));
		assertThrows(IllegalStateException.class, () -> watch.heard("block.note_block.pling", TIMEOUT));
		assertEquals(List.of(), sounds.watch().unread());
	}

	@Test
	void expectsAMatchingSoundOnlySinceTheWatchStarted() {
		channel.publish(SoundsChannel.HEARD, ALERT);
		SoundWatch watch = sounds.watch();
		Expectation alert = watch.expectHeard(sound -> sound.is("example:alert"));

		assertFalse(alert.met());
		assertEquals("hear a matching sound", alert.getDescription());

		channel.publish(SoundsChannel.HEARD, ALERT);
		assertTrue(alert.met());
		assertEquals(ALERT, watch.heard("example:alert", TIMEOUT));
	}

	@Test
	void failsWhenAForbiddenSoundIsHeardSinceThePreviousWaitOnly() {
		channel.publish(SoundsChannel.HEARD, ALERT);
		SoundWatch watch = sounds.watch();

		watch.neverHeard("example:alert", TIMEOUT);

		channel.publish(SoundsChannel.HEARD, ALERT);
		assertEquals("Player heard a sound it must not hear: example:alert", assertThrows(IllegalStateException.class,
				() -> watch.neverHeard("example:alert", TIMEOUT)).getMessage());

		channel.publish(SoundsChannel.HEARD, LEVEL_UP);
		watch.heard("entity.player.levelup", TIMEOUT);
		watch.neverHeard("example:alert", TIMEOUT);
	}

	private static HeardSound sound(String key, float pitch) {
		return HeardSound.builder()
				.key(key)
				.category(SoundCategory.MASTER)
				.volume(1F)
				.pitch(pitch)
				.position(new SoundPosition(1D, 64D, -2D))
				.build();
	}
}
