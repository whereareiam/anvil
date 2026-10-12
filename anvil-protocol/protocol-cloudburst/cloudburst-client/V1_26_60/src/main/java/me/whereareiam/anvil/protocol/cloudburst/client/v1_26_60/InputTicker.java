package me.whereareiam.anvil.protocol.cloudburst.client.v1_26_60;

import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.ClientPlayMode;
import org.cloudburstmc.protocol.bedrock.data.InputInteractionModel;
import org.cloudburstmc.protocol.bedrock.data.InputMode;
import org.cloudburstmc.protocol.bedrock.data.PlayerAuthInputData;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.cloudburstmc.protocol.bedrock.packet.StartGamePacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * The player authoritative input one connection's client sends on every tick while the server is authoritative over
 * its movement, as a Bedrock client does from its spawn on: a server under that authority takes the player's
 * position only from these inputs, and drops the ones that arrive while it still completes a teleport. The input
 * repeats the position and rotation the {@link PositionTracker} holds, so a move the client sent once, or a position
 * the server set, stays in effect until the next one.
 *
 * <p>Each input is that of a mouse player aiming with the crosshair that presses no movement key, like the inputs
 * the movement capability sends. The first input after a teleport or respawn move of the player's own entity
 * reports that the client handled the teleport. A tick in which the client already sent an input of its own, such
 * as a move, sends no second one.</p>
 *
 * <p>The ticks run on the connection's network thread; packets reach {@link #received} on that thread and
 * {@link #sent} on the threads that send them.</p>
 */
final class InputTicker {
	private static final long TICK_MILLIS = 50;

	private final PositionTracker positions;
	private final LongSupplier tick;
	private final BooleanSupplier serverMovement;
	private final Consumer<BedrockPacket> send;

	private volatile long runtimeEntityId;
	private volatile boolean teleported;
	private volatile long lastInputTick = -1;
	private volatile @Nullable ScheduledFuture<?> ticks;

	/**
	 * @param positions position and rotation the inputs repeat
	 * @param tick the client tick an input sent now carries
	 * @param serverMovement whether the server is currently authoritative over the player's movement
	 * @param send sends an input on the connection
	 */
	InputTicker(
			@NotNull PositionTracker positions,
			@NotNull LongSupplier tick,
			@NotNull BooleanSupplier serverMovement,
			@NotNull Consumer<BedrockPacket> send
	) {
		this.positions = positions;
		this.tick = tick;
		this.serverMovement = serverMovement;
		this.send = send;
	}

	/**
	 * Applies a packet the server sent.
	 *
	 * @param packet received packet
	 */
	void received(@NotNull BedrockPacket packet) {
		switch (packet) {
			case StartGamePacket start -> runtimeEntityId = start.getRuntimeEntityId();
			case MovePlayerPacket move -> {
				if (move.getRuntimeEntityId() == runtimeEntityId && (move.getMode() == MovePlayerPacket.Mode.TELEPORT || move.getMode() == MovePlayerPacket.Mode.RESPAWN))
					teleported = true;
			}
			default -> { }
		}
	}

	/**
	 * Applies a packet the client is about to send.
	 *
	 * @param packet packet to send
	 */
	void sent(@NotNull BedrockPacket packet) {
		if (packet instanceof PlayerAuthInputPacket input) lastInputTick = input.getTick();
	}

	/**
	 * Starts sending an input on every tick, once the player spawned.
	 *
	 * @param scheduler the connection's network thread
	 */
	void start(@NotNull ScheduledExecutorService scheduler) {
		if (ticks != null) return;

		ticks = scheduler.scheduleAtFixedRate(this::tick, TICK_MILLIS, TICK_MILLIS, TimeUnit.MILLISECONDS);
	}

	/**
	 * Stops the inputs once the connection closed.
	 */
	void stop() {
		ScheduledFuture<?> current = ticks;
		if (current != null) current.cancel(false);
	}

	/**
	 * Sends the input of the current tick, unless the server leaves the movement to the client or the client already
	 * sent one in this tick.
	 */
	void tick() {
		if (!serverMovement.getAsBoolean()) return;

		long now = tick.getAsLong();
		if (now == lastInputTick) return;

		boolean handledTeleport = teleported;
		teleported = false;
		try {
			send.accept(input(positions.pose(), now, handledTeleport));
		} catch (IllegalStateException closed) {
			// The connection closed between two ticks; its close stops the ticks.
			stop();
		}
	}

	private static PlayerAuthInputPacket input(PositionTracker.Pose pose, long tick, boolean handledTeleport) {
		Vector3f rotation = pose.rotation();
		PlayerAuthInputPacket packet = new PlayerAuthInputPacket();
		packet.setPosition(pose.eyes());
		packet.setRotation(rotation);
		packet.setInteractRotation(Vector2f.from(rotation.getX(), rotation.getY()));
		packet.setCameraOrientation(view(rotation.getY(), rotation.getX()));
		packet.setMotion(Vector2f.ZERO);
		packet.setAnalogMoveVector(Vector2f.ZERO);
		packet.setRawMoveVector(Vector2f.ZERO);
		packet.setDelta(Vector3f.ZERO);
		packet.setInputMode(InputMode.MOUSE);
		packet.setPlayMode(ClientPlayMode.NORMAL);
		packet.setInputInteractionModel(InputInteractionModel.CROSSHAIR);
		packet.setTick(tick);
		if (handledTeleport) packet.getInputData().add(PlayerAuthInputData.HANDLE_TELEPORT);
		return packet;
	}

	/**
	 * Returns the unit vector a view of the given yaw and pitch, in degrees, looks along.
	 */
	private static Vector3f view(float yaw, float pitch) {
		double yawRadians = Math.toRadians(yaw);
		double pitchRadians = Math.toRadians(pitch);
		double horizontal = Math.cos(pitchRadians);

		return Vector3f.from(-Math.sin(yawRadians) * horizontal, -Math.sin(pitchRadians), Math.cos(yawRadians) * horizontal);
	}
}
