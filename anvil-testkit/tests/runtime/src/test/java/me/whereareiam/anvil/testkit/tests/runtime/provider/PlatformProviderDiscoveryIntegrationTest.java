package me.whereareiam.anvil.testkit.tests.runtime.provider;

import me.whereareiam.anvil.api.type.Platforms;
import me.whereareiam.anvil.platform.api.PlatformProvider;
import org.junit.jupiter.api.Test;

import java.util.ServiceLoader;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlatformProviderDiscoveryIntegrationTest {
	@Test
	void discoversEveryBundledPlatformProvider() {
		Set<String> platforms = ServiceLoader.load(PlatformProvider.class).stream()
				.map(ServiceLoader.Provider::get)
				.map(PlatformProvider::id)
				.collect(Collectors.toSet());

		assertEquals(Set.of(
				Platforms.PAPER,
				Platforms.SPIGOT,
				Platforms.VELOCITY,
				Platforms.BUNGEECORD
		), platforms);
	}
}
