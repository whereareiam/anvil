package me.whereareiam.anvil.capability.movement.cloudburst.v1_26_60;

import me.whereareiam.anvil.capability.movement.model.Position;
import me.whereareiam.anvil.capability.movement.packet.MovementPackets;
import me.whereareiam.anvil.protocol.cloudburst.client.CloudburstSession;
import me.whereareiam.anvil.protocol.cloudburst.client.model.GameStart;
import org.cloudburstmc.math.vector.Vector2f;
import org.cloudburstmc.math.vector.Vector3f;
import org.cloudburstmc.protocol.bedrock.data.ClientPlayMode;
import org.cloudburstmc.protocol.bedrock.data.InputInteractionModel;
import org.cloudburstmc.protocol.bedrock.data.InputMode;
import org.cloudburstmc.protocol.bedrock.data.definitions.ItemDefinition;
import org.cloudburstmc.protocol.bedrock.data.inventory.ItemData;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.MovePlayerPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayerAuthInputPacket;
import org.jetbrains.annotations.NotNull;

/**
 * Movement packets of the CloudburstMC Protocol release for every Bedrock codec it has. The session's movement
 * authority selects the packet: under server authority, which every protocol from 818 on and Lumi by default use, a
 * player authoritative input of the session's current tick reports the position; under the client authority an
 * older server may grant, a normal player move sets it.
 *
 * <p>Bedrock positions a player by its eyes, so the packets carry the position 1.62 blocks above the
 * feet the {@link Position} names, and a rotation of pitch, yaw and a head yaw equal to the yaw. The input is that of
 * a mouse player aiming with the crosshair that presses no movement key: its move vectors and its position delta are
 * zero, and its camera looks where the view does. Under server authority the server decides whether the player
 * stands on the ground, so only a player move carries the position's ground state.</p>
 */
public final class CloudburstMovementPackets implements MovementPackets<CloudburstSession<BedrockPacket, ItemDefinition, ItemData>> {
	@SuppressWarnings("unchecked")
	private static final Class<CloudburstSession<BedrockPacket, ItemDefinition, ItemData>> SESSION =
			(Class<CloudburstSession<BedrockPacket, ItemDefinition, ItemData>>) (Class<?>) CloudburstSession.class;
	private static final float EYE_HEIGHT = 1.62F;

	@Override
	public @NotNull Class<CloudburstSession<BedrockPacket, ItemDefinition, ItemData>> sessionType() {
		return SESSION;
	}

	@Override
	public void move(@NotNull CloudburstSession<BedrockPacket, ItemDefinition, ItemData> session, @NotNull Position position) {
		GameStart start = session.start()
				.orElseThrow(() -> new IllegalStateException("A Bedrock player moves only after its game started"));
		Vector3f eyes = Vector3f.from(position.getX(), position.getY() + EYE_HEIGHT, position.getZ());
		Vector3f rotation = Vector3f.from(position.getPitch(), position.getYaw(), position.getYaw());

		if (session.serverAuthoritativeMovement()) session.send(input(eyes, rotation, position, session.tick()));
		else session.send(move(start, eyes, rotation, position, session.tick()));
	}

	private static PlayerAuthInputPacket input(Vector3f eyes, Vector3f rotation, Position position, long tick) {
		PlayerAuthInputPacket packet = new PlayerAuthInputPacket();
		packet.setPosition(eyes);
		packet.setRotation(rotation);
		packet.setInteractRotation(Vector2f.from(position.getPitch(), position.getYaw()));
		packet.setCameraOrientation(view(position.getYaw(), position.getPitch()));
		packet.setMotion(Vector2f.ZERO);
		packet.setAnalogMoveVector(Vector2f.ZERO);
		packet.setRawMoveVector(Vector2f.ZERO);
		packet.setDelta(Vector3f.ZERO);
		packet.setInputMode(InputMode.MOUSE);
		packet.setPlayMode(ClientPlayMode.NORMAL);
		packet.setInputInteractionModel(InputInteractionModel.CROSSHAIR);
		packet.setTick(tick);
		return packet;
	}

	private static MovePlayerPacket move(GameStart start, Vector3f eyes, Vector3f rotation, Position position, long tick) {
		MovePlayerPacket packet = new MovePlayerPacket();
		packet.setRuntimeEntityId(start.getRuntimeEntityId());
		packet.setPosition(eyes);
		packet.setRotation(rotation);
		packet.setMode(MovePlayerPacket.Mode.NORMAL);
		packet.setOnGround(position.isOnGround());
		packet.setTick(tick);
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
