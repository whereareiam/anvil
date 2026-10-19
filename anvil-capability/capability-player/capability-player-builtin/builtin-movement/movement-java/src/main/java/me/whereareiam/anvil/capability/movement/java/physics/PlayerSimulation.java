package me.whereareiam.anvil.capability.movement.java.physics;

import me.whereareiam.anvil.capability.movement.model.MovementInput;
import me.whereareiam.anvil.protocol.java.data.model.BlockState;
import me.whereareiam.anvil.protocol.java.data.model.StuckSpeed;
import me.whereareiam.anvil.protocol.java.data.type.BlockTrait;
import me.whereareiam.anvil.protocol.java.data.type.FluidType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One tick of the vanilla client's own player at a time, as {@code LocalPlayer.tick} runs it without a vehicle,
 * flying, gliding, swimming or item use: the fluid state of {@code Entity.baseTick}, the keys, crouching and
 * sprinting of {@code LocalPlayer.aiStep}, the jump and travel of {@code LivingEntity.aiStep}, the collisions and
 * block effects of {@code Entity.move} and the pose of {@code Player.updatePlayerPose}. Floats and doubles are
 * combined in vanilla's order, so that a tick gives the bits the client of the same version computes.
 *
 * <p>The simulation is not thread-safe; its owner serializes ticks and placements.</p>
 */
final class PlayerSimulation {
	private static final float WIDTH = 0.6F;
	private static final float STANDING_HEIGHT = 1.8F;
	private static final float CROUCHING_HEIGHT = 1.5F;
	private static final float STANDING_EYES = 1.62F;
	private static final float CROUCHING_EYES = 1.27F;
	private static final float STEP_HEIGHT = 0.6F;
	private static final double GRAVITY = 0.08;
	private static final float JUMP_POWER = 0.42F;
	private static final float SNEAKING_SPEED = 0.3F;
	private static final double BASE_SPEED = 0.1F;
	private static final double SPRINT_SPEED_MODIFIER = 0.3F;
	private static final float DEGREES_TO_RADIANS = (float) (Math.PI / 180.0);
	private static final int SPRINT_WINDOW = 7;

	private final @NotNull MovementRules rules;
	private final @NotNull Trigonometry trigonometry;
	private final @NotNull BlockView view;
	private final @NotNull Collisions collisions;
	private final @NotNull Fluids fluids;

	private double x;
	private double y;
	private double z;
	private double oldX;
	private double oldY;
	private double oldZ;
	private @NotNull Box box;
	private @NotNull Vector deltaMovement = Vector.ZERO;
	private float yaw;
	private float pitch;
	private boolean crouchingPose;

	private boolean onGround;
	private boolean horizontalCollision;
	private boolean minorHorizontalCollision;
	private double fallDistance;
	private @NotNull Vector stuckSpeedMultiplier = Vector.ZERO;
	private int @Nullable [] supportingBlock;
	private boolean onGroundNoBlocks;

	private boolean firstTick = true;
	private boolean touchingWater;
	private boolean eyeInWater;
	private boolean underWater;
	private double waterHeight;
	private double lavaHeight;
	private boolean inPowderSnow;
	private boolean wasInPowderSnow;

	private @NotNull MovementInput keys = MovementInput.NONE;
	private float leftImpulse;
	private float forwardImpulse;
	private boolean crouching;
	private boolean sprinting;
	private int sprintTriggerTime;
	private int noJumpDelay;
	private boolean jumping;
	private float xxa;
	private float zza;
	private float legacyFlyingSpeed = 0.02F;

	PlayerSimulation(@NotNull MovementRules rules, @NotNull BlockView view, double x, double y, double z, float yaw, float pitch) {
		this.rules = rules;
		this.trigonometry = new Trigonometry(rules.doubleTrigonometry());
		this.view = view;
		this.collisions = new Collisions(view);
		this.fluids = new Fluids(view);
		this.yaw = yaw;
		this.pitch = pitch;
		this.box = Box.at(x, y, z, WIDTH, STANDING_HEIGHT);
		setPos(x, y, z);
		this.oldX = x;
		this.oldY = y;
		this.oldZ = z;
	}

	double x() {
		return x;
	}

	double y() {
		return y;
	}

	double z() {
		return z;
	}

	float yaw() {
		return yaw;
	}

	float pitch() {
		return pitch;
	}

	boolean onGround() {
		return onGround;
	}

	boolean horizontalCollision() {
		return horizontalCollision;
	}

	boolean sprinting() {
		return sprinting;
	}

	boolean sneaking() {
		return keys.isSneak();
	}

	@NotNull MovementInput keys() {
		return keys;
	}

	@NotNull Vector deltaMovement() {
		return deltaMovement;
	}

	@NotNull Box box() {
		return box;
	}

	boolean crouchingPose() {
		return crouchingPose;
	}

	void look(float yaw, float pitch) {
		this.yaw = yaw;
		this.pitch = pitch;
	}

	/**
	 * Places the player as a server teleport does ({@code setPos}, {@code setDeltaMovement}, {@code setYRot},
	 * {@code setXRot}); the ground state stays.
	 */
	void place(double x, double y, double z, @NotNull Vector deltaMovement, float yaw, float pitch) {
		setPos(x, y, z);
		this.deltaMovement = deltaMovement;
		this.yaw = yaw;
		this.pitch = pitch;
	}

	/**
	 * Places the player where a test moved it, with no velocity, standing on the ground as told.
	 */
	void moveTo(double x, double y, double z, float yaw, float pitch, boolean onGround) {
		place(x, y, z, Vector.ZERO, yaw, pitch);
		this.onGround = onGround;
		fallDistance = 0.0;
	}

