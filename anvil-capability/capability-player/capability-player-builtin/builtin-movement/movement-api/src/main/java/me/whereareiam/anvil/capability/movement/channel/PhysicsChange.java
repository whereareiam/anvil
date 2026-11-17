package me.whereareiam.anvil.capability.movement.channel;

import lombok.Value;
import org.jetbrains.annotations.Nullable;

/**
 * The worker's answer to turning physics on or off: whether the change took effect, or why the player's release cannot
 * simulate the client.
 */
@Value
public class PhysicsChange {
	/**
	 * Why physics stays off, such as a release Anvil ships no block data for, or {@code null} when the change took
	 * effect.
	 */
	@Nullable String refusal;
}
