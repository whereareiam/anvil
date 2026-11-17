package me.whereareiam.anvil.protocol.java.physics.living;

import me.whereareiam.anvil.protocol.java.physics.EntityBehaviour;
import me.whereareiam.anvil.protocol.java.physics.entity.BlockEffects;
import me.whereareiam.anvil.protocol.java.physics.entity.FluidState;
import me.whereareiam.anvil.protocol.java.geometry.Vector;
import me.whereareiam.anvil.protocol.java.physics.living.effect.Effects;
import me.whereareiam.anvil.protocol.java.physics.living.effect.MobEffect;
import org.jetbrains.annotations.NotNull;

/**
 * The ticks of a living entity on one {@link LivingBody}, as {@code LivingEntity.tick} runs them without a vehicle,
 * gliding, swimming or item use: the fluids of {@code Entity.baseTick}, then {@code LivingEntity.aiStep} with its dead
 * zone of small speeds, the entity's controls ({@link #serverAiStep}), its jump, its {@link #travel} with the collisions
 * and block effects of {@code Entity.move}, and, from 1.21.2, the effects of the blocks it passed. Floats and doubles
 * are combined in vanilla's order, so that a tick gives the bits the client of the same version computes.
 *
 * <p>A subclass steers the entity as vanilla's subclasses do: {@link #serverAiStep} sets its input, as
 * {@code LocalPlayer} reads its keys there, and {@link #travel} and {@link #aiStep} wrap what the entity does, as
 * {@code Player.travel} keeps a flying player's height and {@code LocalPlayer.aiStep} toggles its flight.</p>
 *
 * <pre>{@code
 * LivingSimulation zombie = new LivingSimulation(body);
 * for (int tick = 0; tick < 20; tick++) zombie.tick();
 * Vector landed = body.getPosition();
 * }</pre>
 *
 * <p>Not thread-safe; its owner serializes ticks and placements.</p>
 */
public class LivingSimulation {
	private final @NotNull LivingBody body;
	private final @NotNull FluidState fluids;
	private final @NotNull BlockEffects blocks;
	private final @NotNull Jumping jumping;
	private final @NotNull Travel travel;

	/**
	 * Creates the simulation of a living entity.
	 *
	 * @param body the entity
	 */
	public LivingSimulation(@NotNull LivingBody body) {
		this.body = body;
		this.fluids = new FluidState(body);
		this.blocks = new BlockEffects(body);
		this.jumping = new Jumping(body, fluids, blocks);
		this.travel = new Travel(body, fluids, blocks);
	}

	/**
	 * Runs one tick, reading the world at one moment ({@code LivingEntity.tick}): the tick's start, the base tick and the
	 * entity's own step.
	 */
	public void tick() {
		body.getLevel().read(() -> {
			body.setOldPosition(body.getPosition());
			baseTick();
			aiStep();
		});
	}

	/**
	 * Measures the fluids around the entity, which is no longer in its first tick after ({@code Entity.baseTick}).
	 */
	public final void baseTick() {
		fluids.baseTick();
		body.setFirstTick(false);
	}

	/**
	 * {@code LivingEntity.aiStep}: the jump delay, the dead zone of small speeds, the input, the jump, before 1.21.5 the
	 * input's damping, the travel and, from 1.21.2, the effects of the blocks passed.
	 */
	public void aiStep() {
		jumping.countDown();
		deadZone();
		controls();
		jumping.jump();
		if (body.has(EntityBehaviour.DAMPED_INPUT)) {
			body.setXxa(body.getXxa() * 0.98F);
			body.setZza(body.getZza() * 0.98F);
		}
		Effects effects = body.getEffects();
		if (effects.has(MobEffect.SLOW_FALLING) || effects.has(MobEffect.LEVITATION)) body.resetFallDistance();
		travel(new Vector(body.getXxa(), 0.0, body.getZza()));
		if (body.has(EntityBehaviour.PATH_BLOCK_EFFECTS)) blocks.afterMove();
	}

	/**
	 * Sets the entity's input and jumping from its controls ({@code LivingEntity.serverAiStep}), which keeps the input
	 * by default.
	 */
	protected void serverAiStep() {
		// A living entity without controls of its own keeps the input it was given.
	}

	/**
	 * Travels one tick with the entity's input ({@code LivingEntity.travel}).
	 *
	 * @param input the strafing, vertical and forward input
	 */
	protected void travel(@NotNull Vector input) {
		travel.travel(input);
	}

	/**
	 * Returns how the entity is in fluids.
	 *
	 * @return its fluid state
	 */
	public @NotNull FluidState fluids() {
		return fluids;
	}

	/**
	 * Returns the entity's jump, which a player that starts flying uses too.
	 *
	 * @return its jumping
	 */
	public @NotNull Jumping jumping() {
		return jumping;
	}

	/**
	 * Returns what the blocks around the entity do to it, such as their jump factor.
	 *
	 * @return its block effects
	 */
	public @NotNull BlockEffects blockEffects() {
		return blocks;
	}

	/**
	 * Returns the entity whose ticks this runs.
	 *
	 * @return its body
	 */
	public @NotNull LivingBody body() {
		return body;
	}

	/**
	 * The entity's controls set its input, unless it cannot act, which leaves it without input and jump.
	 */
	private void controls() {
		if (!body.isImmobile()) {
			serverAiStep();
			return;
		}

		body.setJumping(false);
		body.setXxa(0.0F);
		body.setZza(0.0F);
	}

	/**
	 * Drops speeds too small to matter: from 1.21.5 a horizontal speed whose square is below {@code 9.0E-6}, before each
	 * horizontal component below {@code 0.003}; a vertical one below {@code 0.003} always.
	 */
	private void deadZone() {
		Vector delta = body.getDeltaMovement();
		boolean squared = body.has(EntityBehaviour.SQUARED_DEAD_ZONE);
		boolean stillHorizontally = squared && delta.horizontalDistanceSqr() < 9.0E-6;
		double x = stillHorizontally || !squared && Math.abs(delta.x) < 0.003 ? 0.0 : delta.x;
		double z = stillHorizontally || !squared && Math.abs(delta.z) < 0.003 ? 0.0 : delta.z;
		double y = Math.abs(delta.y) < 0.003 ? 0.0 : delta.y;
		body.setDeltaMovement(new Vector(x, y, z));
	}
}