	/**
	 * Runs one tick with the keys held from this tick on.
	 */
	void tick(@NotNull MovementInput input) {
		oldX = x;
		oldY = y;
		oldZ = z;
		underWater = eyeInWater;
		baseTick();
		aiStep(input);

		double clampedX = Math.clamp(x, -2.9999999E7, 2.9999999E7);
		double clampedZ = Math.clamp(z, -2.9999999E7, 2.9999999E7);
		if (clampedX != x || clampedZ != z) setPos(clampedX, y, clampedZ);
		updatePose();
	}

	private void baseTick() {
		wasInPowderSnow = inPowderSnow;
		inPowderSnow = false;
		waterHeight = 0.0;
		lavaHeight = 0.0;
		updateWater();
		FluidContact lava = fluidContact(FluidType.LAVA, view.fastLava() ? 0.007 : 0.0023333333333333335);
		if (lava != null) lavaHeight = lava.depth();
		updateEyes();
		if (inLava()) fallDistance = rules.vectorInput() ? fallDistance * 0.5 : (float) fallDistance * 0.5F;
		firstTick = false;
	}

	/**
	 * Updates whether the player touches water and pushes it along the current
	 * ({@code updateInWaterStateAndDoWaterCurrentPushing}).
	 */
	private void updateWater() {
		FluidContact contact = fluidContact(FluidType.WATER, 0.014);
		if (contact != null) waterHeight = contact.depth();
		touchingWater = contact != null && contact.touching();
		if (touchingWater) fallDistance = 0.0;
	}

	/**
	 * Measures how deep the player is in a fluid and pushes it along the fluid's flow
	 * ({@code updateFluidHeightAndDoFluidPushing}); in a box touching an unloaded column nothing changes.
	 *
	 * @return whether the player touches the fluid and how deep it is in it, or {@code null} when the box touches an
	 * unloaded column
	 */
	private @Nullable FluidContact fluidContact(@NotNull FluidType type, double pushScale) {
		Box around = box.inflate(1.0);
		if (!view.hasChunksAt(Fluids.floor(around.minX), Fluids.floor(around.minZ), Fluids.ceil(around.maxX), Fluids.ceil(around.maxZ)))
			return null;

		Box inner = box.deflate(0.001);
		int minX = Fluids.floor(inner.minX);
		int maxX = Fluids.ceil(inner.maxX);
		int minY = Fluids.floor(inner.minY);
		int maxY = Fluids.ceil(inner.maxY);
		int minZ = Fluids.floor(inner.minZ);
		int maxZ = Fluids.ceil(inner.maxZ);
		boolean touching = false;
		double depth = 0.0;
		Vector push = Vector.ZERO;
		int pushes = 0;
		for (int bx = minX; bx < maxX; bx++)
			for (int by = minY; by < maxY; by++)
				for (int bz = minZ; bz < maxZ; bz++) {
					if (fluids.type(bx, by, bz) != type) continue;

					double surface = by + fluids.height(bx, by, bz);
					if (surface < inner.minY) continue;

					touching = true;
					depth = Math.max(surface - inner.minY, depth);
					Vector flow = fluids.flow(bx, by, bz);
					if (depth < 0.4) flow = flow.scale(depth);
					push = push.add(flow);
					pushes++;
				}

		if (push.length() > 0.0) {
			if (pushes > 0) push = push.scale(1.0 / pushes);
			push = push.scale(pushScale);
			if (Math.abs(deltaMovement.x) < 0.003 && Math.abs(deltaMovement.z) < 0.003 && push.length() < 0.0045000000000000005)
				push = push.normalize().scale(0.0045000000000000005);
			deltaMovement = deltaMovement.add(push);
		}

		return new FluidContact(touching, depth);
	}

	private void updateEyes() {
		eyeInWater = false;
		double eyes = y + eyeHeight() - 0.11111111F;
		int bx = Fluids.floor(x);
		int by = Fluids.floor(eyes);
		int bz = Fluids.floor(z);
		if (fluids.type(bx, by, bz) != FluidType.WATER) return;

		eyeInWater = by + fluids.height(bx, by, bz) > eyes;
	}

	private boolean inLava() {
		return !firstTick && lavaHeight > 0.0;
	}

	private void aiStep(@NotNull MovementInput input) {
		if (sprintTriggerTime > 0) sprintTriggerTime--;

		if (rules.vectorInput()) localAiStep(input);
		else legacyLocalAiStep(input);

		livingAiStep();
		if (!rules.liveFlyingSpeed()) legacyFlyingSpeed = sprinting ? 0.02F + 0.006F : 0.02F;
	}

	/**
	 * {@code LocalPlayer.aiStep} from 1.21.5: the keys become a normalized direction and sprinting follows
	 * {@code canStartSprinting} and {@code shouldStopRunSprinting}.
	 */
	private void localAiStep(@NotNull MovementInput input) {
		boolean wasShift = keys.isSneak();
		boolean hadForwardImpulse = forwardImpulse > 1.0E-5F;
		crouching = canFit(CROUCHING_HEIGHT) && (wasShift || !canFit(STANDING_HEIGHT));
		keys = input;
		float left = impulse(input.isLeft(), input.isRight());
		float forward = impulse(input.isForward(), input.isBackward());
		float length = (float) Math.sqrt(left * left + forward * forward);
		leftImpulse = length < 1.0E-4F ? 0.0F : left / length;
		forwardImpulse = length < 1.0E-4F ? 0.0F : forward / length;
		if (wasShift || input.isBackward()) sprintTriggerTime = 0;

		if (canStartSprinting()) {
			if (!hadForwardImpulse) {
				if (sprintTriggerTime > 0) sprinting = true;
				else sprintTriggerTime = SPRINT_WINDOW;
			}
			if (input.isSprint()) sprinting = true;
		}

		if (sprinting && shouldStopSprinting()) sprinting = false;
		if (touchingWater && input.isSneak()) goDownInWater();
	}

