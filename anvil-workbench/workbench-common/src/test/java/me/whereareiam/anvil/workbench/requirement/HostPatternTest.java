package me.whereareiam.anvil.workbench.requirement;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HostPatternTest {
	@Test
	void allowsEveryHostThroughAnAsterisk() {
		assertTrue(HostPattern.matches("*", "api.mojang.com"));
	}

	@Test
	void allowsSubdomainsButNotTheDomainItself() {
		assertTrue(HostPattern.matches("*.mojang.com", "sessionserver.mojang.com"));
		assertTrue(HostPattern.matches("*.mojang.com", "a.b.mojang.com"));
		assertFalse(HostPattern.matches("*.mojang.com", "mojang.com"));
		assertFalse(HostPattern.matches("*.mojang.com", "evilmojang.com"));
	}

	@Test
	void comparesExactHostsWithoutRegardToLetterCase() {
		assertTrue(HostPattern.matches("repo.papermc.io", "Repo.PaperMC.io"));
		assertFalse(HostPattern.matches("repo.papermc.io", "papermc.io"));
	}
}
