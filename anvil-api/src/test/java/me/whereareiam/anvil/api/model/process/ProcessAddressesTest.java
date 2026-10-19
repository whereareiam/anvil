package me.whereareiam.anvil.api.model.process;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProcessAddressesTest {
	private final ProcessAddresses addresses = ProcessAddresses.of(
			Map.of(
					"proxy", new InetSocketAddress("127.0.0.1", 21000),
					"lobby", new InetSocketAddress("127.0.0.1", 21001)
			),
			Map.of("proxy", Map.of("link", new InetSocketAddress("127.0.0.1", 21002)))
	);

	@Test
	void fillsTheHostAGamePortAndAListenerOfAProcess() {
		assertEquals("127.0.0.1:21002 game 21001",
				addresses.resolve("${anvil.process.proxy.host}:${anvil.process.proxy.listener.link} game ${anvil.process.lobby.port}"));
	}

	@Test
	void fillsTheHostPortOfAContainersPortByItsNumber() {
		var containers = ProcessAddresses.of(
				Map.of("database", new InetSocketAddress("127.0.0.1", 21010)),
				Map.of("database", Map.of(
						"5432", new InetSocketAddress("127.0.0.1", 21010),
						"8080", new InetSocketAddress("127.0.0.1", 21011)
				))
		);

		assertEquals("21010 21011", containers.resolve("${anvil.process.database.port} ${anvil.process.database.port.8080}"));
	}

	@Test
	void returnsATextWithoutPlaceholdersAsItIs() {
		String text = "port: 25565";
		assertSame(text, addresses.resolve(text));
	}

	@Test
	void refusesAnUnknownProcessOrListener() {
		var unknownProcess = assertThrows(IllegalArgumentException.class, () -> addresses.resolve("${anvil.process.survival.host}"));
		assertEquals("No process 'survival' of the same lifetime runs in the scenario", unknownProcess.getMessage());
		var unknownListener = assertThrows(IllegalArgumentException.class, () -> addresses.resolve("${anvil.process.lobby.listener.link}"));
		assertEquals("Process 'lobby' declares no listener 'link'", unknownListener.getMessage());
	}
}