	private boolean canStartSprinting() {
		return !sprinting
				&& forwardImpulse > 1.0E-5F
				&& !inShallowWater()
				&& (!crouching || underWater);
	}

	private boolean shouldStopSprinting() {
		return inShallowWater()
				|| !(forwardImpulse > 1.0E-5F)
				|| horizontalCollision && !minorHorizontalCollision;
	}

	private boolean inShallowWater() {
		return touchingWater && !underWater;
	}

	/**
	 * {@code LocalPlayer.aiStep} before 1.21.5: the keys become impulses scaled for sneaking, and sprinting starts
	 * on a double tap or the sprint key with enough forward impulse.
	 */
	private void legacyLocalAiStep(@NotNull MovementInput input) {
		boolean wasShift = keys.isSneak();
		boolean enoughImpulse = enoughImpulseToStartSprinting();
		crouching = canFit(CROUCHING_HEIGHT) && (wasShift || !canFit(STANDING_HEIGHT));
		keys = input;
		forwardImpulse = impulse(input.isForward(), input.isBackward());
		leftImpulse = impulse(input.isLeft(), input.isRight());
		if (crouching) {
			leftImpulse *= SNEAKING_SPEED;
			forwardImpulse *= SNEAKING_SPEED;
		}
		if (wasShift) sprintTriggerTime = 0;

		boolean canStart = !sprinting && enoughImpulseToStartSprinting();
		if ((onGround || underWater) && !wasShift && !enoughImpulse && canStart) {
			if (sprintTriggerTime <= 0 && !input.isSprint()) sprintTriggerTime = SPRINT_WINDOW;
			else sprinting = true;
		}
		if ((!touchingWater || underWater) && canStart && input.isSprint()) sprinting = true;

		if (sprinting) {
			boolean noImpulse = !(forwardImpulse > 1.0E-5F);
			boolean blocked = horizontalCollision && !(rules.minorHorizontalCollision() && minorHorizontalCollision);
			if (noImpulse || blocked || touchingWater && !underWater) sprinting = false;
		}
		if (touchingWater && input.isSneak()) goDownInWater();
	}

	private boolean enoughImpulseToStartSprinting() {
		return underWater ? forwardImpulse > 1.0E-5F : (double) forwardImpulse >= 0.8;
	}

	private void goDownInWater() {
		deltaMovement = deltaMovement.add(0.0, -0.04F, 0.0);
	}

	private static float impulse(boolean positive, boolean negative) {
		if (positive == negative) return 0.0F;

		return positive ? 1.0F : -1.0F;
	}

	/**
	 * {@code LivingEntity.aiStep}: the jump delay, the dead zone of small speeds, the input, the jump and the travel.
	 */
	private void livingAiStep() {
		if (noJumpDelay > 0) noJumpDelay--;

		double dx = deltaMovement.x;
		double dy = deltaMovement.y;
		double dz = deltaMovement.z;
		if (rules.vectorInput()) {
			if (deltaMovement.horizontalDistanceSqr() < 9.0E-6) {
				dx = 0.0;
				dz = 0.0;
			}
		} else {
			if (Math.abs(dx) < 0.003) dx = 0.0;
			if (Math.abs(dz) < 0.003) dz = 0.0;
		}
		if (Math.abs(dy) < 0.003) dy = 0.0;
		deltaMovement = new Vector(dx, dy, dz);

		applyInput();
		jump();
		if (!rules.vectorInput()) {
			xxa *= 0.98F;
			zza *= 0.98F;
		}

		travel(new Vector(xxa, 0.0, zza));
		if (rules.pathBlockEffects()) applyEffectsFromBlocks();
	}

	private void applyInput() {
		jumping = keys.isJump();
		if (!rules.vectorInput()) {
			xxa = leftImpulse;
			zza = forwardImpulse;
			return;
		}

		float inputX = leftImpulse;
		float inputY = forwardImpulse;
		if (inputX * inputX + inputY * inputY == 0.0F) {
			xxa = inputX;
			zza = inputY;
			return;
		}

		inputX *= 0.98F;
		inputY *= 0.98F;
		if (crouching) {
			inputX *= SNEAKING_SPEED;
			inputY *= SNEAKING_SPEED;
		}

		float length = (float) Math.sqrt(inputX * inputX + inputY * inputY);
		if (length <= 0.0F) {
			xxa = inputX;
			zza = inputY;
			return;
		}

		float unitX = inputX * (1.0F / length);
		float unitY = inputY * (1.0F / length);
		float absX = Math.abs(unitX);
		float absY = Math.abs(unitY);
		float ratio = absY > absX ? absX / absY : absY / absX;
		float toSquare = (float) Math.sqrt(1.0F + ratio * ratio);
		float scale = Math.min(length * toSquare, 1.0F);
		xxa = unitX * scale;
		zza = unitY * scale;
	}

