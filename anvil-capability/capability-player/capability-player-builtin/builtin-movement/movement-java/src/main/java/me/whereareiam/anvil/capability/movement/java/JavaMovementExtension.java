package me.whereareiam.anvil.capability.movement.java;

import me.whereareiam.anvil.capability.movement.channel.MovementChannel;
import me.whereareiam.anvil.capability.movement.model.Position;
import me.whereareiam.anvil.protocol.api.model.player.ViewRotation;
import me.whereareiam.anvil.protocol.api.worker.OperationRegistry;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.protocol.api.worker.WorkerBinding;
import me.whereareiam.anvil.protocol.api.worker.WorkerExtension;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import me.whereareiam.anvil.protocol.java.api.model.world.Vec3d;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundMovePlayerPosRot;
import org.jetbrains.annotations.NotNull;


/**
 * Installs the built-in movement capability for players that speak the Java Edition protocol: each move sends the
 * position and view direction in one packet, without horizontal collision, and records the view rotation that other
 * native actions share. A move the player cannot send, because it is not connected, leaves that rotation unchanged.
 */
public final class JavaMovementExtension implements WorkerExtension<Object> {
	@Override
	public @NotNull String id() {
		return MovementChannel.ID;
	}

	@Override
	public @NotNull Class<Object> nativeSessionType() {
		return Object.class;
	}

	@Override
	public @NotNull WorkerBinding bind(@NotNull PlayerBindingContext<Object> player, @NotNull OperationRegistry operations) {
		JavaProtocol protocol = player.protocol(JavaProtocol.class);
		operations.register(MovementChannel.MOVE, position -> {
			move(player, protocol, position);
			return null;
		});

		return () -> { };
	}

	private static void move(PlayerBindingContext<Object> player, JavaProtocol protocol, Position position) {
		protocol.send(ServerboundMovePlayerPosRot.builder()
				.position(new Vec3d(position.getX(), position.getY(), position.getZ()))
				.yaw(position.getYaw())
				.pitch(position.getPitch())
				.onGround(position.isOnGround())
				.build());
		player.viewRotation(new ViewRotation(position.getYaw(), position.getPitch()));
	}
}
