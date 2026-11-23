package me.whereareiam.anvil.capability.world.channel;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.capability.world.model.block.BlockPosition;
import me.whereareiam.anvil.capability.world.model.environment.WorldState;
import me.whereareiam.anvil.capability.world.model.level.ChunkPosition;
import me.whereareiam.anvil.capability.world.model.level.DimensionChange;
import me.whereareiam.anvil.capability.world.model.level.ServerLevels;
import me.whereareiam.anvil.player.api.model.channel.EventDescriptor;
import me.whereareiam.anvil.player.api.model.channel.OperationDescriptor;

/**
 * The channel contract between the host world capability and its worker extensions: the capability ID both register
 * under, the events the worker emits and the operations through which the host reads the blocks the client knows and
 * opens and closes the watches of their areas.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class WorldChannel {
	/**
	 * Capability ID of the host provider and of every edition's worker extension.
	 */
	public static final String ID = "me.whereareiam.anvil.world";
	/**
	 * The player's current connection joined a server, before the level it joined.
	 */
	public static final EventDescriptor<ServerLevels> SERVER = new EventDescriptor<>(
			"world.server", ServerLevels.class
	);
	/**
	 * The player's current connection joined a level or respawned.
	 */
	public static final EventDescriptor<DimensionChange> DIMENSION = new EventDescriptor<>(
			"world.dimension", DimensionChange.class
	);
	/**
	 * The player's current connection ended while it was in a level, with the key of that level. The connection is on
	 * no server afterwards.
	 */
	public static final EventDescriptor<String> LEFT = new EventDescriptor<>("world.left", String.class);
	/**
	 * What the player's current connection was told about the world around its level changed, with the whole state
	 * afterwards.
	 */
	public static final EventDescriptor<WorldState> STATE = new EventDescriptor<>("world.state", WorldState.class);
	/**
	 * Reads one block of the player's level, which the answer leaves out while the client has no chunk holding it.
	 */
	public static final OperationDescriptor<BlockPosition, BlockLookup> BLOCK = new OperationDescriptor<>(
			"world.lookup", BlockPosition.class, BlockLookup.class
	);
	/**
	 * Tells whether the client has one chunk column.
	 */
	public static final OperationDescriptor<ChunkPosition, Boolean> CHUNK = new OperationDescriptor<>(
			"world.chunk", ChunkPosition.class, Boolean.class
	);
	/**
	 * Lists every chunk column the client has.
	 */
	public static final OperationDescriptor<Void, LoadedChunks> CHUNKS = new OperationDescriptor<>(
			"world.chunks", Void.class, LoadedChunks.class
	);
	/**
	 * Starts sending the changes of an area's blocks as {@link #CHANGES} under the watch's ID.
	 */
	public static final OperationDescriptor<AreaWatch, Void> WATCH = new OperationDescriptor<>(
			"world.watch", AreaWatch.class, Void.class
	);
	/**
	 * Stops sending the changes of the watch with this ID.
	 */
	public static final OperationDescriptor<Integer, Void> UNWATCH = new OperationDescriptor<>(
			"world.unwatch", Integer.class, Void.class
	);
	/**
	 * Blocks of an open watch's area took states the client did not know them in.
	 */
	public static final EventDescriptor<WatchedChanges> CHANGES = new EventDescriptor<>(
			"world.changes", WatchedChanges.class
	);
}