	private void jump() {
		if (!jumping) {
			noJumpDelay = 0;
			return;
		}

		double depth = inLava() ? lavaHeight : waterHeight;
		boolean inWater = touchingWater && depth > 0.0;
		double threshold = fluidJumpThreshold();
		if (inWater && !(onGround && !(depth > threshold))) {
			deltaMovement = deltaMovement.add(0.0, 0.04F, 0.0);
			return;
		}
		if (inLava() && !(onGround && !(depth > threshold))) {
			deltaMovement = deltaMovement.add(0.0, 0.04F, 0.0);
			return;
		}
		if ((onGround || inWater && depth <= threshold) && noJumpDelay == 0) {
			jumpFromGround();
			noJumpDelay = 10;
		}
	}

	private void jumpFromGround() {
		float power = JUMP_POWER * blockJumpFactor();
		if (power <= 1.0E-5F) return;

		double rise = rules.pathBlockEffects() ? Math.max(power, deltaMovement.y) : power;
		deltaMovement = new Vector(deltaMovement.x, rise, deltaMovement.z);
		if (!sprinting) return;

		float radians = yaw * DEGREES_TO_RADIANS;
		if (rules.doubleSprintJumpBoost())
			deltaMovement = deltaMovement.add(-trigonometry.sin(radians) * 0.2, 0.0, trigonometry.cos(radians) * 0.2);
		else
			deltaMovement = deltaMovement.add(-trigonometry.sin(radians) * 0.2F, 0.0, trigonometry.cos(radians) * 0.2F);
	}

	private double fluidJumpThreshold() {
		return eyeHeight() < 0.4 ? 0.0 : 0.4;
	}

	private void travel(@NotNull Vector input) {
		if (touchingWater || inLava()) travelInFluid(input);
		else travelInAir(input);
	}

	private void travelInAir(@NotNull Vector input) {
		int[] below = blockPosBelowThatAffectsMyMovement();
		float friction = onGround ? view.state(below[0], below[1], below[2]).getBlock().getFriction() : 1.0F;
		float drag = friction * 0.91F;
		Vector movement = handleRelativeFrictionAndCalculateMovement(input, friction);
		double vertical = movement.y;
		if (view.hasChunkAt(below[0], below[2])) vertical -= GRAVITY;
		else if (y > view.minY()) vertical = -0.1;
		else vertical = 0.0;

		deltaMovement = new Vector(movement.x * drag, vertical * 0.98F, movement.z * drag);
	}

	private @NotNull Vector handleRelativeFrictionAndCalculateMovement(@NotNull Vector input, float friction) {
		moveRelative(frictionInfluencedSpeed(friction), input);
		deltaMovement = handleOnClimbable(deltaMovement);
		move(deltaMovement);
		Vector movement = deltaMovement;
		if ((horizontalCollision || jumping) && onClimbable()) movement = new Vector(movement.x, 0.2, movement.z);

		return movement;
	}

	/**
	 * {@code LivingEntity.getFrictionInfluencedSpeed}. On the ground it scales the player's speed, which
	 * {@code Player.getSpeed} reads from the movement speed attribute at that moment, so the sprint that started in
	 * the same tick already counts; the speed {@code Player.aiStep} stores after the movement is never read for a
	 * player.
	 */
	private float frictionInfluencedSpeed(float friction) {
		if (onGround) return (float) movementSpeed() * (0.21600002F / (friction * friction * friction));
		if (!rules.liveFlyingSpeed()) return legacyFlyingSpeed;

		return sprinting ? 0.025999999F : 0.02F;
	}

	private void travelInFluid(@NotNull Vector input) {
		boolean falling = deltaMovement.y <= 0.0;
		double startY = y;
		if (touchingWater) {
			float slowDown = sprinting ? 0.9F : 0.8F;
			moveRelative(0.02F, input);
			move(deltaMovement);
			Vector movement = deltaMovement;
			if (horizontalCollision && onClimbable()) movement = new Vector(movement.x, 0.2, movement.z);
			movement = movement.multiply(slowDown, 0.8F, slowDown);
			deltaMovement = fluidFallingAdjustedMovement(falling, movement);
			jumpOutOfFluid(startY);
			return;
		}

		moveRelative(0.02F, input);
		move(deltaMovement);
		if (lavaHeight <= fluidJumpThreshold()) {
			deltaMovement = deltaMovement.multiply(0.5, 0.8F, 0.5);
			deltaMovement = fluidFallingAdjustedMovement(falling, deltaMovement);
		} else {
			deltaMovement = deltaMovement.scale(0.5);
		}
		deltaMovement = deltaMovement.add(0.0, -GRAVITY / 4.0, 0.0);
		jumpOutOfFluid(startY);
	}

	private @NotNull Vector fluidFallingAdjustedMovement(boolean falling, @NotNull Vector movement) {
		if (sprinting) return movement;

		double vertical = falling && Math.abs(movement.y - 0.005) >= 0.003 && Math.abs(movement.y - GRAVITY / 16.0) < 0.003
				? -0.003
				: movement.y - GRAVITY / 16.0;
		return new Vector(movement.x, vertical, movement.z);
	}

	private void jumpOutOfFluid(double startY) {
		Vector movement = deltaMovement;
		if (horizontalCollision && isFree(movement.x, movement.y + 0.6F - y + startY, movement.z))
			deltaMovement = new Vector(movement.x, 0.3F, movement.z);
	}

	private boolean isFree(double dx, double dy, double dz) {
		Box moved = box.move(dx, dy, dz);
		return collisions.noCollision(moved) && !fluids.containsAny(moved);
	}

