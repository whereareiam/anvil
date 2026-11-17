package me.whereareiam.anvil.capability.movement.java.physics.player;

import me.whereareiam.anvil.capability.movement.java.physics.ClientBehaviour;
import me.whereareiam.anvil.capability.movement.java.physics.MovementRules;
import me.whereareiam.anvil.capability.movement.java.physics.player.input.KeyInput;
import me.whereareiam.anvil.capability.movement.java.physics.server.PlayerState;
import me.whereareiam.anvil.capability.movement.model.MovementInput;
import me.whereareiam.anvil.capability.movement.model.Position;
import me.whereareiam.anvil.protocol.java.physics.geometry.Axis;
import me.whereareiam.anvil.protocol.java.physics.geometry.Vector;
import me.whereareiam.anvil.protocol.java.physics.living.LivingSimulation;
import me.whereareiam.anvil.protocol.java.physics.world.BlockReader;
import org.jetbrains.annotations.NotNull;

/**
 * One tick of the vanilla client's own player at a time, as {@code LocalPlayer.tick} runs it without a vehicle,
 * gliding, swimming or item use, on the physics kit's living entity: {@code Player.tick} around the base tick, the
 * keys, crouching, sprinting and flight of {@code LocalPlayer.aiStep} around the living entity's own step, the keys as
 * its input ({@code LocalPlayer.serverAiStep}), a flying player's travel ({@code Player.travel}) and the pose of
 * {@code Player.updatePlayerPose}, all on one {@link PlayerBody}, which keeps the flight changes a tick tells the
 * server. A dead player holds no keys.
 *
 * <p>Not thread-safe; its owner serializes ticks and placements.</p>
 */
public final class PlayerSimulation extends LivingSimulation {
	private final @NotNull PlayerBody body;
	private final @NotNull Pose pose;
	private final @NotNull Flight flight;
	private final @NotNull KeyInput input;
	private @NotNull MovementInput keys = MovementInput.NONE;

	private PlayerSimulation(@NotNull PlayerBody body) {
		super(body);
		this.body = body;
		this.pose = new Pose(body);
		this.flight = new Flight(body, jumping());
		this.input = KeyInput.of(body, pose, fluids());
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
		server.getAttributes().sprinting(false);
		PlayerSimulation simulation = new PlayerSimulation(new PlayerBody(rules, reader, server));
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
	@Override
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
		this.keys = keys;
		tick();
	}

	/**
	 * {@code Player.tick}: a spectator moves through blocks off the ground, the eyes' water is remembered, the pose's
	 * dimensions apply, then the base tick, the player's own step, the world's border and the pose for the next tick.
	 */
	@Override
	public void tick() {
		body.getLevel().read(() -> {
			body.startTick();
			body.setOldPosition(body.getPosition());
			body.setNoPhysics(body.isSpectator());
			if (body.isSpectator()) body.setOnGround(false);
			body.setUnderWater(fluids().isEyeInWater());
			pose.refresh();
			baseTick();
			aiStep();
			clampToWorld();
			pose.update();
		});
	}

	/**
	 * {@code LocalPlayer.aiStep}: the keys, crouching and sprinting, flight and sinking in water, then the living
	 * entity's own step, with a flying player's fall distance reset before it from 1.21.2 and after it before, and the
	 * landing of a flight.
	 */
	@Override
	public void aiStep() {
		boolean wasJumping = body.getKeys().isJump();
		input.read(body.getServer().isDead() ? MovementInput.NONE : keys);
		flight.toggle(wasJumping);
		if (fluids().isTouchingWater() && body.isShiftKeyDown() && body.isAffectedByFluids())
			body.addDeltaMovement(0.0, -0.04F, 0.0);
		flight.steer();
		flight.countDown();
		boolean flying = body.getServer().isFlying();
		if (flying && body.has(ClientBehaviour.FLIGHT_RESETS_FALL_FIRST)) body.resetFallDistance();
		super.aiStep();
		if (flying && !body.has(ClientBehaviour.FLIGHT_RESETS_FALL_FIRST)) body.resetFallDistance();
		body.rememberFlyingSpeed();
		flight.land();
	}

	/**
	 * {@code LocalPlayer.serverAiStep}: the keys become the player's input and jump.
	 */
	@Override
	protected void serverAiStep() {
		input.apply();
	}

	/**
	 * {@code Player.travel}: a flying player keeps {@code 0.6} of the vertical speed its travel started with.
	 */
	@Override
	protected void travel(@NotNull Vector input) {
		if (!body.getServer().isFlying()) {
			super.travel(input);
			return;
		}

		double vertical = body.getDeltaMovement().y;
		super.travel(input);
		body.setDeltaMovement(body.getDeltaMovement().with(Axis.Y, vertical * 0.6));
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
