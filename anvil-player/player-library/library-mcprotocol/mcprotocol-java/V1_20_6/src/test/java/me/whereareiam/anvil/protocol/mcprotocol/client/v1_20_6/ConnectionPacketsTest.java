package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundStartConfiguration;
import net.kyori.adventure.key.Key;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundKeepAlivePacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundStartConfigurationPacket;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConnectionPacketsTest {
	private final ConnectionPackets packets = new ConnectionPackets();

	@Test
	void decodesJoiningALevelRespawningAndReturningToConfiguration() throws Exception {
		assertEquals(new ClientboundLogin(42), packets.decode(new ClientboundLoginPacket(42, false, new Key[0], 20, 10, 10,
				false, true, false, null, false)));
		assertEquals(new ClientboundRespawn(), packets.decode(allocate(ClientboundRespawnPacket.class)));
		assertEquals(new ClientboundStartConfiguration(), packets.decode(new ClientboundStartConfigurationPacket()));
	}

	@Test
	void leavesPacketsOfOtherAreasToOtherTranslations() {
		assertNull(packets.decode(new ClientboundKeepAlivePacket(7)));
		assertNull(packets.encode(new UnknownPacket()));
	}

	@Test
	void carriesEveryConnectionPacket() {
		assertEquals(Set.of(ClientboundLogin.class, ClientboundRespawn.class, ClientboundStartConfiguration.class),
				packets.packets());
	}

	/**
	 * Creates a packet without running its constructor, whose arguments describe a whole level.
	 */
	private static <T> T allocate(Class<T> type) throws ReflectiveOperationException {
		Class<?> unsafe = Class.forName("sun.misc.Unsafe");
		Field instance = unsafe.getDeclaredField("theUnsafe");
		instance.setAccessible(true);
		return type.cast(unsafe.getMethod("allocateInstance", Class.class).invoke(instance.get(null), type));
	}

	private static final class UnknownPacket implements ServerboundPacket {
	}
}