	private void moveRelative(float amount, @NotNull Vector input) {
		double lengthSqr = input.lengthSqr();
		if (lengthSqr < 1.0E-7) return;

		Vector scaled = (lengthSqr > 1.0 ? input.normalize() : input).scale(amount);
		float sin = trigonometry.sin(yaw * DEGREES_TO_RADIANS);
		float cos = trigonometry.cos(yaw * DEGREES_TO_RADIANS);
		deltaMovement = deltaMovement.add(new Vector(scaled.x * cos - scaled.z * sin, scaled.y, scaled.z * cos + scaled.x * sin));
	}

	private @NotNull Vector handleOnClimbable(@NotNull Vector movement) {
		if (!onClimbable()) return movement;

		fallDistance = 0.0;
		double clampedX = Math.clamp(movement.x, -0.15F, 0.15F);
		double clampedZ = Math.clamp(movement.z, -0.15F, 0.15F);
		double vertical = Math.max(movement.y, -0.15F);
		if (vertical < 0.0 && !inBlock().getBlock().getName().equals("minecraft:scaffolding") && keys.isSneak()) vertical = 0.0;

		return new Vector(clampedX, vertical, clampedZ);
	}

	private boolean onClimbable() {
		BlockState state = inBlock();
		if (state.getBlock().hasTrait(BlockTrait.CLIMBABLE)) return true;
		if (!state.getBlock().hasTrait(BlockTrait.TRAPDOOR) || !"true".equals(state.property("open").orElse(null))) return false;

		BlockState below = view.state(Fluids.floor(x), Fluids.floor(y) - 1, Fluids.floor(z));
		return below.getBlock().getName().equals("minecraft:ladder")
				&& below.property("facing").equals(state.property("facing"));
	}

	private @NotNull BlockState inBlock() {
		return view.state(Fluids.floor(x), Fluids.floor(y), Fluids.floor(z));
	}

	/**
	 * {@code Entity.move} for the player's own movement.
	 */
	private void move(@NotNull Vector requested) {
		Vector movement = requested;
		if (stuckSpeedMultiplier.lengthSqr() > 1.0E-7) {
			movement = movement.multiply(stuckSpeedMultiplier);
			stuckSpeedMultiplier = Vector.ZERO;
			deltaMovement = Vector.ZERO;
		}

		movement = maybeBackOffFromEdge(movement);
		Vector collided = collide(movement);
		double lengthSqr = collided.lengthSqr();
		if (lengthSqr > 1.0E-7 || rules.pathBlockEffects() && movement.lengthSqr() - lengthSqr < 1.0E-7) {
			if (rules.boxDrivenPosition()) {
				box = box.move(collided);
				x = (box.minX + box.maxX) / 2.0;
				y = box.minY;
				z = (box.minZ + box.maxZ) / 2.0;
			} else {
				setPos(x + collided.x, y + collided.y, z + collided.z);
			}
		}

		boolean collidedX = !equal(movement.x, collided.x);
		boolean collidedZ = !equal(movement.z, collided.z);
		horizontalCollision = collidedX || collidedZ;
		boolean verticalCollision = movement.y != collided.y;
		onGround = verticalCollision && movement.y < 0.0;
		if (rules.supportingBlock()) checkSupportingBlock(collided);
		minorHorizontalCollision = horizontalCollision && rules.minorHorizontalCollision() && horizontalCollisionMinor(collided);

		int[] onPos = onPos(0.2F);
		BlockState landed = view.state(onPos[0], onPos[1], onPos[2]);
		checkFallDamage(collided.y);
		if (horizontalCollision)
			deltaMovement = new Vector(collidedX ? 0.0 : deltaMovement.x, deltaMovement.y, collidedZ ? 0.0 : deltaMovement.z);
		if (verticalCollision) fallOn(landed);

		if (!rules.pathBlockEffects()) {
			if (onGround) stepOn(landed);
			for (int[] block : blocksInside(box.deflate(rules.supportingBlock() ? 1.0E-7 : 0.001))) entityInside(block);
		}

		float factor = blockSpeedFactor();
		deltaMovement = deltaMovement.multiply(factor, 1.0, factor);
	}

	private static boolean equal(double first, double second) {
		return Math.abs(second - first) < 1.0E-5F;
	}

	/**
	 * {@code Entity.collide}: moves the player against the blocks and steps up onto what stops it horizontally.
	 */
	private @NotNull Vector collide(@NotNull Vector movement) {
		Vector collided = movement.lengthSqr() == 0.0 ? movement : collisions.collideBoundingBox(movement, box);
		boolean collidedX = movement.x != collided.x;
		boolean collidedY = movement.y != collided.y;
		boolean collidedZ = movement.z != collided.z;
		boolean landing = collidedY && movement.y < 0.0;
		if (!(landing || onGround) || !(collidedX || collidedZ)) return collided;

		if (rules.candidateStepHeights()) return stepUp(movement, collided, landing);

		Vector stepped = collisions.collideBoundingBox(new Vector(movement.x, STEP_HEIGHT, movement.z), box);
		Vector rise = collisions.collideBoundingBox(new Vector(0.0, STEP_HEIGHT, 0.0), box.expandTowards(movement.x, 0.0, movement.z));
		if (rise.y < (double) STEP_HEIGHT) {
			Vector across = collisions.collideBoundingBox(new Vector(movement.x, 0.0, movement.z), box.move(rise)).add(rise);
			if (across.horizontalDistanceSqr() > stepped.horizontalDistanceSqr()) stepped = across;
		}
		if (stepped.horizontalDistanceSqr() <= collided.horizontalDistanceSqr()) return collided;

		return stepped.add(collisions.collideBoundingBox(new Vector(0.0, -stepped.y + movement.y, 0.0), box.move(stepped)));
	}

