package me.whereareiam.anvil.capability.world.channel;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.capability.world.model.BlockCoordinates;
import me.whereareiam.anvil.capability.world.model.BlockReading;
import me.whereareiam.anvil.capability.world.model.KnownDimension;
import me.whereareiam.anvil.capability.world.model.LoadedChunks;
import me.whereareiam.anvil.capability.world.model.ReceivedBlocks;
import me.whereareiam.anvil.protocol.api.model.channel.EventDescriptor;
import me.whereareiam.anvil.protocol.api.model.channel.OperationDescriptor;

/**
 * The channel contract between the host world capability and its worker extensions: the capability ID both register
 * under, the reads the host requests and the event the worker emits. The worker speaks in global block state IDs; the
 * host names them through the block data of the player's release.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class WorldChannel {
	/**
	 * Capability ID of the host provider and of every edition's worker extension.
	 */
	public static final String ID = "me.whereareiam.anvil.world";
	/**
	 * Reads the level the player is in.
	 */
	public static final OperationDescriptor<Void, KnownDimension> DIMENSION = new OperationDescriptor<>(
			"world.dimension", Void.class, KnownDimension.class
	);
	/**
	 * Reads the state of one block.
	 */
	public static final OperationDescriptor<BlockCoordinates, BlockReading> BLOCK = new OperationDescriptor<>(
			"world.block", BlockCoordinates.class, BlockReading.class
	);
	/**
	 * Reads the chunk columns the client has.
	 */
	public static final OperationDescriptor<Void, LoadedChunks> CHUNKS = new OperationDescriptor<>(
			"world.chunks", Void.class, LoadedChunks.class
	);
	/**
	 * The blocks one block update set, numbered by the connection that received it.
	 */
	public static final EventDescriptor<ReceivedBlocks> CHANGED = new EventDescriptor<>(
			"world.changed", ReceivedBlocks.class
	);
}
