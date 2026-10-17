package me.whereareiam.anvil.capability.movement.mcprotocol.v1_18_2;

import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundMovePlayerPosRotPacket;
import com.github.steveice10.packetlib.Session;
import me.whereareiam.anvil.capability.movement.model.Position;
import me.whereareiam.anvil.capability.movement.packet.MovementPackets;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class McProtocolMovementPacketsTest {
	@Test
	void sendsOnePositionAndRotationPacket() {
		List<Object> sent = new ArrayList<>();

		new McProtocolMovementPackets().move(session(sent), Position.builder()
				.x(1.5).y(64).z(-2.5)
				.yaw(90).pitch(-15)
				.onGround(false)
				.build());

		assertEquals(1, sent.size());
		ServerboundMovePlayerPosRotPacket packet = assertInstanceOf(ServerboundMovePlayerPosRotPacket.class, sent.getFirst());
		assertFalse(packet.isOnGround());
		assertEquals(1.5, packet.getX());
		assertEquals(64, packet.getY());
		assertEquals(-2.5, packet.getZ());
		assertEquals(90, packet.getYaw());
		assertEquals(-15, packet.getPitch());
	}

	@Test
	void isTheOnlyMovementPortOfItsRelease() {
		List<Class<?>> ports = ServiceLoader.load(MovementPackets.class).stream()
				.map(ServiceLoader.Provider::type)
				.<Class<?>>map(type -> type)
				.toList();

		assertEquals(List.of(McProtocolMovementPackets.class), ports);
		assertEquals(Session.class, new McProtocolMovementPackets().sessionType());
	}

	private static Session session(List<Object> sent) {
		return (Session) Proxy.newProxyInstance(Session.class.getClassLoader(), new Class<?>[]{Session.class},
				(proxy, method, arguments) -> {
					if (!method.getName().equals("send")) throw new UnsupportedOperationException(method.getName());

					sent.add(arguments[0]);
					return null;
				});
	}
}
