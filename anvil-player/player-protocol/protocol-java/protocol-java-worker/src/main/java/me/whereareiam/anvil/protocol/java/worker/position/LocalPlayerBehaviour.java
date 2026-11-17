package me.whereareiam.anvil.protocol.java.worker.position;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * A behaviour of the vanilla client's own player that only some Minecraft versions have, as far as every Java Edition
 * player keeps it: how it ticks, loads its level and accepts teleports. It applies from {@code since}, when set, up to
 * before {@code until}, when set. Each constant names the vanilla code it stands for; how the player moves is the
 * feature's that simulates it.
 */
enum LocalPlayerBehaviour {
	/**
	 * Before 1.17 the client ticks its player only while the chunk it is in is loaded ({@code ClientLevel.tickNonPassenger}
	 * ticks an entity that is {@code inChunk}, which a chunk's arrival makes it); from 1.17 the level ticks the player
	 * always, as it ticks every player, also in a chunk the client lacks.
	 */
	TICKS_ONLY_IN_LOADED_CHUNK(null, "1.17"),
	/**
	 * From 1.21.2 a server teleport clamps the pitch to a quarter turn ({@code PositionMoveRotation.calculateAbsolute}).
	 */
	CLAMPED_TELEPORT_PITCH("1.21.2", null),
	/**
	 * From 1.21.4 the client ticks its player only once it has loaded the level it joined or respawned in, and then
	 * tells the server ({@code ServerboundPlayerLoadedPacket}): after the server's event that starts the level's loading,
	 * once the section its player is in has compiled, the player is outside the build height, a spectator or dead, or
	 * thirty seconds passed ({@code LevelLoadTracker}, {@code ClientPacketListener.tick}, {@code LocalPlayer.tick}).
	 */
	PLAYER_LOADED("1.21.4", null),
	/**
	 * Before 26.3 the client follows the acceptance of a teleport with its position and view
	 * ({@code ClientPacketListener.handleMovePlayer}); from 26.3 the acceptance carries them itself.
	 */
	TELEPORT_CONFIRMATION(null, "26.3");

	private final @Nullable MinecraftVersion since;
	private final @Nullable MinecraftVersion until;

	LocalPlayerBehaviour(@Nullable String since, @Nullable String until) {
		this.since = since == null ? null : MinecraftVersion.parse(since);
		this.until = until == null ? null : MinecraftVersion.parse(until);
	}

	/**
	 * Returns the behaviours a client version has.
	 *
	 * @param version the client's Minecraft version
	 * @return the behaviours whose range holds the version
	 */
	static @NotNull Set<LocalPlayerBehaviour> of(@NotNull MinecraftVersion version) {
		Set<LocalPlayerBehaviour> behaviours = EnumSet.noneOf(LocalPlayerBehaviour.class);
		Arrays.stream(values()).filter(behaviour -> behaviour.appliesTo(version)).forEach(behaviours::add);
		return behaviours;
	}

	/**
	 * Tells whether the client's own rules read which chunks it has: the wait for the level from 1.21.4, and the ticks
	 * of the player before 1.17, which need the chunk the player is in.
	 *
	 * @param behaviours the behaviours of the client's version
	 * @return whether the player's world must keep chunks
	 */
	static boolean readsChunks(@NotNull Set<LocalPlayerBehaviour> behaviours) {
		return behaviours.contains(PLAYER_LOADED) || behaviours.contains(TICKS_ONLY_IN_LOADED_CHUNK);
	}

	/**
	 * Tells whether a client of a version behaves so.
	 *
	 * @param version the client's Minecraft version
	 * @return whether the version lies in the behaviour's range
	 */
	boolean appliesTo(@NotNull MinecraftVersion version) {
		return (since == null || version.isAtLeast(since)) && (until == null || !version.isAtLeast(until));
	}
}