	private @NotNull Vector stepUp(@NotNull Vector movement, @NotNull Vector collided, boolean landing) {
		Box base = landing ? box.move(0.0, collided.y, 0.0) : box;
		Box reach = base.expandTowards(movement.x, STEP_HEIGHT, movement.z);
		if (!landing) reach = reach.expandTowards(0.0, -1.0E-5F, 0.0);

		List<Collisions.Placed> shapes = collisions.blockCollisions(reach);
		for (float height : Collisions.candidateStepHeights(base, shapes, STEP_HEIGHT, (float) collided.y)) {
			Vector stepped = Collisions.collideWithShapes(new Vector(movement.x, height, movement.z), base, shapes);
			if (stepped.horizontalDistanceSqr() > collided.horizontalDistanceSqr())
				return stepped.subtract(0.0, box.minY - base.minY, 0.0);
		}

		return collided;
	}

	/**
	 * {@code Player.maybeBackOffFromEdge}: a sneaking player on the ground does not walk off an edge it would fall
	 * from by more than the step height.
	 */
	private @NotNull Vector maybeBackOffFromEdge(@NotNull Vector movement) {
		if (!keys.isSneak()) return movement;
		if (!rules.fallTestingEdgeBackOff()) return legacyBackOff(movement);
		if (movement.y > 0.0 || !aboveGround()) return movement;

		double dx = movement.x;
		double dz = movement.z;
		double stepX = Math.signum(dx) * 0.05;
		double stepZ = Math.signum(dz) * 0.05;
		for (; dx != 0.0 && canFallAtLeast(dx, 0.0, STEP_HEIGHT); dx -= stepX) {
			if (Math.abs(dx) <= 0.05) {
				dx = 0.0;
				break;
			}
		}
		while (dz != 0.0 && canFallAtLeast(0.0, dz, STEP_HEIGHT)) {
			if (Math.abs(dz) <= 0.05) {
				dz = 0.0;
				break;
			}
			dz -= stepZ;
		}
		while (dx != 0.0 && dz != 0.0 && canFallAtLeast(dx, dz, STEP_HEIGHT)) {
			if (Math.abs(dx) <= 0.05) dx = 0.0;
			else dx -= stepX;
			if (Math.abs(dz) <= 0.05) dz = 0.0;
			else dz -= stepZ;
		}

		return new Vector(dx, movement.y, dz);
	}

	private boolean aboveGround() {
		if (onGround) return true;
		if (!(fallDistance < STEP_HEIGHT)) return false;

		double remaining = rules.vectorInput() ? STEP_HEIGHT - fallDistance : (float) (STEP_HEIGHT - (float) fallDistance);
		return !canFallAtLeast(0.0, 0.0, remaining);
	}

	private boolean canFallAtLeast(double dx, double dz, double distance) {
		if (rules.vectorInput())
			return collisions.noCollision(new Box(box.minX + 1.0E-7 + dx, box.minY - distance - 1.0E-7, box.minZ + 1.0E-7 + dz,
					box.maxX - 1.0E-7 + dx, box.minY, box.maxZ - 1.0E-7 + dz));

		return collisions.noCollision(new Box(box.minX + dx, box.minY - distance - 1.0E-5F, box.minZ + dz,
				box.maxX + dx, box.minY, box.maxZ + dz));
	}

	/**
	 * The back-off before 1.20.5: the box moved down by the step height must touch a block.
	 */
	private @NotNull Vector legacyBackOff(@NotNull Vector movement) {
		boolean aboveGround = onGround || (float) fallDistance < STEP_HEIGHT
				&& !collisions.noCollision(box.move(0.0, (float) fallDistance - STEP_HEIGHT, 0.0));
		if (!aboveGround) return movement;

		double dx = movement.x;
		double dz = movement.z;
		while (dx != 0.0 && collisions.noCollision(box.move(dx, -STEP_HEIGHT, 0.0))) dx = towardsZero(dx);
		while (dz != 0.0 && collisions.noCollision(box.move(0.0, -STEP_HEIGHT, dz))) dz = towardsZero(dz);
		while (dx != 0.0 && dz != 0.0 && collisions.noCollision(box.move(dx, -STEP_HEIGHT, dz))) {
			dx = towardsZero(dx);
			dz = towardsZero(dz);
		}

		return new Vector(dx, movement.y, dz);
	}

	private static double towardsZero(double value) {
		if (value < 0.05 && value >= -0.05) return 0.0;

		return value > 0.0 ? value - 0.05 : value + 0.05;
	}

	private boolean horizontalCollisionMinor(@NotNull Vector movement) {
		float radians = yaw * DEGREES_TO_RADIANS;
		double sin = trigonometry.sin(radians);
		double cos = trigonometry.cos(radians);
		double inputX = (double) xxa * cos - (double) zza * sin;
		double inputZ = (double) zza * cos + (double) xxa * sin;
		double inputSqr = inputX * inputX + inputZ * inputZ;
		double movementSqr = movement.x * movement.x + movement.z * movement.z;
		if (inputSqr < 1.0E-5F || movementSqr < 1.0E-5F) return false;

		double dot = inputX * movement.x + inputZ * movement.z;
		return Math.acos(dot / Math.sqrt(inputSqr * movementSqr)) < 0.13962634F;
	}

