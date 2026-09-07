package me.whereareiam.anvil.provisioning.java;

import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.api.model.java.JavaArchive;
import me.whereareiam.anvil.api.model.java.JavaRequirement;
import me.whereareiam.anvil.api.model.java.JavaSource;
import me.whereareiam.anvil.provisioning.api.artifact.ArtifactLease;
import me.whereareiam.anvil.provisioning.api.artifact.ArtifactStore;
import me.whereareiam.anvil.provisioning.cache.ArtifactCache;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import me.whereareiam.anvil.provisioning.java.installation.JavaExecutables;

import static org.junit.jupiter.api.Assertions.*;

class JavaRuntimeResolverTest {
	@TempDir
	Path directory;

	@Test
	void validatesFeatureDistributionAndReleaseIndependentlyOfTheHostJvm() {
		try (var cache = new ArtifactCache(directory, true, false, 1)) {
			var java = new JavaRuntimeResolver(directory, cache, false, false);
			var selection = JavaRequirement.builder().featureVersion(21).distribution("temurin").release("21.0.7+6").build();
			String actual = "java.version = 21.0.7\njava.runtime.version = 21.0.7+6\njava.vendor = Eclipse Adoptium\njava.vm.name = OpenJDK 64-Bit Server VM\n";
			java.validate(java.inspect(actual, Path.of("test-java")), selection, 17);
			assertThrows(ProvisioningException.class, () -> java.validate(java.inspect(actual, Path.of("test-java")), selection, 25));
			assertThrows(ProvisioningException.class, () -> java.validate(java.inspect(actual, Path.of("test-java")), selection.toBuilder().featureVersion(25).build(), 17));
			assertThrows(ProvisioningException.class, () -> java.validate(java.inspect(actual, Path.of("test-java")), selection.toBuilder().distribution("graalvm-community").build(), 17));
			assertThrows(ProvisioningException.class, () -> java.validate(java.inspect(actual, Path.of("test-java")), selection.toBuilder().release("21.0.7+7").build(), 17));
			java.validate(java.inspect(actual.replace("Eclipse Adoptium", "GraalVM Community"), Path.of("test-java")),
					selection.toBuilder().distribution("graalvm-community").build(), 17);
		}
	}

	@Test
	void inspectsExplicitInstallationAndRejectsUnmetRequirementsWithoutDownloading() {
		try (var cache = new ArtifactCache(directory, true, false, 1)) {
			var java = new JavaRuntimeResolver(directory, cache, false, false);
			var selection = JavaRequirement.builder()
					.featureVersion(Runtime.version().feature()).build();
			assertEquals(JavaExecutables.current().toAbsolutePath(), java.resolve(selection, 21));
			assertThrows(ProvisioningException.class, () -> java.resolve(selection.toBuilder().featureVersion(99).build(), 21));
		}
	}

	@Test
	void acceptsAnExplicitExecutableSource() {
		try (var cache = new ArtifactCache(directory, true, false, 1)) {
			var java = new JavaRuntimeResolver(directory, cache, false, false);
			JavaRequirement selection = JavaRequirement.builder().featureVersion(Runtime.version().feature()).build();

			assertEquals(JavaExecutables.current().toAbsolutePath(),
					java.resolve(selection, selection.getFeatureVersion(), JavaSource.executable(JavaExecutables.current())));
		}
	}

	@Test
	void preservesExplicitZipArchiveTypeInTheArtifactCachePath() {
		AtomicReference<Path> destination = new AtomicReference<>();
		ArtifactStore artifacts = new ArtifactStore() {
			@Override
			public @NotNull Path obtain(@NotNull URI uri, @NotNull Path target, String expectedSha256) {
				destination.set(target);
				throw new IllegalStateException("stop after capturing the cache path");
			}

			@Override
			public @NotNull String read(@NotNull URI uri) {
				throw new UnsupportedOperationException();
			}

			@Override
			public @NotNull ArtifactLease lock(@NotNull Path entry) {
				return () -> { };
			}

			@Override
			public void close() { }
		};
		JavaRuntimeResolver java = new JavaRuntimeResolver(directory, artifacts, true, false);
		JavaArchive source = JavaArchive.builder()
				.uri(URI.create("https://example.test/jdk.zip"))
				.sha256("a".repeat(64))
				.build();

		assertThrows(IllegalStateException.class, () -> java.resolve(
				JavaRequirement.builder().featureVersion(21).build(),
				21,
				source
		));
		assertEquals("a".repeat(64) + ".zip", destination.get().getFileName().toString());
	}
}
