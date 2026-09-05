package me.whereareiam.anvil.api.type;

import org.jetbrains.annotations.NotNull;

/**
 * Well-known platform identifiers shipped with Anvil.
 */
public final class Platforms {
	/**
	 * Paper server provider identifier.
	 */
	public static final @NotNull String PAPER = "paper";
	/**
	 * Spigot server provider identifier.
	 */
	public static final @NotNull String SPIGOT = "spigot";
	/**
	 * Velocity proxy provider identifier.
	 */
	public static final @NotNull String VELOCITY = "velocity";
	/**
	 * BungeeCord proxy provider identifier.
	 */
	public static final @NotNull String BUNGEECORD = "bungeecord";

	private Platforms() {
		throw new AssertionError("No instances");
	}
}
