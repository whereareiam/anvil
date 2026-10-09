package me.whereareiam.anvil.platform.planning.version;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.type.SupportLevel;
import me.whereareiam.anvil.platform.api.exception.PlatformException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformVersionsTest {
	private static final PlatformVersionsReader READER = new PlatformVersionsReader();
	private static final PlatformVersions SAMPLE = READER.read("sample",
			PlatformVersionsTest.class.getResource("sample-versions.toml"));

	@TempDir
	Path directory;

	@ParameterizedTest
	@CsvSource({
			"1.16.5, 1.16.5",
			"1.17.1, 1.16.5",
			"1.18, 1.18",
			"1.19.4, 1.18",
			"1.20.4, 1.18",
			"1.20.5, 1.20.5",
			"26.1.2, 1.20.5"
	})
	void selectsTheRowWithTheGreatestStartNotNewerThanTheVersion(String version, String since) {
		JavaCompatibility row = SAMPLE.javaCompatibility(MinecraftVersion.parse(version)).orElseThrow();

		assertEquals(MinecraftVersion.parse(since), row.getSince());
	}

	@Test
	void readsRowsInVersionOrderWithOptionalMaximumBypassAndAgent() {
		JavaCompatibility legacy = SAMPLE.getJavaCompatibilities().getFirst();

		assertEquals(3, SAMPLE.getJavaCompatibilities().size());
		assertEquals(MinecraftVersion.parse("1.16.5"), SAMPLE.firstVersion());
		assertEquals(11, SAMPLE.getAgentMinimumJava());
		assertEquals(11, legacy.getMinimum());
		assertEquals(16, legacy.getMaximum());
		assertEquals(11, legacy.getPreferred());
		assertEquals("Sample.IgnoreJavaVersion", legacy.getMaximumBypassProperty());
		assertTrue(legacy.exceedsMaximum(17));
		assertFalse(legacy.exceedsMaximum(16));
		JavaCompatibility modern = SAMPLE.getJavaCompatibilities().getLast();
		assertNull(modern.getMaximum());
		assertNull(modern.getMaximumBypassProperty());
		assertFalse(modern.exceedsMaximum(29));
	}

	@Test
	void usesTheNewestRowForAProcessWithoutVersionAndNoRowBeforeTheFirst() {
		assertEquals(MinecraftVersion.parse("1.20.5"), SAMPLE.javaCompatibility(null).orElseThrow().getSince());
		assertEquals(Optional.empty(), SAMPLE.javaCompatibility(MinecraftVersion.parse("1.16.4")));
	}

	@Test
	void appliesAFirstRowWithoutStartToEveryVersion() throws IOException {
		PlatformVersions unversioned = read("""
				[[java]]
				minimum = 17
				preferred = 21
				""");

		assertNull(unversioned.firstVersion());
		assertNull(unversioned.getAgentMinimumJava());
		assertEquals(21, unversioned.javaCompatibility(null).orElseThrow().getPreferred());
		assertEquals(17, unversioned.javaCompatibility(MinecraftVersion.parse("1.8.8")).orElseThrow().getMinimum());
		assertEquals(SupportLevel.UNTESTED, unversioned.support(MinecraftVersion.parse("1.8.8")));
		assertEquals(Set.of(), unversioned.verifiedJava(null));
	}

	@ParameterizedTest
	@CsvSource({
			"1.16.5, VERIFIED",
			"1.20.6, VERIFIED",
			"1.17.1, COMPATIBLE",
			"1.20.4, COMPATIBLE",
			"1.19.2, UNTESTED",
			"26.2, UNTESTED",
			"1.16.4, UNSUPPORTED",
			"1.12.2, UNSUPPORTED"
	})
	void assessesThePlatformVersionItself(String version, SupportLevel expected) {
		assertEquals(expected, SAMPLE.support(MinecraftVersion.parse(version)));
	}

	@Test
	void reportsVerifiedJavaAndTheNewestKnownVersion() {
		assertEquals(Set.of(11, 17), SAMPLE.verifiedJava(MinecraftVersion.parse("1.16.5")));
		assertEquals(Set.of(), SAMPLE.verifiedJava(MinecraftVersion.parse("1.17.1")));
		assertEquals(Set.of(), SAMPLE.verifiedJava(null));
		assertEquals(MinecraftVersion.parse("1.20.6"), SAMPLE.newestKnownVersion().orElseThrow());
	}

	@ParameterizedTest
	@CsvSource({
			"8, false", "11, true", "16, false", "17, true", "21, true", "22, false",
			"25, true", "26, false", "29, true", "33, true", "7, false"
	})
	void recognisesTheLongTermSupportReleasesAnvilRuns(int featureVersion, boolean expected) {
		assertEquals(expected, JavaCompatibility.isLongTermSupport(featureVersion));
	}

	@ParameterizedTest
	@CsvSource({"8, 11", "11, 11", "12, 17", "17, 17", "18, 21", "21, 21", "22, 25", "25, 25", "26, 29"})
	void raisesAJavaVersionToTheNextLongTermSupportRelease(int featureVersion, int expected) {
		assertEquals(expected, JavaCompatibility.nextLongTermSupport(featureVersion));
	}

	@Test
	void rejectsInconsistentRows() {
		assertInvalid(row("1.16.5", 11, 16, 16, null), "not an LTS release");
		assertInvalid(row("1.16.5", 8, null, 8, null), "not an LTS release");
		assertInvalid(row("1.16.5", 17, null, 11, null), "below its minimum");
		assertInvalid(row("1.16.5", 11, 16, 17, null), "above its maximum");
		assertInvalid(row("1.16.5", 17, null, 17, "Sample.IgnoreJavaVersion"), "without a maximum");
		IllegalArgumentException duplicate = assertThrows(IllegalArgumentException.class, () -> PlatformVersions.builder()
				.javaCompatibility(row("1.18", 17, null, 17, null))
				.javaCompatibility(row("1.18.0", 17, null, 21, null))
				.build());
		assertTrue(duplicate.getMessage().contains("Duplicate"), duplicate.getMessage());
		IllegalArgumentException startless = assertThrows(IllegalArgumentException.class, () -> PlatformVersions.builder()
				.javaCompatibility(row(null, 17, null, 17, null))
				.javaCompatibility(row(null, 21, null, 21, null))
				.build());
		assertTrue(startless.getMessage().contains("Only the first Java row may omit 'since'"), startless.getMessage());
		assertThrows(IllegalArgumentException.class, () -> PlatformVersions.builder().build());
	}

	@Test
	void rejectsVerifiedAndKnownVersionsOutsideTheRowsOrBelowTheAgent() {
		JavaCompatibility row = row("1.18", 17, 18, 17, null);

		assertThrows(IllegalArgumentException.class, () -> PlatformVersions.builder().javaCompatibility(row)
				.knownVersion(MinecraftVersion.parse("1.17.1")).build());
		assertThrows(IllegalArgumentException.class, () -> PlatformVersions.builder().javaCompatibility(row)
				.verifiedJavaVersion(MinecraftVersion.parse("1.18.2"), Set.of(21)).build());
		assertThrows(IllegalArgumentException.class, () -> PlatformVersions.builder().javaCompatibility(row)
				.verifiedJavaVersion(MinecraftVersion.parse("1.18.2"), Set.of(11)).build());
		assertThrows(IllegalArgumentException.class, () -> PlatformVersions.builder().javaCompatibility(row)
				.verifiedJavaVersion(MinecraftVersion.parse("1.18.2"), Set.of(16)).build());
		IllegalArgumentException agent = assertThrows(IllegalArgumentException.class, () -> PlatformVersions.builder()
				.javaCompatibility(row("1.18", 17, null, 17, null))
				.agentMinimumJava(21)
				.verifiedJavaVersion(MinecraftVersion.parse("1.18.2"), Set.of(17))
				.build());
		assertTrue(agent.getMessage().contains("below the agent's minimum Java 21"), agent.getMessage());
		assertThrows(IllegalArgumentException.class, () -> PlatformVersions.builder()
				.javaCompatibility(row).agentMinimumJava(0).build());
	}

	@Test
	void reportsMissingMalformedAndMisspeltDataAsPlatformFailures() throws IOException {
		PlatformException missing = assertThrows(PlatformException.class, () -> READER.read("sample", null));
		assertEquals("Platform 'sample' supplies no version data", missing.getMessage());
		PlatformException invalid = assertThrows(PlatformException.class,
				() -> READER.read("sample", PlatformVersionsTest.class.getResource("invalid-versions.toml")));
		assertTrue(invalid.getMessage().startsWith("Platform 'sample' has invalid version data "), invalid.getMessage());
		assertTrue(invalid.getMessage().contains("java.minimum"), invalid.getMessage());
		PlatformException misspelt = assertThrows(PlatformException.class, () -> read("""
				[agent]
				minimumjava = 11

				[[java]]
				minimum = 17
				preferred = 17
				"""));
		assertTrue(misspelt.getMessage().contains("unknown key 'minimumjava' in [agent]"), misspelt.getMessage());
		PlatformException row = assertThrows(PlatformException.class, () -> read("""
				[[java]]
				since = "1.18"
				minimum = 17
				prefered = 17
				"""));
		assertTrue(row.getMessage().contains("unknown key 'prefered' in a [[java]] row"), row.getMessage());
	}

	private PlatformVersions read(String document) throws IOException {
		Path file = Files.writeString(directory.resolve("versions.toml"), document);
		URL resource = file.toUri().toURL();

		return READER.read("sample", resource);
	}

	private static void assertInvalid(JavaCompatibility row, String message) {
		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
				() -> PlatformVersions.builder().javaCompatibility(row).build());
		assertTrue(failure.getMessage().contains(message), failure.getMessage());
	}

	private static JavaCompatibility row(String since, int minimum, Integer maximum, int preferred, String bypass) {
		return JavaCompatibility.builder()
				.since(since == null ? null : MinecraftVersion.parse(since))
				.minimum(minimum)
				.maximum(maximum)
				.preferred(preferred)
				.maximumBypassProperty(bypass)
				.build();
	}
}
