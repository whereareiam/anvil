package me.whereareiam.anvil.api.model.service;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServiceAddressesTest {
	private final ServiceAddresses addresses = ServiceAddresses.of(Map.of(
			"redis", Map.of(6379, InetSocketAddress.createUnresolved("redis", 6379)),
			"database", Map.of(
					5432, new InetSocketAddress("127.0.0.1", 21000),
					8080, new InetSocketAddress("127.0.0.1", 21001)
			)
	));

	@Test
	void fillsTheHostAndPortOfAServiceWithOnePort() {
		assertEquals("redis://redis:6379", addresses.resolve("redis://${anvil.service.redis.host}:${anvil.service.redis.port}"));
	}

	@Test
	void fillsTheHostPortThatReachesANamedContainerPort() {
		assertEquals("127.0.0.1:21001", addresses.resolve("${anvil.service.database.host}:${anvil.service.database.port.8080}"));
	}

	@Test
	void returnsATextWithoutPlaceholdersAsItIs() {
		String text = "host: localhost";
		assertSame(text, addresses.resolve(text));
	}

	@Test
	void refusesAPortWithoutNumberForAServiceWithSeveral() {
		var failure = assertThrows(IllegalArgumentException.class, () -> addresses.resolve("${anvil.service.database.port}"));
		assertEquals("${anvil.service.database.port} needs a port: service 'database' listens on [5432, 8080]",
				failure.getMessage());
	}

	@Test
	void refusesAnUnknownServiceOrPort() {
		assertThrows(IllegalArgumentException.class, () -> addresses.resolve("${anvil.service.missing.host}"));
		assertThrows(IllegalArgumentException.class, () -> addresses.resolve("${anvil.service.redis.port.6380}"));
		assertThrows(IllegalArgumentException.class, () -> ServiceAddresses.NONE.resolve("${anvil.service.redis.host}"));
	}
}
