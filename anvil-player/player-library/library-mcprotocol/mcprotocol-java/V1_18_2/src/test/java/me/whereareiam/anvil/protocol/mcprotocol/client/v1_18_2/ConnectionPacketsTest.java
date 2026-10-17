package me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2;

import com.github.steveice10.mc.protocol.data.game.entity.player.GameMode;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundKeepAlivePacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundRespawn;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConnectionPacketsTest {
	private final ConnectionPackets packets = new ConnectionPackets();

	@Test
	void decodesJoiningALevelAndRespawning() throws Exception {
		assertEquals(new ClientboundLogin(42), packets.decode(new ClientboundLoginPacket(42, false, GameMode.SURVIVAL,
				GameMode.SURVIVAL, 1, new String[0], new CompoundTag(""), new CompoundTag(""), "minecraft:overworld", 0, 20, 10,
				10, false, true, false, false)));
		assertEquals(new ClientboundRespawn(), packets.decode(allocate(ClientboundRespawnPacket.class)));
	}

	@Test
	void leavesPacketsOfOtherAreasToOtherTranslations() {
		assertNull(packets.decode(new ClientboundKeepAlivePacket(7)));
		assertNull(packets.encode(new UnknownPacket()));
	}

	@Test
	void carriesJoiningALevelAndRespawningButNoConfigurationPhase() {
		assertEquals(Set.of(ClientboundLogin.class, ClientboundRespawn.class), packets.packets());
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
