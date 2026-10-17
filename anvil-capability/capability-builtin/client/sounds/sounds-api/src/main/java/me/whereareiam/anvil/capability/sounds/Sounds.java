package me.whereareiam.anvil.capability.sounds;

import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.capability.sounds.model.HeardSound;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Observes the sounds the server plays to the player.
 *
 * <p>The history holds every sound the player is told to play, in order and across reconnects: sounds at a position
 * and sounds that follow an entity, whether the server names a sound or refers to one by its registry ID. Every
 * sound is identified by its namespaced key, such as {@code minecraft:entity.player.levelup}. Sounds a client plays
 * on its own, such as footsteps or block breaking it predicts, never reach it as packets and are not recorded.</p>
 */
public interface Sounds extends PlayerCapability {
	/**
	 * Returns an immutable snapshot of the sounds heard so far, without waiting.
	 *
	 * @return heard sounds, oldest first
	 */
	@NotNull List<HeardSound> history();

	/**
	 * Starts following the sounds the player hears from now on. Each watch keeps its own position, so several can
	 * follow the same player independently.
	 *
	 * <pre>{@code
	 * SoundWatch sounds = alice.capability(Sounds.class).watch();
	 * messages.command("example announce");
	 * sounds.heard("block.note_block.pling");
	 * sounds.neverHeard("entity.villager.no", Duration.ofSeconds(2));
	 * }</pre>
	 *
	 * @return a watch that starts after the last sound heard so far
	 */
	@NotNull SoundWatch watch();
}
