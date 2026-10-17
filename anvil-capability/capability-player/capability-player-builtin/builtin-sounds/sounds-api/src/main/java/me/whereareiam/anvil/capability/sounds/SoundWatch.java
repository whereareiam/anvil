package me.whereareiam.anvil.capability.sounds;

import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.capability.sounds.model.HeardSound;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.function.Predicate;

/**
 * Follows the sounds a player hears from the moment the watch was created. Each wait continues after the sound the
 * previous one found, so a sound played again is matched once per wait and never by an occurrence from before the
 * watch. A key without a namespace means the {@code minecraft} namespace.
 *
 * <pre>{@code
 * SoundWatch sounds = alice.capability(Sounds.class).watch();
 * messages.command("example announce");
 * HeardSound ping = sounds.heard(sound -> sound.is("block.note_block.pling") && sound.getPitch() > 1F, timeout);
 * }</pre>
 */
public interface SoundWatch {
	/**
	 * Waits, using the default timeout, for the next sound with the supplied key.
	 *
	 * @param key sound key, such as {@code entity.player.levelup} or {@code myplugin:alert}
	 * @return the matching sound
	 */
	default @NotNull HeardSound heard(@NotNull String key) {
		return heard(key, SimulatedPlayer.DEFAULT_TIMEOUT);
	}

	/**
	 * Waits for the next sound with the supplied key.
	 *
	 * @param key sound key, such as {@code entity.player.levelup} or {@code myplugin:alert}
	 * @param timeout maximum wait
	 * @return the matching sound
	 * @throws IllegalStateException when no such sound is heard in time
	 */
	default @NotNull HeardSound heard(@NotNull String key, @NotNull Duration timeout) {
		return heard(sound -> sound.is(key), timeout);
	}

	/**
	 * Waits for the next sound that satisfies a condition. The following wait looks only at later sounds.
	 *
	 * @param matcher condition on the sound
	 * @param timeout maximum wait
	 * @return the matching sound
	 * @throws IllegalStateException when no such sound is heard in time
	 */
	@NotNull HeardSound heard(@NotNull Predicate<HeardSound> matcher, @NotNull Duration timeout);

	/**
	 * Expects that no sound with the supplied key is heard for a duration.
	 *
	 * @param key forbidden sound key
	 * @param duration how long no such sound may be heard
	 * @throws IllegalStateException as soon as such a sound has been heard since the previous wait
	 */
	default void neverHeard(@NotNull String key, @NotNull Duration duration) {
		neverHeard(sound -> sound.is(key), duration);
	}

	/**
	 * Expects that no sound satisfying a condition is heard for a duration. It fails as soon as such a sound has been
	 * heard since the previous wait, naming its key, and returns normally once the duration has passed without one.
	 * It does not move the watch.
	 *
	 * @param matcher condition on the sound
	 * @param duration how long no matching sound may be heard
	 * @throws IllegalStateException when such a sound is heard
	 */
	void neverHeard(@NotNull Predicate<HeardSound> matcher, @NotNull Duration duration);

	/**
	 * Expects the next sound that satisfies a condition, for {@link SimulatedPlayer#awaitFirst}. Once it is met,
	 * {@link #heard(Predicate, Duration)} with the same condition returns the sound at once.
	 *
	 * @param matcher condition on the sound
	 * @return expectation met once such a sound has been heard since the previous wait
	 */
	@NotNull Expectation expectHeard(@NotNull Predicate<HeardSound> matcher);

	/**
	 * Returns the sounds heard since the previous wait, without waiting or moving the watch.
	 *
	 * @return unread sounds, oldest first
	 */
	@NotNull List<HeardSound> unread();
}
