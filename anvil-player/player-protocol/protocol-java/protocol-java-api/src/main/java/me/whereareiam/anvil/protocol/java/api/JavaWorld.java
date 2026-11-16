package me.whereareiam.anvil.protocol.java.api;

import me.whereareiam.anvil.protocol.java.api.model.world.chunk.ChunkPosition;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.function.Function;

/**
 * The world one simulated player's client knows, which the packets its {@link JavaProtocol} receives keep up to date:
 * the level the login and each respawn name, and the chunk columns the server sent with the block updates that
 * followed. The world follows the player's current connection: a login replaces it, a respawn into a level of another
 * key empties it, and so do a return to the configuration phase and a closed connection.
 *
 * <p>The world knows the level from the start, but keeps chunks only from the first call of
 * {@link JavaProtocol#world()} on; until then the player's release does not even decode them, so a player that nothing
 * asks about blocks spends nothing on them. Bindings ask for the world while they bind, before the player connects.</p>
 *
 * <p>The world may be read from any thread. Each packet applies whole: a reader never sees part of a column or part of
 * a multi-block update. Each call of this interface sees the world at one moment; {@link #read} sees it at one moment
 * for a whole reader, such as a physics tick that reads many blocks. Packet listeners see the world with the packet
 * they receive already applied.</p>
 *
 * <pre>{@code
 * JavaWorld world = player.protocol(JavaProtocol.class).world();
 * Set<ChunkPosition> around = world.loadedChunks();
 * int[] column = world.read(view -> new int[] {
 *         view.blockState(x, y - 1, z).orElse(LevelChunkSection.AIR),
 *         view.blockState(x, y - 2, z).orElse(LevelChunkSection.AIR)
 * });
 * }</pre>
 */
public interface JavaWorld extends WorldView {
	/**
	 * Returns the chunk columns the client has.
	 *
	 * @return an immutable snapshot of their positions
	 */
	@NotNull Set<ChunkPosition> loadedChunks();

	/**
	 * Reads the world at one moment: no packet applies while the reader runs. The reader must not wait for packets of
	 * the player, which cannot apply until it returns, and should return quickly, since the player's connection waits
	 * for it.
	 *
	 * @param reader reads what it needs through the view, which is valid only while the reader runs
	 * @param <T> what the reader returns
	 * @return what the reader returned
	 */
	<T> T read(@NotNull Function<? super WorldView, ? extends T> reader);
}