	/**
	 * {@code Entity.checkSupportingBlock}: remembers the block the player landed on.
	 */
	private void checkSupportingBlock(@NotNull Vector movement) {
		if (!onGround) {
			onGroundNoBlocks = false;
			supportingBlock = null;
			return;
		}

		Box below = new Box(box.minX, box.minY - 1.0E-6, box.minZ, box.maxX, box.minY, box.maxZ);
		int[] found = collisions.supportingBlock(below, x, y, z);
		if (found != null || onGroundNoBlocks) {
			supportingBlock = found;
		} else {
			found = collisions.supportingBlock(below.move(-movement.x, 0.0, -movement.z), x, y, z);
			supportingBlock = found;
		}
		onGroundNoBlocks = found == null;
	}

	/**
	 * {@code Entity.getOnPos}: the block an offset below the player's feet, which is the block it stands on where it
	 * stands on one.
	 */
	private int @NotNull [] onPos(float offset) {
		if (rules.supportingBlock()) {
			if (supportingBlock == null) return new int[]{Fluids.floor(x), Fluids.floor(y - offset), Fluids.floor(z)};
			if (!(offset > 1.0E-5F)) return supportingBlock;

			BlockState state = view.state(supportingBlock[0], supportingBlock[1], supportingBlock[2]);
			boolean keep = offset <= 0.5 && state.getBlock().hasTrait(BlockTrait.FENCE)
					|| state.getBlock().hasTrait(BlockTrait.WALL)
					|| state.getBlock().hasTrait(BlockTrait.FENCE_GATE);
			if (keep) return supportingBlock;

			return new int[]{supportingBlock[0], Fluids.floor(y - offset), supportingBlock[2]};
		}

		int[] position = {Fluids.floor(x), Fluids.floor(y - offset), Fluids.floor(z)};
		if (!view.state(position[0], position[1], position[2]).isAir()) return position;

		BlockState below = view.state(position[0], position[1] - 1, position[2]);
		boolean tall = below.getBlock().hasTrait(BlockTrait.FENCE) || below.getBlock().hasTrait(BlockTrait.WALL)
				|| below.getBlock().hasTrait(BlockTrait.FENCE_GATE);
		return tall ? new int[]{position[0], position[1] - 1, position[2]} : position;
	}

	private int @NotNull [] blockPosBelowThatAffectsMyMovement() {
		if (rules.supportingBlock()) return onPos(0.500001F);

		return new int[]{Fluids.floor(x), Fluids.floor(box.minY - 0.5000001), Fluids.floor(z)};
	}

	/**
	 * {@code LivingEntity.checkFallDamage} and {@code Entity.checkFallDamage}: updates the water state out of water
	 * and the fall distance, which landing resets.
	 */
	private void checkFallDamage(double movedY) {
		if (!touchingWater) updateWater();

		if (rules.vectorInput()) {
			if (!touchingWater && movedY < 0.0) fallDistance -= (float) movedY;
			if (onGround) fallDistance = 0.0;
			return;
		}

		if (onGround) fallDistance = 0.0;
		else if (movedY < 0.0) fallDistance = (float) fallDistance - (float) movedY;
	}

	/**
	 * {@code Block.updateEntityMovementAfterFallOn}: slime blocks and beds bounce a player that does not sneak, and
	 * other blocks stop its vertical movement.
	 */
	private void fallOn(@NotNull BlockState landed) {
		double bounce = landed.getBlock().getBounceFactor();
		if (bounce == 0.0 || keys.isSneak()) {
			deltaMovement = deltaMovement.multiply(1.0, 0.0, 1.0);
			return;
		}
		if (deltaMovement.y < 0.0) deltaMovement = new Vector(deltaMovement.x, -deltaMovement.y * bounce * 1.0, deltaMovement.z);
	}

	/**
	 * {@code SlimeBlock.stepOn}: slime slows a player walking on it without sneaking.
	 */
	private void stepOn(@NotNull BlockState state) {
		if (!state.getBlock().hasTrait(BlockTrait.SLIME_STEP)) return;

		double vertical = Math.abs(deltaMovement.y);
		if (!(vertical < 0.1) || keys.isSneak()) return;

		double factor = 0.4 + vertical * 0.2;
		deltaMovement = deltaMovement.multiply(factor, 1.0, factor);
	}

	/**
	 * {@code Entity.applyEffectsFromBlocks} from 1.21.2: the block stood on and the blocks passed through this tick.
	 */
	private void applyEffectsFromBlocks() {
		if (onGround) {
			int[] onPos = onPos(0.2F);
			stepOn(view.state(onPos[0], onPos[1], onPos[2]));
		}

		Box from = Box.at(oldX, oldY, oldZ, WIDTH, height()).deflate(1.0E-5F);
		Box to = box.deflate(1.0E-5F);
		Box passed = new Box(Math.min(from.minX, to.minX), Math.min(from.minY, to.minY), Math.min(from.minZ, to.minZ),
				Math.max(from.maxX, to.maxX), Math.max(from.maxY, to.maxY), Math.max(from.maxZ, to.maxZ));
		for (int[] block : blocksInside(passed)) entityInside(block);
	}

	private @NotNull List<int[]> blocksInside(@NotNull Box inside) {
		int minX = Fluids.floor(inside.minX);
		int minY = Fluids.floor(inside.minY);
		int minZ = Fluids.floor(inside.minZ);
		int maxX = Fluids.floor(inside.maxX);
		int maxY = Fluids.floor(inside.maxY);
		int maxZ = Fluids.floor(inside.maxZ);
		if (!view.hasChunksAt(minX, minZ, maxX, maxZ)) return List.of();

		List<int[]> blocks = new ArrayList<>();
		for (int bx = minX; bx <= maxX; bx++)
			for (int by = minY; by <= maxY; by++)
				for (int bz = minZ; bz <= maxZ; bz++)
					blocks.add(new int[]{bx, by, bz});

		return blocks;
	}

