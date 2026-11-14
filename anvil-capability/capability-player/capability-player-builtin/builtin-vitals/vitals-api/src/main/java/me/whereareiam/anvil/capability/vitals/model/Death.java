package me.whereareiam.anvil.capability.vitals.model;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * One death of the player, as its client was told.
 */
@Value
public class Death {
	/**
	 * The message of the death screen, flattened to plain text as {@code Messages} flattens chat: a translated message
	 * keeps its key and arguments, such as {@code death.attack.genericKill Alice}.
	 */
	@NotNull String message;
	/**
	 * Whether the client respawned by itself, because the server turned the death screen off.
	 */
	boolean respawnedImmediately;
}
