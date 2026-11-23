package me.whereareiam.anvil.edition.java.physics.entity;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import me.whereareiam.anvil.edition.java.physics.EntityBehaviour;
import me.whereareiam.anvil.edition.java.physics.EntityRules;
import me.whereareiam.anvil.edition.java.physics.geometry.Box;
import me.whereareiam.anvil.edition.java.physics.geometry.Coordinates;
import me.whereareiam.anvil.edition.java.physics.geometry.Vector;
import me.whereareiam.anvil.edition.java.physics.world.BlockReader;
import me.whereareiam.anvil.edition.java.physics.world.EntityContext;
import me.whereareiam.anvil.edition.java.protocol.model.world.chunk.ChunkPosition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The state of an entity that its physics shares, as vanilla's {@code Entity} keeps it: its level, position, bounding
 * box and dimensions, velocity, view, ground and collision flags, fall distance and the block it stands on. Its
 * public non-final methods are the points where vanilla's subclasses change how an entity moves; the defaults are
 * {@code Entity}'s, and a subclass overrides them as {@code Player} and {@code LocalPlayer} do.
 *
 * <p>Not thread-safe; its owner serializes ticks and placements.</p>
 */
@Getter
@Setter
public class EntityBody {
	private final @NotNull EntityRules rules;
	/**
	 * The blocks the entity moves among ({@code Entity.level}).
	 */
	private final @NotNull BlockReader level;
	@Setter(AccessLevel.NONE)
	private @NotNull Vector position = Vector.ZERO;
	/**
	 * The position at the start of the tick ({@code xo}, {@code yo}, {@code zo}).
	 */
	private @NotNull Vector oldPosition = Vector.ZERO;
	@Setter(AccessLevel.NONE)
	private @NotNull Box box;
	@Setter(AccessLevel.NONE)
	private @NotNull EntityDimensions dimensions;
	/**
	 * The velocity in blocks per tick ({@code deltaMovement}).
	 */
	private @NotNull Vector deltaMovement = Vector.ZERO;
	private float yaw;
	private float pitch;
	private boolean onGround;
	private boolean horizontalCollision;
	private boolean minorHorizontalCollision;
	/**
	 * How far the entity has fallen since it last stood on the ground; before 1.21.5 the client keeps it as a float.
	 */
	private double fallDistance;
	/**
	 * The factors of the next movement, set by a block that slows the entity inside it ({@code stuckSpeedMultiplier}).
	 */
	private @NotNull Vector stuckSpeedMultiplier = Vector.ZERO;
	/**
	 * The block the entity stands on ({@code mainSupportingBlockPos}), from 1.20.
	 */
	private int @Nullable [] supportingBlock;
	private boolean onGroundNoBlocks;
	private boolean firstTick = true;
	/**
	 * Whether the entity moves through blocks ({@code noPhysics}).
	 */
	private boolean noPhysics;
	@Getter(AccessLevel.NONE)
	private boolean shiftKeyDown;

	/**
	 * Creates the body of an entity at the world's origin.
	 *
	 * @param rules the behaviours of the client's version
	 * @param level the blocks the entity moves among
	 * @param dimensions the entity's size
	 */
	public EntityBody(@NotNull EntityRules rules, @NotNull BlockReader level, @NotNull EntityDimensions dimensions) {
		this.rules = rules;
		this.level = level;
		this.dimensions = dimensions;
		this.box = Box.at(position, dimensions.width(), dimensions.height());
	}

	/**
	 * Places the entity's feet and rebuilds its box there ({@code Entity.setPos}).
	 *
	 * @param position the new position
	 */
	public void position(@NotNull Vector position) {
		this.position = position;
		this.box = Box.at(position, dimensions.width(), dimensions.height());
	}

	/**
	 * Moves the box and reads the position back from it, as before 1.17 ({@code setLocationFromBoundingbox}).
	 *
	 * @param movement the movement
	 */
	public void moveBox(@NotNull Vector movement) {
		box = box.move(movement);
		position = new Vector((box.minX + box.maxX) / 2.0, box.minY, (box.minZ + box.maxZ) / 2.0);
	}

