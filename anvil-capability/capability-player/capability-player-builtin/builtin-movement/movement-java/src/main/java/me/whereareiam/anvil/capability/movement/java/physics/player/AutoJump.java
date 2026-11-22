package me.whereareiam.anvil.capability.movement.java.physics.player;

import me.whereareiam.anvil.capability.movement.java.physics.ClientBehaviour;
import me.whereareiam.anvil.capability.movement.java.physics.player.input.KeyInput;
import me.whereareiam.anvil.edition.java.data.block.model.BlockState;
import me.whereareiam.anvil.edition.java.physics.EntityBehaviour;
import me.whereareiam.anvil.edition.java.physics.entity.BlockEffects;
import me.whereareiam.anvil.edition.java.physics.geometry.Box;
import me.whereareiam.anvil.edition.java.physics.geometry.Coordinates;
import me.whereareiam.anvil.edition.java.physics.geometry.Trigonometry;
import me.whereareiam.anvil.edition.java.physics.geometry.Vector;
import me.whereareiam.anvil.edition.java.physics.living.effect.MobEffect;
import me.whereareiam.anvil.edition.java.physics.world.BlockReader;
import me.whereareiam.anvil.edition.java.physics.world.Collisions;
import me.whereareiam.anvil.edition.java.physics.world.shape.BlockShape;
import me.whereareiam.anvil.edition.java.physics.world.shape.PlacedShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The vanilla client's auto-jump, which its default options turn on before 1.19 ({@link ClientBehaviour#AUTO_JUMP}) and
 * which every new player has during its first tick, before it reports one: after each movement on the ground
 * ({@code LocalPlayer.move}), a player that walks towards a block more than half a block and at most its jump height
 * above its feet, with room above its head, presses jump for the next tick ({@code LocalPlayer.updateAutoJump}),
 * which {@code LocalPlayer.aiStep} applies to its input.
 *
 * <p>Not thread-safe; its owner serializes ticks.</p>
 */
final class AutoJump {
	private static final float DEGREES_TO_RADIANS = (float) (Math.PI / 180.0);

	private final @NotNull PlayerBody body;
	private final @NotNull KeyInput input;
	private final @NotNull BlockEffects blocks;
	private final @NotNull BlockReader reader;
	private final @NotNull Collisions collisions;
	private int autoJumpTime;
	/**
	 * Whether auto-jump is on ({@code LocalPlayer.autoJumpEnabled}): on for a new player, then as the client's options
	 * say once it reported its first tick ({@code sendPosition}).
	 */
	private boolean enabled = true;

	/**
	 * Creates the auto-jump of a player.
	 *
	 * @param body the player
	 * @param input its key input, whose move vector tells whether it walks
	 * @param blocks what the blocks around it do to it
	 */
	AutoJump(@NotNull PlayerBody body, @NotNull KeyInput input, @NotNull BlockEffects blocks) {
		this.body = body;
		this.input = input;
		this.blocks = blocks;
		this.reader = body.getLevel();
		this.collisions = new Collisions(reader);
	}

	/**
	 * Presses jump for this tick when the previous movement asked for it ({@code LocalPlayer.aiStep}).
	 *
	 * @return whether auto-jump pressed jump
	 */
	boolean press() {
		if (autoJumpTime <= 0) return false;

		autoJumpTime--;
		body.pressAutoJump();
		return true;
	}

	/**
	 * Takes the client's option after the player reported a tick ({@code LocalPlayer.sendPosition}), which its default
	 * options turn on before 1.19.
	 */
	void reported() {
		enabled = body.has(ClientBehaviour.AUTO_JUMP);
	}

	/**
	 * Decides after a movement whether to jump next tick ({@code LocalPlayer.updateAutoJump}).
	 *
	 * @param fromX where the movement started east-west
	 * @param fromZ where the movement started north-south
	 */
	void moved(double fromX, double fromZ) {
		if (!canAutoJump()) return;

		Vector moved = new Vector((float) (body.getPosition().x - fromX), 0.0, (float) (body.getPosition().z - fromZ));
		Vector direction = direction(moved);
		if (direction == null) return;

		float inverse = fastInvSqrt((float) direction.lengthSqr());
		Vector unit = direction.scale(inverse);
		Vector forward = forward();
		if ((float) (forward.x * unit.x + forward.z * unit.z) < -0.15F) return;
		int[] head = {Coordinates.floor(body.getPosition().x), Coordinates.floor(body.getBox().maxY),
				Coordinates.floor(body.getPosition().z)};
		if (solid(head) || solid(above(head, 1))) return;

		float reach = Math.max(body.getSpeed() * 7.0F, 1.0F / inverse);
		float top = obstacle(body.getPosition().add(moved), unit, reach, above(head, 1));
		if (top == Float.MIN_VALUE) return;

		float rise = (float) (top - body.getPosition().y);
		if (!(rise <= 0.5F) && !(rise > jumpHeight())) autoJumpTime = 1;
	}

	/**
	 * {@code canAutoJump}: on the ground, not sneaking, walking, and not on a block that weakens jumps.
	 */
	private boolean canAutoJump() {
		boolean walking = input.leftImpulse() != 0.0F || input.forwardImpulse() != 0.0F;
		return enabled && autoJumpTime <= 0 && body.isOnGround() && !body.isShiftKeyDown()
				&& walking && blocks.jumpFactor() >= 1.0;
	}

