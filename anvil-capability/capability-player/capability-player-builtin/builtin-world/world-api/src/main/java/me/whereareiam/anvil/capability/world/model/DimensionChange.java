package me.whereareiam.anvil.capability.world.model;

import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The player entering a dimension: joining a level, which the server does at the start of the game and again when a
 * proxy moves the player to another server, or respawning, which the server does after the player died and when it
 * moves the player to another dimension. Dimensions are named by the keys of their levels, with their namespace,
 * such as {@code minecraft:the_nether}; a server with several worlds of one kind gives each a level key of its own.
 */
@Value
public class DimensionChange {
	/**
	 * The dimension the player was in before, or {@code null} when its connection had not joined a level yet. It
	 * equals {@link #getTo()} when the server respawned the player in the dimension it was in.
	 */
	@Nullable String from;
	/**
	 * The dimension the player is in afterwards.
	 */
	@NotNull String to;
}