	/**
	 * Changes the entity's dimensions and rebuilds its box ({@code Entity.refreshDimensions}).
	 *
	 * @param dimensions the new dimensions
	 */
	public void dimensions(@NotNull EntityDimensions dimensions) {
		this.dimensions = dimensions;
		box = Box.at(position, dimensions.width(), dimensions.height());
	}

	/**
	 * Tells whether entities of the client's version behave so.
	 *
	 * @param behaviour the behaviour
	 * @return whether the client's version has it
	 */
	public boolean has(@NotNull EntityBehaviour behaviour) {
		return rules.has(behaviour);
	}

	/**
	 * Returns what the blocks' collision shapes may depend on of the entity now ({@code CollisionContext.of}).
	 *
	 * @return the entity's collision context
	 */
	public @NotNull EntityContext context() {
		return new EntityContext(position.y, shiftKeyDown(), fallDistance);
	}

	/**
	 * Resets the fall distance, as landing in water, climbing or being stuck in a block does.
	 */
	public void resetFallDistance() {
		fallDistance = 0.0;
	}

	/**
	 * Adds to the velocity.
	 *
	 * @param dx the east-west addend
	 * @param dy the vertical addend
	 * @param dz the north-south addend
	 */
	public void addDeltaMovement(double dx, double dy, double dz) {
		deltaMovement = deltaMovement.add(dx, dy, dz);
	}

	/**
	 * Returns the chunk column the entity is in.
	 *
	 * @return the column's position
	 */
	public @NotNull ChunkPosition chunk() {
		return ChunkPosition.containing(Coordinates.floor(position.x), Coordinates.floor(position.z));
	}

	/**
	 * Returns the block coordinates of the entity's feet ({@code Entity.blockPosition}).
	 *
	 * @return the east-west, height and north-south block coordinates
	 */
	public int @NotNull [] blockPosition() {
		return new int[] {Coordinates.floor(position.x), Coordinates.floor(position.y), Coordinates.floor(position.z)};
	}

	/**
	 * Tells whether the entity's shift key is down ({@code Entity.isShiftKeyDown}), which makes it descend through
	 * scaffolding and step carefully on slime; {@code LocalPlayer} reads its held keys instead.
	 *
	 * @return whether it sneaks
	 */
	public boolean shiftKeyDown() {
		return shiftKeyDown;
	}

	/**
	 * Tells whether the entity spectates ({@code Entity.isSpectator}), as a player in spectator mode does.
	 *
	 * @return {@code false} by default
	 */
	public boolean spectator() {
		return false;
	}

	/**
	 * Tells whether flowing fluids push the entity ({@code Entity.isPushedByFluid}); a flying player is not pushed.
	 *
	 * @return {@code true} by default
	 */
	public boolean pushedByFluid() {
		return true;
	}

	/**
	 * Tells whether the entity lands on a bouncy block without bouncing ({@code Entity.isSuppressingBounce}), as a
	 * sneaking player does.
	 *
	 * @return {@code false} by default
	 */
	public boolean suppressingBounce() {
		return false;
	}

	/**
	 * How high the entity steps up onto what stops it ({@code Entity.maxUpStep}).
	 *
	 * @return {@code 0} for an entity that does not step
	 */
	public float maxUpStep() {
		return 0.0F;
	}

	/**
	 * The factor of the entity's velocity after a movement, from the factor the blocks give
	 * ({@code Entity.getBlockSpeedFactor}), which {@code LivingEntity} eases by its movement efficiency.
	 *
	 * @param blockFactor the speed factor of the blocks in and below the entity
	 * @return the factor to apply
	 */
	public float blockSpeedFactor(float blockFactor) {
		return blockFactor;
	}

	/**
	 * Shortens a movement that would take the entity off an edge ({@code Entity.maybeBackOffFromEdge}).
	 *
	 * @param movement the movement
	 * @return the movement unchanged by default
	 */
	public @NotNull Vector maybeBackOffFromEdge(@NotNull Vector movement) {
		return movement;
	}

	/**
	 * Tells whether a movement that hit a wall only grazes it ({@code Entity.isHorizontalCollisionMinor}).
	 *
	 * @param movement the movement after collisions
	 * @return {@code false} by default
	 */
	public boolean isHorizontalCollisionMinor(@NotNull Vector movement) {
		return false;
	}
}