	/**
	 * The direction the player moved, or, when it barely moved, the one its input and speed point to; {@code null} when
	 * neither is long enough.
	 */
	private @Nullable Vector direction(@NotNull Vector moved) {
		if ((float) moved.lengthSqr() > 0.001F) return moved;

		float speed = body.getSpeed();
		float left = speed * input.leftImpulse();
		float forward = speed * input.forwardImpulse();
		Trigonometry trigonometry = body.getRules().trigonometry();
		float sin = trigonometry.sin(body.getYaw() * DEGREES_TO_RADIANS);
		float cos = trigonometry.cos(body.getYaw() * DEGREES_TO_RADIANS);
		Vector wanted = new Vector(left * cos - forward * sin, moved.y, forward * cos + left * sin);
		return (float) wanted.lengthSqr() <= 0.001F ? null : wanted;
	}

	/**
	 * Finds the top of the first box ahead that the player's two sides run into half a block above its feet
	 * ({@code updateAutoJump}'s scan), raised by the blocks stacked on it, or {@link Float#MIN_VALUE} when no box is
	 * ahead or the stack leaves no room to jump.
	 *
	 * @param target where the player's movement leads
	 * @param unit the movement's direction of length one
	 * @param reach how far ahead of the target to look
	 * @param headroom the block above the player's head
	 */
	private float obstacle(@NotNull Vector target, @NotNull Vector unit, float reach, int @NotNull [] headroom) {
		Vector position = body.getPosition();
		Vector end = target.add(unit.scale(reach));
		float width = body.getDimensions().getWidth();
		Box area = inflate(new Box(position, end.add(0.0, body.getDimensions().getHeight(), 0.0)), width);
		Vector from = position.add(0.0, 0.51F, 0.0);
		Vector to = end.add(0.0, 0.51F, 0.0);
		Vector side = new Vector(unit.y * 0.0 - unit.z * 1.0, unit.z * 0.0 - unit.x * 0.0, unit.x * 1.0 - unit.y * 0.0)
				.scale(width * 0.5F);
		Box left = new Box(from.subtract(side.x, side.y, side.z), to.subtract(side.x, side.y, side.z));
		Box right = new Box(from.add(side), to.add(side));
		boolean heightFirst = body.has(EntityBehaviour.HEIGHT_FIRST_SHAPE_BOXES);
		for (PlacedShape shape : collisions.blockCollisions(area, body.context()))
			for (Box box : shape.getShape().toBoxes(shape.getBlock(), heightFirst))
				if (box.intersects(left) || box.intersects(right)) return stacked(box, headroom);
		return Float.MIN_VALUE;
	}

	/**
	 * The top of a box ahead raised by the blocks stacked on it within the jump height, or {@link Float#MIN_VALUE} when
	 * the stack is too high or the blocks above the player's head close.
	 */
	private float stacked(@NotNull Box box, int @NotNull [] headroom) {
		float top = (float) box.maxY;
		int[] base = {Coordinates.floor(box.minX + 0.5 * (box.maxX - box.minX)),
				Coordinates.floor(box.minY + 0.5 * (box.maxY - box.minY)),
				Coordinates.floor(box.minZ + 0.5 * (box.maxZ - box.minZ))};
		int[] head = headroom;
		float jumpHeight = jumpHeight();
		for (int step = 1; step < jumpHeight; step++) {
			int[] stackedOn = above(base, step);
			BlockShape shape = reader.shape(state(stackedOn), stackedOn, body.context());
			if (!shape.isEmpty()) top = (float) shape.maxY() + stackedOn[1];
			if (!shape.isEmpty() && top - body.getPosition().y > jumpHeight) return Float.MIN_VALUE;
			if (step == 1) continue;

			head = above(head, 1);
			if (solid(head)) return Float.MIN_VALUE;
		}
		return top;
	}

	/**
	 * How high auto-jump jumps: {@code 1.2F}, raised by {@code 0.75F} per level of jump boost.
	 */
	private float jumpHeight() {
		int boost = body.getEffects().amplifier(MobEffect.JUMP_BOOST).orElse(-1);
		return boost < 0 ? 1.2F : 1.2F + (boost + 1) * 0.75F;
	}

	/**
	 * {@code Entity.getForward}: the view direction of {@code Vec3.directionFromRotation}.
	 */
	private @NotNull Vector forward() {
		Trigonometry trigonometry = body.getRules().trigonometry();
		float yaw = -body.getYaw() * DEGREES_TO_RADIANS;
		float pitch = -body.getPitch() * DEGREES_TO_RADIANS;
		float cos = trigonometry.cos(yaw - (float) Math.PI);
		float sin = trigonometry.sin(yaw - (float) Math.PI);
		float horizontal = -trigonometry.cos(pitch);
		return new Vector(sin * horizontal, trigonometry.sin(pitch), cos * horizontal);
	}

	private boolean solid(int @NotNull [] block) {
		return !reader.shape(state(block), block, body.context()).isEmpty();
	}

	private @NotNull BlockState state(int @NotNull [] block) {
		return reader.state(block[0], block[1], block[2]);
	}

	/**
	 * {@code Mth.fastInvSqrt}: the inverse square root by one Newton step from a bit-level guess.
	 */
	private static float fastInvSqrt(float value) {
		float half = 0.5F * value;
		float guess = Float.intBitsToFloat(1597463007 - (Float.floatToIntBits(value) >> 1));
		return guess * (1.5F - half * guess * guess);
	}

	private static int @NotNull [] above(int @NotNull [] block, int blocks) {
		return new int[] {block[0], block[1] + blocks, block[2]};
	}

	private static @NotNull Box inflate(@NotNull Box box, double horizontal) {
		return new Box(new Vector(box.minX - horizontal, box.minY, box.minZ - horizontal),
				new Vector(box.maxX + horizontal, box.maxY, box.maxZ + horizontal));
	}
}
