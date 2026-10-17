package me.whereareiam.anvil.capability.sounds;

import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.capability.sounds.channel.SoundsChannel;
import me.whereareiam.anvil.capability.sounds.model.HeardSound;
import me.whereareiam.anvil.capability.sounds.model.SoundPosition;
import me.whereareiam.anvil.capability.sounds.type.SoundCategory;
import me.whereareiam.anvil.protocol.api.channel.ProtocolChannel;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.model.channel.EventDescriptor;
import me.whereareiam.anvil.protocol.api.model.channel.OperationDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelSoundsTest {
	private static final Duration TIMEOUT = Duration.ofSeconds(3);
	private static final HeardSound LEVEL_UP = sound("minecraft:entity.player.levelup", 1F);
	private static final HeardSound PLING = sound("minecraft:block.note_block.pling", 2F);
	private static final HeardSound ALERT = sound("example:alert", 1F);

	private final RecordingChannel channel = new RecordingChannel();
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

	/**
	 * Delivers published events synchronously; a wait whose condition does not hold fails immediately instead of
	 * timing out.
	 */
	private static final class RecordingChannel implements ProtocolChannel {
		private final Map<String, List<Consumer<Object>>> listeners = new HashMap<>();

		private <E> void publish(EventDescriptor<E> eventDescriptor, @Nullable E payload) {
			listeners.getOrDefault(eventDescriptor.getId(), List.of()).forEach(listener -> listener.accept(payload));
		}

		@Override
		public <Q, R> @Nullable R request(@NotNull OperationDescriptor<Q, R> operation, @Nullable Q request) {
			throw new AssertionError("Sounds send no requests");
		}

		@Override
		@SuppressWarnings("unchecked")
		public <E> @NotNull Subscription subscribe(@NotNull EventDescriptor<E> eventDescriptor, @NotNull Consumer<E> listener) {
			listeners.computeIfAbsent(eventDescriptor.getId(), ignored -> new ArrayList<>()).add((Consumer<Object>) listener);
			return () -> listeners.get(eventDescriptor.getId()).remove(listener);
		}

		@Override
		public void await(@NotNull BooleanSupplier condition, @NotNull String description, @NotNull Duration timeout) {
			if (!condition.getAsBoolean()) throw new IllegalStateException("Player did not " + description);
		}

		@Override
		public @NotNull Set<String> installedCapabilities() {
			return Set.of(SoundsChannel.ID);
		}
	}
}
