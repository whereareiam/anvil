package me.whereareiam.anvil.capability.movement.java.physics.simulation;

import me.whereareiam.anvil.capability.movement.java.physics.geometry.Coordinates;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import me.whereareiam.anvil.capability.movement.java.physics.ClientBehaviour;
import me.whereareiam.anvil.capability.movement.java.physics.MovementRules;
import me.whereareiam.anvil.capability.movement.java.physics.geometry.Box;
import me.whereareiam.anvil.capability.movement.java.physics.geometry.Vector;
import me.whereareiam.anvil.capability.movement.java.physics.server.PlayerState;
import me.whereareiam.anvil.capability.movement.java.physics.world.EntityContext;
import me.whereareiam.anvil.capability.movement.model.MovementInput;
import me.whereareiam.anvil.protocol.java.api.model.world.chunk.ChunkPosition;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundPlayerAbilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The state of the vanilla client's own player that its use cases share, as {@code Entity}, {@code LivingEntity} and
 * {@code LocalPlayer} keep it: position, bounding box and dimensions, velocity, view, ground and collision flags, fall
 * distance, the block it stands on, the keys it holds, the input they became and whether it sprints. It also carries
 * the client's rules and what the server set of the player, which every use case reads, and the flight changes the
 * client told the server during the current tick.
 *
 * <p>Not thread-safe; its owner serializes ticks and placements.</p>
 */
@Getter
@Setter
public final class PlayerBody {
	@Setter(AccessLevel.NONE)
	private final @NotNull MovementRules rules;
	/**
	 * What the server set of the player.
	 */
	@Setter(AccessLevel.NONE)
	private final @NotNull PlayerState server;
	/**
	 * The position of the player's feet; {@link #setPosition} rebuilds the box.
	 */
	@Setter(AccessLevel.NONE)
	private @NotNull Vector position = Vector.ZERO;
	/**
	 * The position at the start of the tick ({@code xo}, {@code yo}, {@code zo}).
	 */
	private @NotNull Vector oldPosition = Vector.ZERO;
	@Setter(AccessLevel.NONE)
	private @NotNull Box box;
	/**
	 * The velocity in blocks per tick ({@code deltaMovement}).
	 */
	private @NotNull Vector deltaMovement = Vector.ZERO;
	private float yaw;
	private float pitch;
	@Setter(AccessLevel.NONE)
	private float width = 0.6F;
	@Setter(AccessLevel.NONE)
	private float height = 1.8F;
	@Setter(AccessLevel.NONE)
	private float eyeHeight = 1.62F;
	private boolean onGround;
	private boolean horizontalCollision;
	private boolean minorHorizontalCollision;
	/**
	 * How far the player has fallen since it last stood on the ground; before 1.21.5 the client keeps it as a float.
	 */
	private double fallDistance;
	/**
	 * The factors of the next movement, set by a block that slows the player inside it ({@code stuckSpeedMultiplier}).
	 */
	private @NotNull Vector stuckSpeedMultiplier = Vector.ZERO;
	/**
	 * The block the player stands on ({@code mainSupportingBlockPos}), from 1.20.
	 */
	private int @Nullable [] supportingBlock;
	private boolean onGroundNoBlocks;
	/**
	 * The keys the client holds this tick ({@code input.keyPresses}).
	 */
	private @NotNull MovementInput keys = MovementInput.NONE;
	@Setter(AccessLevel.NONE)
	private boolean sprinting;
	/**
	 * Whether the client crouches for its input ({@code LocalPlayer.crouching}), which may differ from its pose.
	 */
	private boolean crouching;
	private boolean jumping;
	/**
	 * The strafing input ({@code xxa}).
	 */
	private float xxa;
	/**
	 * The forward input ({@code zza}).
	 */
	private float zza;
	private boolean firstTick = true;
	/**
	 * The abilities the client told the server during the current tick, oldest first.
	 */
	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	private final @NotNull List<ServerboundPlayerAbilities> abilityUpdates = new ArrayList<>();

	/**
	 * Creates the body of a player at the world's origin.
	 *
	 * @param rules the client's rules
	 * @param server what the server set of the player
	 */
	public PlayerBody(@NotNull MovementRules rules, @NotNull PlayerState server) {
		this.rules = rules;
		this.server = server;
		this.box = Box.at(position, width, height);
	}

	/**
	 * Places the player's feet and rebuilds its box there ({@code Entity.setPos}).
	 *
	 * @param position the new position
	 */
	public void setPosition(@NotNull Vector position) {
		this.position = position;
		this.box = Box.at(position, width, height);
	}

	/**
	 * Moves the player by moving its box and reading the position back from it, as the clients before 1.17 move
	 * ({@code setLocationFromBoundingbox}).
	 *
	 * @param movement the movement
	 */
	public void moveBox(@NotNull Vector movement) {
		box = box.move(movement);
		position = new Vector((box.minX + box.maxX) / 2.0, box.minY, (box.minZ + box.maxZ) / 2.0);
	}

	/**
	 * Changes the player's dimensions and rebuilds its box ({@code Entity.refreshDimensions}).
	 *
	 * @param width the new width
	 * @param height the new height
	 * @param eyeHeight the new eye height
	 */
	public void dimensions(float width, float height, float eyeHeight) {
		this.width = width;
		this.height = height;
		this.eyeHeight = eyeHeight;
		box = Box.at(position, width, height);
	}

	/**
	 * Starts or stops sprinting and its speed modifier ({@code LivingEntity.setSprinting}).
	 *
	 * @param sprinting whether the player sprints
	 */
	public void setSprinting(boolean sprinting) {
		this.sprinting = sprinting;
		server.getAttributes().sprinting(sprinting);
	}

	/**
	 * Tells the server whether the player flies, as {@code LocalPlayer.onUpdateAbilities} does each time the client
	 * starts or stops flying on its own; the packet leaves with the packets of the tick.
	 */
	public void updateAbilities() {
		abilityUpdates.add(new ServerboundPlayerAbilities(server.isFlying()));
	}

	/**
	 * Returns the abilities the client told the server during the current tick, in the order it told them.
	 *
	 * @return the abilities packets, unmodifiable
	 */
	public @NotNull List<ServerboundPlayerAbilities> abilityUpdates() {
		return Collections.unmodifiableList(abilityUpdates);
	}

	/**
	 * Forgets the abilities told during the previous tick, as a new tick starts.
	 */
	public void startTick() {
		abilityUpdates.clear();
	}

	/**
	 * Tells whether the client behaves so.
	 *
	 * @param behaviour the behaviour
	 * @return whether the client's version has it
	 */
	public boolean has(@NotNull ClientBehaviour behaviour) {
		return rules.has(behaviour);
	}

	/**
	 * Returns what the blocks' collision shapes may depend on of the player now.
	 *
	 * @return the player's collision context
	 */
	public @NotNull EntityContext context() {
		return new EntityContext(position.y, keys.isSneak(), fallDistance);
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
	 * Returns the chunk column the player is in.
	 *
	 * @return the column's position
	 */
	public @NotNull ChunkPosition chunk() {
		return ChunkPosition.containing(Coordinates.floor(position.x), Coordinates.floor(position.z));
	}

	/**
	 * Returns the block coordinates of the player's feet ({@code Entity.blockPosition}).
	 *
	 * @return the east-west, height and north-south block coordinates
	 */
	public int @NotNull [] blockPosition() {
		return new int[] {Coordinates.floor(position.x), Coordinates.floor(position.y), Coordinates.floor(position.z)};
	}
}
