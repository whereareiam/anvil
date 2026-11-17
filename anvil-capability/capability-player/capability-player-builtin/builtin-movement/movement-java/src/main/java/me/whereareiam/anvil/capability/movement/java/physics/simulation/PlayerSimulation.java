package me.whereareiam.anvil.capability.movement.java.physics.simulation;

import me.whereareiam.anvil.capability.movement.java.physics.ClientBehaviour;
import me.whereareiam.anvil.capability.movement.java.physics.MovementRules;
import me.whereareiam.anvil.capability.movement.java.physics.geometry.Vector;
import me.whereareiam.anvil.capability.movement.java.physics.server.MobEffect;
import me.whereareiam.anvil.capability.movement.java.physics.server.PlayerState;
import me.whereareiam.anvil.capability.movement.java.physics.simulation.input.KeyInput;
import me.whereareiam.anvil.capability.movement.java.physics.simulation.move.EntityMove;
import me.whereareiam.anvil.capability.movement.java.physics.world.BlockReader;
import me.whereareiam.anvil.capability.movement.java.physics.world.Collisions;
import me.whereareiam.anvil.capability.movement.model.MovementInput;
import me.whereareiam.anvil.capability.movement.model.Position;
import org.jetbrains.annotations.NotNull;

/**
 * One tick of the vanilla client's own player at a time, as {@code LocalPlayer.tick} runs it without a vehicle,
 * gliding, swimming or item use: the fluids of {@code Entity.baseTick}, the keys, crouching, sprinting and flight of
 * {@code LocalPlayer.aiStep}, the jump and travel of {@code LivingEntity.aiStep}, the collisions and block effects of
 * {@code Entity.move} and the pose of {@code Player.updatePlayerPose}, all on one {@link PlayerBody}, which keeps the
 * flight changes a tick tells the server. A dead player
 * holds no keys and has no input ({@code LivingEntity.isImmobile}). Floats and doubles are combined in vanilla's order,
 * so that a tick gives the bits the client of the same version computes.
 *
 * <p>Not thread-safe; its owner serializes ticks and placements.</p>
 */
public final class PlayerSimulation {
	private final @NotNull PlayerBody body;
	private final @NotNull BlockReader reader;
	private final @NotNull Pose pose;
	private final @NotNull FluidState fluids;
	private final @NotNull BlockEffects effects;
	private final @NotNull Jumping jumping;
	private final @NotNull Travel travel;
	private final @NotNull KeyInput input;

	private PlayerSimulation(@NotNull MovementRules rules, @NotNull PlayerState server, @NotNull BlockReader reader) {
		server.getAttributes().sprinting(false);
		this.body = new PlayerBody(rules, server);
		this.reader = reader;
		this.pose = new Pose(body, new Collisions(reader));
		this.fluids = new FluidState(body, reader);
		this.effects = new BlockEffects(body, reader);
		this.jumping = new Jumping(body, fluids, effects);
		this.travel = new Travel(body, reader, new EntityMove(body, reader, effects, fluids), fluids, effects);
		this.input = KeyInput.of(body, pose, fluids);
	}

	/**
	 * Starts simulating a player where the client last told the server it is.
	 *
	 * @param rules the client's rules
	 * @param server what the server set of the player
	 * @param reader the blocks its client knows
	 * @param at the player's position, view and ground state
	 * @param deltaMovement the player's velocity
	 * @return the simulation
	 */
	public static @NotNull PlayerSimulation start(
			@NotNull MovementRules rules,
			@NotNull PlayerState server,
			@NotNull BlockReader reader,
			@NotNull Position at,
			@NotNull Vector deltaMovement
	) {
		PlayerSimulation simulation = new PlayerSimulation(rules, server, reader);
		simulation.place(new Vector(at.getX(), at.getY(), at.getZ()), deltaMovement, at.getYaw(), at.getPitch());
		simulation.body.setOnGround(at.isOnGround());
		return simulation;
	}

	/**
	 * Returns where the player is.
	 *
	 * @return its position, view and ground state
	 */
	public @NotNull Position position() {
		return Position.builder()
				.x(body.getPosition().x)
				.y(body.getPosition().y)
				.z(body.getPosition().z)
				.yaw(body.getYaw())
				.pitch(body.getPitch())
				.onGround(body.isOnGround())
				.build();
	}

	/**
	 * Places the player where a test moves it, at rest and on the ground as the test says, without a fall behind it.
	 *
	 * @param at the player's position, view and ground state
	 */
	public void moveTo(@NotNull Position at) {
		place(new Vector(at.getX(), at.getY(), at.getZ()), Vector.ZERO, at.getYaw(), at.getPitch());
		body.setOnGround(at.isOnGround());
		body.resetFallDistance();
	}

