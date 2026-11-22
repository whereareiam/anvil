package me.whereareiam.anvil.edition.java.protocol.worker.position;

import me.whereareiam.anvil.edition.java.protocol.JavaWorld;
import me.whereareiam.anvil.edition.java.protocol.model.world.Level;
import me.whereareiam.anvil.edition.java.protocol.model.world.chunk.ChunkPosition;
import me.whereareiam.anvil.edition.java.protocol.type.player.GameMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * The client's wait for the level its player joined or respawned in, from Minecraft 1.21.4 ({@code LevelLoadTracker}):
 * it waits for the server's event that starts the level's loading, then for the section the player is in to compile,
 * and then the client ticks its player and tells the server it loaded. Earlier releases have nothing to wait for.
 *
 * <p>The vanilla client compiles a section on worker threads once its chunk and the eight around it are loaded, and
 * fades it in over the next frames. Anvil draws nothing, so it takes the frame after each client tick
 * ({@code Minecraft.runTick} renders after its ticks) to compile every section it could: a section compiled in a frame
 * counts from the next tick on. Once the server started the loading, the vanilla client lets the player in thirty
 * seconds after the login anyway, which Anvil counts in client ticks.</p>
 *
 * <p>Not thread-safe; its owner serializes packets and ticks.</p>
 */
final class LevelLoading {
	/**
	 * Thirty seconds of client ticks, after which the client lets the player into its level anyway.
	 */
	private static final int TIMEOUT_TICKS = 30 * 20;

	private final @NotNull Set<ChunkPosition> compiled = new HashSet<>();
	private @NotNull Stage stage;
	private int ticksWaited;

	/**
	 * Starts the wait of a new player, which a release from 1.21.4 waits through and an earlier release has loaded.
	 *
	 * @param behaviours the behaviours of the client's version
	 */
	LevelLoading(@NotNull Set<LocalPlayerBehaviour> behaviours) {
		this.stage = behaviours.contains(LocalPlayerBehaviour.PLAYER_LOADED) ? Stage.WAITING_FOR_SERVER : Stage.LOADED;
	}

	/**
	 * Tells whether the client has loaded its level and ticks its player.
	 */
	boolean isLoaded() {
		return stage == Stage.LOADED;
	}

	/**
	 * Takes the server's event that starts the level's loading, after which the client waits for its player's section.
	 */
	void loadingStarted() {
		if (stage == Stage.WAITING_FOR_SERVER) stage = Stage.WAITING_FOR_PLAYER_CHUNK;
	}

	/**
	 * Takes the level as loaded at once, as a player placed without the server's handshake has it.
	 */
	void loaded() {
		stage = Stage.LOADED;
	}

	/**
	 * Runs the wait at the start of a client tick, as the connection's tick runs it before the level's entities tick.
	 *
	 * @param chunk the chunk the player is in, or {@code null} while the client knows no position
	 * @param y the height of the player's feet
	 * @param world the client's world
	 * @param state what the server set of the player's entity
	 * @return whether the client loaded its level this tick, which it then tells the server
	 */
	boolean tick(@Nullable ChunkPosition chunk, int y, @NotNull JavaWorld world, @NotNull ServerState state) {
		if (stage == Stage.LOADED) return false;
		ticksWaited++;
		if (stage != Stage.WAITING_FOR_PLAYER_CHUNK) return false;
		if (ticksWaited <= TIMEOUT_TICKS && !ready(chunk, y, world, state)) return false;

		stage = Stage.LOADED;
		return true;
	}

	/**
	 * Renders the frame after a client tick, which compiles the player's section once its chunk and the eight around it
	 * are loaded.
	 *
	 * @param chunk the chunk the player is in, or {@code null} while the client knows no position
	 * @param world the client's world
	 */
	void frame(@Nullable ChunkPosition chunk, @NotNull JavaWorld world) {
		if (stage == Stage.LOADED || chunk == null || !surrounded(chunk, world)) return;

		compiled.add(chunk);
	}

	private boolean ready(@Nullable ChunkPosition chunk, int y, @NotNull JavaWorld world, @NotNull ServerState state) {
		if (chunk == null) return false;

		Optional<Level> level = world.level();
		boolean outside = level.isPresent() && !level.get().contains(y);
		return outside || state.getGameMode() == GameMode.SPECTATOR || state.isDead() || compiled.contains(chunk);
	}

	private static boolean surrounded(@NotNull ChunkPosition chunk, @NotNull JavaWorld world) {
		for (int x = -1; x <= 1; x++)
			for (int z = -1; z <= 1; z++)
				if (!world.isLoaded(new ChunkPosition(chunk.getX() + x, chunk.getZ() + z))) return false;
		return true;
	}

	/**
	 * Where the wait stands.
	 */
	private enum Stage {
		WAITING_FOR_SERVER,
		WAITING_FOR_PLAYER_CHUNK,
		LOADED
	}
}
