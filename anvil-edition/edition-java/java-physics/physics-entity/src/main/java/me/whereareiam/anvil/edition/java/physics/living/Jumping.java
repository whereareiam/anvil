package me.whereareiam.anvil.edition.java.physics.living;

import me.whereareiam.anvil.edition.java.physics.EntityBehaviour;
import me.whereareiam.anvil.edition.java.physics.entity.BlockEffects;
import me.whereareiam.anvil.edition.java.physics.entity.FluidState;
import me.whereareiam.anvil.edition.java.physics.geometry.Trigonometry;
import me.whereareiam.anvil.edition.java.physics.geometry.Vector;
import me.whereareiam.anvil.edition.java.physics.living.attribute.LivingAttributes;
import me.whereareiam.anvil.edition.java.physics.living.effect.MobEffect;
import org.jetbrains.annotations.NotNull;

/**
 * A living entity's jump, as {@code LivingEntity.aiStep} and {@code jumpFromGround} make it: while it jumps and fluids
 * affect it ({@link LivingBody#affectedByFluids}), a jump from the ground with the jump strength, the block's jump
 * factor and jump boost, ten ticks apart while it keeps jumping, rising in water and lava, and a sprint jump's push
 * along its view.
 */
public final class Jumping {
	private static final int JUMP_DELAY = 10;

	private final @NotNull LivingBody body;
	private final @NotNull FluidState fluids;
	private final @NotNull BlockEffects effects;
	private int noJumpDelay;

	/**
	 * Creates the jumping of a living entity.
	 *
	 * @param body the entity
	 * @param fluids how it is in fluids
	 * @param effects what the blocks do to it
	 */
	public Jumping(@NotNull LivingBody body, @NotNull FluidState fluids, @NotNull BlockEffects effects) {
		this.body = body;
		this.fluids = fluids;
		this.effects = effects;
	}

	/**
	 * Counts the jump delay down, as {@code LivingEntity.aiStep} does first.
	 */
	public void countDown() {
		if (noJumpDelay > 0) noJumpDelay--;
	}

	/**
	 * Jumps or rises in a fluid while the entity jumps and fluids affect it ({@code LivingEntity.aiStep}).
	 */
	public void jump() {
		if (!body.jumping() || !body.affectedByFluids()) {
			noJumpDelay = 0;
			return;
		}

		double depth = fluids.isInLava() ? fluids.lavaHeight() : fluids.waterHeight();
		boolean inWater = fluids.touchingWater() && depth > 0.0;
		double threshold = fluids.fluidJumpThreshold();
		boolean deep = !(body.onGround() && !(depth > threshold));
		if (inWater && deep || fluids.isInLava() && deep) {
			body.addDeltaMovement(0.0, 0.04F, 0.0);
			return;
		}
		if ((body.onGround() || inWater && depth <= threshold) && noJumpDelay == 0) {
			jumpFromGround();
			noJumpDelay = JUMP_DELAY;
		}
	}

	/**
	 * {@code LivingEntity.jumpFromGround}: rises with the jump power, from 1.21.2 keeping a higher upward speed, and
	 * pushes a sprinting entity along its view; a player that starts flying on the ground jumps so too.
	 */
	public void jumpFromGround() {
		float power = jumpPower();
		if (power <= 1.0E-5F) return;

		Vector delta = body.deltaMovement();
		double rise = body.has(EntityBehaviour.PATH_BLOCK_EFFECTS) ? Math.max(power, delta.y) : power;
		body.deltaMovement(new Vector(delta.x, rise, delta.z));
		if (!body.sprinting()) return;

		Trigonometry trigonometry = body.rules().trigonometry();
		float radians = body.yaw() * (float) (Math.PI / 180.0);
		if (body.has(EntityBehaviour.DOUBLE_SPRINT_JUMP_BOOST))
			body.addDeltaMovement(-trigonometry.sin(radians) * 0.2, 0.0, trigonometry.cos(radians) * 0.2);
		else body.addDeltaMovement(-trigonometry.sin(radians) * 0.2F, 0.0, trigonometry.cos(radians) * 0.2F);
	}

	/**
	 * {@code LivingEntity.getJumpPower}: the jump strength times the block's jump factor, plus {@code 0.1F} per level of
	 * jump boost ({@code getJumpBoostPower}).
	 */
	private float jumpPower() {
		float strength = (float) body.attributes().value(LivingAttributes.JUMP_STRENGTH);
		int boost = body.effects().amplifier(MobEffect.JUMP_BOOST).orElse(-1);
		float boostPower = boost < 0 ? 0.0F : 0.1F * (boost + 1.0F);
		return strength * 1.0F * effects.jumpFactor() + boostPower;
	}
}
