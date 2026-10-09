package me.whereareiam.anvil.api.model;

import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftVersionTest {
	@Test
	void ordersBothNumberingSchemesComponentByComponent() {
		List<String> ordered = List.of("1.16.5", "1.17.1", "1.18.2", "1.19.4", "1.20.6", "1.21.11", "26.1", "26.1.2", "26.2");
		for (int index = 1; index < ordered.size(); index++) {
			MinecraftVersion older = MinecraftVersion.parse(ordered.get(index - 1));
			MinecraftVersion newer = MinecraftVersion.parse(ordered.get(index));
			assertTrue(older.compareTo(newer) < 0, older + " < " + newer);
			assertTrue(newer.isAtLeast(older));
			assertFalse(older.isAtLeast(newer));
		}
	}

	@Test
	void normalisesTrailingZerosAndPrintsCanonicalForm() {
		assertEquals(MinecraftVersion.parse("1.20"), MinecraftVersion.parse("1.20.0"));
		assertEquals("1.20", MinecraftVersion.parse("1.20.0").toString());
		assertEquals("26.1.2", MinecraftVersion.parse("26.1.2").toString());
	}

	@Test
	void rejectsNonReleaseVersions() {
		for (String text : List.of("1", "latest", "26.1-pre1", "1.21.11-rc1", "25w14a", "1.2.3.4", ""))
			assertThrows(IllegalArgumentException.class, () -> MinecraftVersion.parse(text), text);
	}

	@Test
	void floorSelectsTheNewestStartNotAfterTheTarget() {
		List<String> starts = List.of("1.16.5", "1.18.2", "1.20.6", "1.21.11");

		assertEquals("1.18.2", floor(starts, "1.19.4"));
		assertEquals("1.20.6", floor(starts, "1.20.6"));
		assertEquals("1.21.11", floor(starts, "26.1.2"));
		assertNull(floor(starts, "1.16.4"));
	}

	@Test
	void serversDeriveTheirNativeVersionFromTheDistributionOrDeclaration() {
		MinecraftServer remote = MinecraftServer.builder()
				.name("remote")
				.platform("paper")
				.distribution(Distribution.remote("1.20.6", "151"))
				.build();
		MinecraftServer local = remote.toBuilder()
				.distribution(Distribution.local(Path.of("server.jar")))
				.minecraftVersion("1.16.5")
				.build();
		MinecraftServer undeclared = local.toBuilder().minecraftVersion(null).build();

		assertEquals(MinecraftVersion.parse("1.20.6"), remote.nativeVersion());
		assertEquals(MinecraftVersion.parse("1.16.5"), local.nativeVersion());
		assertNull(undeclared.nativeVersion());
	}

	private static String floor(List<String> starts, String target) {
		return MinecraftVersion.floor(starts, MinecraftVersion::parse, MinecraftVersion.parse(target)).orElse(null);
	}
}
