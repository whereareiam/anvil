package me.whereareiam.anvil.integration.intellij.scenario.editor.inspection;

import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlatformVersionsTest {
	@Test void readsKnownAndVerifiedVersions() {
		var versions = PlatformVersions.parse("""
				# known: versions the provider publishes builds for
				known = [
					"1.16.5",
					"1.21", "1.21.1", # trailing comment
				]

				[agent]
				minimumJava = 11

				[verified]
				"1.21.1" = [21]
				"26.1.2" = [25]

				[[java]]
				since = "1.16.5"
				""");

		assertEquals(Set.of("1.16.5", "1.21", "1.21.1", "26.1.2"), versions);
	}

	@Test void hasNoDataWithoutVersions() {
		assertNull(PlatformVersions.parse("[agent]\nminimumJava = 21\n\n[[java]]\nminimum = 17\n"));
	}
}
