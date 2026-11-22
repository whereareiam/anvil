package me.whereareiam.anvil.edition.java.physics.world;

import me.whereareiam.anvil.edition.java.data.block.BlockRegistry;
import me.whereareiam.anvil.edition.java.data.block.model.BlockState;
import me.whereareiam.anvil.edition.java.data.block.model.collision.BlockOffset;
import me.whereareiam.anvil.edition.java.data.block.model.collision.CollisionShape;
import me.whereareiam.anvil.edition.java.physics.geometry.Vector;
import me.whereareiam.anvil.edition.java.physics.world.shape.BlockShape;
import me.whereareiam.anvil.edition.java.protocol.JavaWorld;
import me.whereareiam.anvil.edition.java.protocol.WorldView;
import me.whereareiam.anvil.edition.java.protocol.model.world.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;

/**
 * The blocks the player's client knows, as vanilla's client level answers movement: the state of every block of the
 * loaded chunk columns by the release's block data, and void air wherever the client has no block, outside the level's
 * heights or in a column it does not have. A client level claims every chunk column ({@code ClientLevel.hasChunk}), so
 * an entity in a column the client lacks moves as through void air. A tick reads the world at one moment through {@link #read}, as the vanilla
 * client runs its packets between ticks.
 *
 * <p>The reader is not thread-safe; its owner serializes ticks.</p>
 */
public final class BlockReader {
	private final @NotNull JavaWorld world;
	private final @NotNull BlockRegistry registry;
	private final @NotNull BlockState voidAir;
	private final @NotNull Map<CollisionShape, BlockShape> shapes = new HashMap<>();
	private final @NotNull Map<Moved, BlockShape> moved = new HashMap<>();
	private @Nullable WorldView view;

	/**
	 * Creates the reader of a player's world.
	 *
	 * @param world the world the player's client knows
	 * @param registry the block data of the player's protocol
	 */
	public BlockReader(@NotNull JavaWorld world, @NotNull BlockRegistry registry) {
		this.world = world;
		this.registry = registry;
		this.voidAir = registry.state(registry.block("minecraft:void_air").orElseThrow().getDefaultStateId());
	}

	/**
	 * Runs a reader that sees the world at one moment: no packet changes the world while it runs.
	 *
	 * @param reader what reads the blocks through this reader
	 */
	public void read(@NotNull Runnable reader) {
		world.read(current -> {
			view = current;
			try {
				reader.run();
			} finally {
				view = null;
			}
			return null;
		});
	}

	/**
	 * Returns the state of a block, void air where the client has none.
	 *
	 * @param x the block's east-west coordinate
	 * @param y the block's height
	 * @param z the block's north-south coordinate
	 * @return the state
	 * @throws IllegalArgumentException when the block's state is not one of the protocol's, such as a modded block's,
	 * whose collision the client's physics cannot know
	 */
	public @NotNull BlockState state(int x, int y, int z) {
		OptionalInt state = view().blockState(x, y, z);
		if (state.isEmpty()) return voidAir;

		return registry.state(state.getAsInt());
	}

	/**
	 * Returns the voxel shape of a collision shape the block data records.
	 *
	 * @param collision the collision shape
	 * @return the voxel shape, built once
	 */
	public @NotNull BlockShape shape(@NotNull CollisionShape collision) {
		return shapes.computeIfAbsent(collision, BlockShape::of);
	}

	/**
	 * Returns the collision shape a block gives an entity ({@code BlockState.getCollisionShape}): the shape the block
	 * data records for the entity's case at the block ({@link EntityContext#collisionCase}), which differs from the
	 * standing shape only for a block whose shape depends on the entity, such as scaffolding and powder snow, moved by the
	 * block's offset at its position for an offset block, such as bamboo and pointed dripstone ({@link PositionOffset}).
	 *
	 * @param state the block's state
	 * @param block the block's east-west, height and north-south coordinates
	 * @param context the entity
	 * @return the shape in block-local coordinates
	 */
	public @NotNull BlockShape shape(@NotNull BlockState state, int @NotNull [] block, @NotNull EntityContext context) {
		CollisionShape collision = state.getCollisionCases().isEmpty() ? state.getCollision()
				: state.collision(context.collisionCase(block[1]));
		return placed(state, collision, block[0], block[2]);
	}

	/**
	 * Tells whether a block's collision shape may reach beyond the block ({@code BlockState.hasLargeCollisionShape}),
	 * as vanilla caches it for the state: from the shape it gives no entity at the origin, which is the standing shape
	 * the block data records, moved by the block's offset there.
	 *
	 * @param state the block's state
	 * @return whether the shape reaches out of its block
	 */
	public boolean hasLargeCollisionShape(@NotNull BlockState state) {
		return placed(state, state.getCollision(), 0, 0).isLarge();
	}

	private @NotNull BlockShape placed(@NotNull BlockState state, @NotNull CollisionShape collision, int x, int z) {
		BlockOffset offset = state.getBlock().getOffset();
		if (offset == null || collision.isEmpty()) return shape(collision);

		Vector by = PositionOffset.at(offset, x, z);
		return moved.computeIfAbsent(new Moved(collision, by.x, by.y, by.z), key -> shape(collision).moved(by));
	}



	/**
	 * Returns the lowest height of the level the client is in, as {@code Level.getMinY} does.
	 *
	 * @return the lowest height, or {@code 0} before the client knows a level
	 */
	public int minY() {
		return view().level().map(Level::minY).orElse(0);
	}

	/**
	 * Tells whether lava pushes entities fast in the client's level, as its dimension type says
	 * ({@code DimensionType.ultraWarm} before 1.21.11, the {@code minecraft:gameplay/fast_lava} environment attribute
	 * from 1.21.11).
	 *
	 * @return whether lava pushes fast, or {@code false} before the client knows a level
	 */
	public boolean fastLava() {
		return view().level().map(level -> level.getType().isFastLava()).orElse(false);
	}

	private @NotNull WorldView view() {
		WorldView current = view;
		if (current == null) throw new IllegalStateException("The client's blocks are read outside a tick");

		return current;
	}

	/**
	 * A collision shape moved by an offset, which the reader builds once.
	 */
	private record Moved(@NotNull CollisionShape shape, double x, double y, double z) {
	}
}