	/**
	 * Returns the player's state, which the client reports and the server's packets change.
	 *
	 * @return the player's body
	 */
	public @NotNull PlayerBody body() {
		return body;
	}

	/**
	 * Places the player as a server teleport does ({@code setPos}, {@code setDeltaMovement}, {@code setYRot},
	 * {@code setXRot}); the ground state stays.
	 *
	 * @param position the new position
	 * @param deltaMovement the new velocity
	 * @param yaw the new horizontal view direction
	 * @param pitch the new vertical view direction
	 */
	public void place(@NotNull Vector position, @NotNull Vector deltaMovement, float yaw, float pitch) {
		body.setPosition(position);
		body.setDeltaMovement(deltaMovement);
		body.setYaw(yaw);
		body.setPitch(pitch);
	}

	/**
	 * Turns the player's view, as the mouse does between ticks.
	 *
	 * @param yaw the horizontal view direction
	 * @param pitch the vertical view direction
	 */
	public void look(float yaw, float pitch) {
		body.setYaw(yaw);
		body.setPitch(pitch);
	}

	/**
	 * Runs one tick with the keys held from this tick on, reading the world at one moment.
	 *
	 * @param keys the held keys, which a dead player ignores
	 */
	public void tick(@NotNull MovementInput keys) {
		reader.read(() -> {
			body.startTick();
			body.setOldPosition(body.getPosition());
			if (body.getServer().isSpectator()) body.setOnGround(false);
			fluids.startTick();
			pose.refresh();
			fluids.baseTick();
			body.setFirstTick(false);
			aiStep(body.getServer().isDead() ? MovementInput.NONE : keys);
			clampToWorld();
			pose.update();
		});
	}

	private void aiStep(@NotNull MovementInput keys) {
		boolean wasJumping = body.getKeys().isJump();
		input.read(keys);
		jumping.flight(wasJumping);
		jumping.countDown();
		boolean flying = body.getServer().isFlying();
		if (flying && body.has(ClientBehaviour.FLIGHT_RESETS_FALL_FIRST)) body.resetFallDistance();
		livingAiStep();
		if (flying && !body.has(ClientBehaviour.FLIGHT_RESETS_FALL_FIRST)) body.resetFallDistance();
		travel.afterAiStep();
		jumping.land();
	}

	/**
	 * {@code LivingEntity.aiStep}: the dead zone of small speeds, the input, the jump, the travel and, from 1.21.2, the
	 * effects of the blocks passed.
	 */
	private void livingAiStep() {
		deadZone();
		input.apply();
		if (body.getServer().isDead()) {
			body.setJumping(false);
			body.setXxa(0.0F);
			body.setZza(0.0F);
		}
		jumping.jump();
		input.beforeTravel();
		boolean floating = body.getServer().getEffects().has(MobEffect.SLOW_FALLING)
				|| body.getServer().getEffects().has(MobEffect.LEVITATION);
		if (floating) body.resetFallDistance();
		travel.travel();
		if (body.has(ClientBehaviour.PATH_BLOCK_EFFECTS)) effects.afterMove();
	}

	/**
	 * Drops speeds too small to matter: from 1.21.5 a horizontal speed whose square is below {@code 9.0E-6}, before each
	 * horizontal component below {@code 0.003}; a vertical one below {@code 0.003} always.
	 */
	private void deadZone() {
		Vector delta = body.getDeltaMovement();
		boolean vector = body.has(ClientBehaviour.VECTOR_INPUT);
		boolean stillHorizontally = vector && delta.horizontalDistanceSqr() < 9.0E-6;
		double x = stillHorizontally || !vector && Math.abs(delta.x) < 0.003 ? 0.0 : delta.x;
		double z = stillHorizontally || !vector && Math.abs(delta.z) < 0.003 ? 0.0 : delta.z;
		double y = Math.abs(delta.y) < 0.003 ? 0.0 : delta.y;
		body.setDeltaMovement(new Vector(x, y, z));
	}

	/**
	 * Keeps the player inside the world's border of thirty million blocks ({@code Player.tick}).
	 */
	private void clampToWorld() {
		Vector position = body.getPosition();
		double x = Math.clamp(position.x, -2.9999999E7, 2.9999999E7);
		double z = Math.clamp(position.z, -2.9999999E7, 2.9999999E7);
		if (x != position.x || z != position.z) body.setPosition(new Vector(x, position.y, z));
	}
}
