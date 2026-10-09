package me.whereareiam.anvil.protocol.mcprotocol.catalog;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.protocol.mcprotocol.model.ReleaseArtifact;
import me.whereareiam.anvil.protocol.mcprotocol.model.ReleaseDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McProtocolReleaseReaderTest {
	@Test
	void readsReleasesWithTheirClosureInOrder() {
		String native_ = artifact("demo:transport:1.0:linux-x86_64", "https://repository.example/transport-1.0-linux-x86_64.jar", "");
		List<ReleaseDefinition> releases = read(release("1.16.5-2", "\"1.16.4\", \"1.16.5\"", "1.16.5") + native_);

		ReleaseDefinition definition = releases.getFirst();
		assertEquals("demo:protocol:1.16.5-2", definition.getModule());
		assertEquals(List.of(MinecraftVersion.parse("1.16.4"), MinecraftVersion.parse("1.16.5")), definition.getRelease().getMinecraftVersions());
		assertEquals(Set.of(MinecraftVersion.parse("1.16.5")), definition.getRelease().getVerifiedVersions());
		assertEquals(754, definition.getRelease().getProtocolNumber());
		assertEquals(17, definition.getRelease().getJavaVersion());
		assertEquals(List.of("demo:protocol:1.16.5-2", "demo:transport:1.0:linux-x86_64"),
				definition.getArtifacts().stream().map(ReleaseArtifact::getModule).toList());

		ReleaseArtifact jar = definition.getArtifacts().getFirst();
		assertEquals(URI.create("https://repository.example/protocol-1.16.5-2.jar"), jar.getUrl());
		assertEquals("protocol-1.16.5-2.jar", jar.fileName());
		assertTrue(jar.pinned());
		assertFalse(definition.getArtifacts().getLast().pinned());
	}

	@Test
	void readsReleasesWithoutFeatures() {
		String text = release("1.18.2-1", "\"1.18.2\"", "1.18.2").replace("features = [\"ONLINE_AUTHENTICATION\"]\n", "");

		assertTrue(read(text).getFirst().getRelease().getFeatures().isEmpty());
	}

	@ParameterizedTest(name = "{0}")
	@CsvSource(delimiter = '|', value = {
			"duplicate version | declares release 1.18.2-1 more than once",
			"shared Minecraft version | assigns Minecraft 1.18.2 to more than one release",
			"unlisted verified version | verifies Minecraft 1.18.1, which it does not list",
			"missing closure | must declare its runtime closure",
			"closure without module | must include its module demo:protocol:1.18.2-1",
			"malformed checksum | must have an empty or 64-digit lower-case hex sha256",
			"classifier on the release module | must be a group:name:version coordinate",
			"unknown field | has unknown fields [versoin]",
			"unknown feature | declares unknown feature FLYING",
			"old Java | must declare java as an integer of at least 8",
			"relative url | must be an http(s) URL of a file",
			"duplicate file name | lists the file protocol-1.18.2-1.jar more than once",
			"pre-release version | Not a Minecraft release version",
			"no release | must declare at least one [[release]]",
			"invalid TOML | is not valid TOML"
	})
	void rejectsInvalidReleaseData(String violation, String message) {
		String release = release("1.18.2-1", "\"1.18.2\"", "1.18.2");
		String text = switch (violation) {
			case "duplicate version" -> release + release.replace("\"1.18.2\"]", "\"1.18.1\"]").replace("verified = [\"1.18.1\"]", "verified = []");
			case "shared Minecraft version" -> release + release.replace("version = \"1.18.2-1\"", "version = \"1.18.2-2\"");
			case "unlisted verified version" -> release.replace("verified = [\"1.18.2\"]", "verified = [\"1.18.1\"]");
			case "missing closure" -> release.substring(0, release.indexOf("[[release.artifact]]"));
			case "closure without module" -> release.replace("module = \"demo:protocol:1.18.2-1\"\nurl", "module = \"demo:other:1.0\"\nurl");
			case "malformed checksum" -> release.replace("a".repeat(64), "ABC");
			case "classifier on the release module" -> release.replace("module = \"demo:protocol:1.18.2-1\"\nprotocol", "module = \"demo:protocol:1.18.2-1:all\"\nprotocol");
			case "unknown field" -> release + "versoin = 1\n";
			case "unknown feature" -> release.replace("\"ONLINE_AUTHENTICATION\"", "\"FLYING\"");
			case "old Java" -> release.replace("java = 17", "java = 7");
			case "relative url" -> release.replace("https://repository.example/protocol-1.18.2-1.jar", "protocol-1.18.2-1.jar");
			case "duplicate file name" -> release + artifact("demo:copy:1.0", "https://mirror.example/protocol-1.18.2-1.jar", "");
			case "pre-release version" -> release.replace("\"1.18.2\"]", "\"1.19-pre1\"]").replace("verified = [\"1.19-pre1\"]", "verified = []");
			case "no release" -> "";
			case "invalid TOML" -> "[[release]\n";
			default -> throw new IllegalArgumentException(violation);
		};

		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> read(text));
		assertTrue(failure.getMessage().startsWith("test.toml"), failure.getMessage());
		assertTrue(failure.getMessage().contains(message), failure.getMessage());
	}

	private List<ReleaseDefinition> read(String text) {
		return new McProtocolReleaseReader().read(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), "test.toml", false);
	}

	private String release(String version, String minecraft, String verified) {
		return """
				[[release]]
				version = "%s"
				module = "demo:protocol:%s"
				protocol = 754
				minecraft = [%s]
				verified = ["%s"]
				java = 17
				features = ["ONLINE_AUTHENTICATION"]

				""".formatted(version, version, minecraft, verified)
				+ artifact("demo:protocol:" + version, "https://repository.example/protocol-" + version + ".jar", "a".repeat(64));
	}

	private String artifact(String module, String url, String sha256) {
		return """
				[[release.artifact]]
				module = "%s"
				url = "%s"
				sha256 = "%s"

				""".formatted(module, url, sha256);
	}
}
