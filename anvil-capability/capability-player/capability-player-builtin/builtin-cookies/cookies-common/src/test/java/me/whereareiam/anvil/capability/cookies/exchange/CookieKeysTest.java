package me.whereareiam.anvil.capability.cookies.exchange;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CookieKeysTest {
	@Test
	void givesAKeyWithoutANamespaceTheMinecraftNamespace() {
		assertEquals("minecraft:ticket", CookieKeys.normalize("ticket"));
		assertEquals("example:ticket", CookieKeys.normalize("example:ticket"));
		assertEquals("example.net:tickets/day-1_a", CookieKeys.normalize("example.net:tickets/day-1_a"));
	}

	@Test
	void refusesWhatIsNotAResourceLocation() {
		assertThrows(IllegalArgumentException.class, () -> CookieKeys.normalize(""));
		assertThrows(IllegalArgumentException.class, () -> CookieKeys.normalize("example:"));
		assertThrows(IllegalArgumentException.class, () -> CookieKeys.normalize(":ticket"));
		assertThrows(IllegalArgumentException.class, () -> CookieKeys.normalize("Example:ticket"));
		assertThrows(IllegalArgumentException.class, () -> CookieKeys.normalize("example:a:b"));
		assertThrows(IllegalArgumentException.class, () -> CookieKeys.normalize("example/ns:ticket"));
	}
}
