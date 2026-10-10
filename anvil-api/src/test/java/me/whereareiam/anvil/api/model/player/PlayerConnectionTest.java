package me.whereareiam.anvil.api.model.player;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayerConnectionTest {
	@Test
	void defaultsToTheEntrypointAtItsRealAddress() {
		PlayerConnection connection = PlayerOptions.builder().name("alice").build().getConnection();

		assertSame(PlayerConnection.entrypoint(), connection);
		assertNull(connection.getTarget());
		assertNull(connection.getVirtualHost());
		assertNull(connection.getSourceAddress());
		assertEquals(connection, PlayerConnection.builder().build());
	}

	@Test
	void keepsDeclaredValues() {
		PlayerConnection connection = PlayerConnection.to("proxy").toBuilder()
				.virtualHost("lobby.example.test")
				.sourceAddress("127.0.0.2")
				.build();

		assertEquals("proxy", connection.getTarget());
		assertEquals("lobby.example.test", connection.getVirtualHost());
		assertEquals("127.0.0.2", connection.getSourceAddress());
		assertEquals("::1", PlayerConnection.builder().sourceAddress("::1").build().getSourceAddress());
	}

	@Test
	void refusesBlankTargetsAndVirtualHosts() {
		assertThrows(IllegalArgumentException.class, () -> PlayerConnection.to(" "));
		assertThrows(IllegalArgumentException.class, () -> PlayerConnection.builder().virtualHost("").build());
		assertThrows(IllegalArgumentException.class, () -> PlayerConnection.builder().virtualHost("lobby example").build());
		assertThrows(IllegalArgumentException.class, () -> PlayerConnection.builder().virtualHost("a".repeat(256)).build());
	}

	@Test
	void acceptsOnlyLoopbackAddressLiteralsAsSource() {
		IllegalArgumentException name = assertThrows(IllegalArgumentException.class,
				() -> PlayerConnection.builder().sourceAddress("localhost").build());
		assertEquals("A player connection's source address 'localhost' must be an IP address literal such as 127.0.0.2",
				name.getMessage());
		assertThrows(IllegalArgumentException.class, () -> PlayerConnection.builder().sourceAddress("192.168.1.20").build());
		assertThrows(IllegalArgumentException.class, () -> PlayerConnection.builder().sourceAddress("127.0.0.300").build());
		assertThrows(IllegalArgumentException.class, () -> PlayerConnection.builder().sourceAddress("fe80::zz").build());
	}
}
