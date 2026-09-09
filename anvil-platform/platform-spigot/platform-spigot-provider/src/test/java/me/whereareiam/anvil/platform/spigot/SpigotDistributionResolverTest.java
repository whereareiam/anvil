package me.whereareiam.anvil.platform.spigot;

import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.platform.api.PlatformArtifactSource;
import me.whereareiam.anvil.platform.api.exception.PlatformException;
import me.whereareiam.anvil.platform.api.model.PlatformContext;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SpigotDistributionResolverTest {
	@TempDir
	Path temporary;

	@ParameterizedTest
	@ValueSource(strings = {"1.21.11", "26.1.2"})
	void downloadsTheContentPinnedSupplierArtifactWithoutRunningJava(String version) throws Exception {
		String checksum = "AB".repeat(32);
		var server = server(Distribution.pinned(version, checksum));
		PlatformArtifactSource artifacts = new PlatformArtifactSource() {
			@Override
			public @NotNull Path obtain(@NotNull URI uri, @NotNull Path destination, String expectedSha256) {
				assertEquals(URI.create("https://cdn.getbukkit.org/spigot/spigot-" + version + ".jar"), uri);
				assertEquals(checksum.toLowerCase(), expectedSha256);
				assertEquals(temporary.resolve("distributions/getbukkit/spigot").resolve(version)
						.resolve(expectedSha256).resolve("spigot-" + version + ".jar"), destination);
				return assertDoesNotThrow(() -> {
					Files.createDirectories(destination.getParent());
					return Files.writeString(destination, "test artifact");
				});
			}

			@Override
			public @NotNull String read(@NotNull URI uri) {
				throw new AssertionError("The supplier does not require a BuildTools metadata request");
			}
		};
		var result = new SpigotDistributionResolver().resolve(server, context(server, artifacts));
		assertTrue(Files.isRegularFile(result.getJar()));
		assertTrue(result.getDescription().startsWith("GetBukkit Spigot " + version));
	}

	@Test
	void rejectsUnpinnedAndLegacyBuildSelectorsBeforeDownloading() {
		PlatformArtifactSource unused = new PlatformArtifactSource() {
			@Override
			public @NotNull Path obtain(@NotNull URI uri, @NotNull Path destination, String expectedSha256) {
				throw new AssertionError("Invalid selectors must fail before download");
			}

			@Override
			public @NotNull String read(@NotNull URI uri) {
				throw new AssertionError("Invalid selectors must fail before metadata lookup");
			}
		};
		for (Distribution distribution : List.of(
				Distribution.remote("1.21.11", "200"),
				Distribution.remote("1.21.11", "latest"),
				Distribution.builder().version("1.21.11").build(),
				Distribution.pinned("1.21.11", "invalid"),
				Distribution.pinned("../other", "a".repeat(64)))) {
			MinecraftServer server = server(distribution);
			assertThrows(PlatformException.class, () -> new SpigotPlatformProvider().validateDistribution(server));
			assertThrows(PlatformException.class, () -> new SpigotDistributionResolver().resolve(server, context(server, unused)));
		}
	}

	private MinecraftServer server(Distribution distribution) {
		return MinecraftServer.builder().name("server").platform("spigot").distribution(distribution).build();
	}

	private PlatformContext context(MinecraftServer server, PlatformArtifactSource artifacts) {
		return PlatformContext.builder()
				.scenario(AnvilScenario.builder().name("supplier").entrypoint("server").server(server).build())
				.cacheDirectory(temporary).workDirectory(temporary.resolve("work"))
				.bindAddress("127.0.0.1").port(25565)

				.artifactSource(artifacts).build();
	}
}