	/**
	 * {@code BlockBehaviour.entityInside} of the blocks that move a player inside them: cobwebs, sweet berry bushes
	 * and powder snow slow it, honey slides it down its sides, and bubble columns push it up or drag it down.
	 */
	private void entityInside(int @NotNull [] block) {
		BlockState state = view.state(block[0], block[1], block[2]);
		String name = state.getBlock().getName();
		if (name.equals("minecraft:powder_snow")) {
			inPowderSnow = true;
			if (inBlock().getBlock() == state.getBlock()) makeStuck(state.getBlock().getStuckSpeed());
		} else if (state.getBlock().getStuckSpeed() != null) {
			makeStuck(state.getBlock().getStuckSpeed());
		}

		if (state.getBlock().hasTrait(BlockTrait.HONEY_SLIDE) && slidingDown(block)) slideDown();
		if (state.getBlock().hasTrait(BlockTrait.BUBBLE_COLUMN)) bubbleColumn(state, block);
	}

	private void makeStuck(@NotNull StuckSpeed stuck) {
		fallDistance = 0.0;
		stuckSpeedMultiplier = new Vector(stuck.getHorizontal(), stuck.getVertical(), stuck.getHorizontal());
	}

	private boolean slidingDown(int @NotNull [] block) {
		if (onGround) return false;
		if (y > block[1] + 0.9375 - 1.0E-7) return false;
		if (deltaMovement.y >= -0.08) return false;

		double dx = Math.abs(block[0] + 0.5 - x);
		double dz = Math.abs(block[2] + 0.5 - z);
		double reach = 0.4375 + WIDTH / 2.0F;
		return dx + 1.0E-7 > reach || dz + 1.0E-7 > reach;
	}

	private void slideDown() {
		Vector movement = deltaMovement;
		if (movement.y < -0.13) {
			double factor = -0.05 / movement.y;
			deltaMovement = new Vector(movement.x * factor, -0.05, movement.z * factor);
		} else {
			deltaMovement = new Vector(movement.x, -0.05, movement.z);
		}
		fallDistance = 0.0;
	}

	private void bubbleColumn(@NotNull BlockState state, int @NotNull [] block) {
		boolean drag = "true".equals(state.property("drag").orElse(null));
		Vector movement = deltaMovement;
		if (view.state(block[0], block[1] + 1, block[2]).isAir()) {
			double vertical = drag ? Math.max(-0.9, movement.y - 0.03) : Math.min(1.8, movement.y + 0.1);
			deltaMovement = new Vector(movement.x, vertical, movement.z);
			return;
		}

		double vertical = drag ? Math.max(-0.3, movement.y - 0.03) : Math.min(0.7, movement.y + 0.06);
		deltaMovement = new Vector(movement.x, vertical, movement.z);
		fallDistance = 0.0;
	}

	private float blockJumpFactor() {
		float inside = inBlock().getBlock().getJumpFactor();
		int[] below = blockPosBelowThatAffectsMyMovement();
		float under = view.state(below[0], below[1], below[2]).getBlock().getJumpFactor();

		return inside == 1.0 ? under : inside;
	}

	private float blockSpeedFactor() {
		BlockState inside = inBlock();
		float factor = inside.getBlock().getSpeedFactor();
		String name = inside.getBlock().getName();
		if (name.equals("minecraft:water") || name.equals("minecraft:bubble_column")) return factor;
		if (factor != 1.0) return factor;

		int[] below = blockPosBelowThatAffectsMyMovement();
		return view.state(below[0], below[1], below[2]).getBlock().getSpeedFactor();
	}

	/**
	 * The player's movement speed attribute: 0.1, raised by 30 % while sprinting
	 * ({@code AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL}).
	 */
	private double movementSpeed() {
		return sprinting ? BASE_SPEED * (1.0 + SPRINT_SPEED_MODIFIER) : BASE_SPEED;
	}

	private boolean canFit(float height) {
		return collisions.noCollision(Box.at(x, y, z, WIDTH, height).deflate(1.0E-7));
	}

	/**
	 * {@code Player.updatePlayerPose}: the player crouches while it holds sneak, and stays crouched where it cannot
	 * stand up.
	 */
	private void updatePose() {
		if (!canFit(0.6F)) return;

		boolean wanted = keys.isSneak();
		if (canFit(wanted ? CROUCHING_HEIGHT : STANDING_HEIGHT)) setCrouchingPose(wanted);
		else if (canFit(CROUCHING_HEIGHT)) setCrouchingPose(true);
	}

	private void setCrouchingPose(boolean crouched) {
		if (crouched == crouchingPose) return;

		crouchingPose = crouched;
		if (rules.boxDrivenPosition()) box = Box.at(x, y, z, WIDTH, height());
		else setPos(x, y, z);
	}

	private float height() {
		return crouchingPose ? CROUCHING_HEIGHT : STANDING_HEIGHT;
	}

	private float eyeHeight() {
		return crouchingPose ? CROUCHING_EYES : STANDING_EYES;
	}

	private void setPos(double x, double y, double z) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.box = Box.at(x, y, z, WIDTH, height());
	}

	/**
	 * Whether the player touches a fluid and how deep it is in it.
	 */
	private record FluidContact(boolean touching, double depth) {
	}
}
