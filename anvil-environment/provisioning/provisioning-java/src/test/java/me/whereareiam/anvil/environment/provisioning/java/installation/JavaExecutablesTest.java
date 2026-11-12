package me.whereareiam.anvil.environment.provisioning.java.installation;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JavaExecutablesTest {
	@Test
	void executableNamesFollowTheHostPlatform() {
		Path home = Path.of("jdk");
		assertEquals(home.resolve("bin/java.exe"), JavaExecutables.atHome(home, "Windows 11"));
		assertEquals(home.resolve("bin/java"), JavaExecutables.atHome(home, "Linux"));
		assertEquals(home.resolve("bin/java"), JavaExecutables.atHome(home, "Darwin"));
	}
}
