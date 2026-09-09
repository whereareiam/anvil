package me.whereareiam.anvil.environment.provisioning.java;

import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.api.model.java.JavaArchive;
import me.whereareiam.anvil.api.model.java.JavaRequirement;
import me.whereareiam.anvil.api.model.java.JavaSource;
import me.whereareiam.anvil.environment.cache.api.CacheEntry;
import me.whereareiam.anvil.environment.cache.api.exception.CacheException;
import me.whereareiam.anvil.environment.cache.filesystem.FileCache;
import me.whereareiam.anvil.environment.provisioning.java.api.installatiion.JavaInstallationStorage;
import me.whereareiam.anvil.environment.provisioning.java.api.JavaPackageSource;
import me.whereareiam.anvil.environment.provisioning.java.installation.JavaExecutables;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class JavaRuntimeResolverTest {
	@TempDir
	Path directory;

	@Test
	void validatesFeatureDistributionAndReleaseIndependentlyOfTheHostJvm() {
		var java = resolver();
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

	@Test
	void inspectsExplicitInstallationAndRejectsUnmetRequirementsWithoutDownloading() {
		var java = resolver();
		var selection = JavaRequirement.builder()
				.featureVersion(Runtime.version().feature()).build();
		assertEquals(JavaExecutables.current().toAbsolutePath(), java.resolve(selection, 21));
		assertThrows(ProvisioningException.class, () -> java.resolve(selection.toBuilder().featureVersion(99).build(), 21));
	}

	@Test
	void acceptsAnExplicitExecutableSource() {
		var java = resolver();
		JavaRequirement selection = JavaRequirement.builder().featureVersion(Runtime.version().feature()).build();

		assertEquals(JavaExecutables.current().toAbsolutePath(),
				java.resolve(selection, selection.getFeatureVersion(), JavaSource.executable(JavaExecutables.current())));
	}

	@Test
	void preservesExplicitZipArchiveTypeInTheArtifactCachePath() {
		AtomicReference<Path> destination = new AtomicReference<>();
		JavaPackageSource packages = new JavaPackageSource() {
			@Override
			public @NotNull Path archive(@NotNull JavaArchive archive, @NotNull Path target) {
				destination.set(target);
				throw new IllegalStateException("stop after capturing the cache path");
			}

			@Override
			public @NotNull String catalog(@NotNull URI uri) {
				throw new UnsupportedOperationException();
			}

		};
		JavaRuntimeResolver java = new JavaRuntimeResolver(directory, packages, storage(), true, false);
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
	private JavaRuntimeResolver resolver() {
		JavaPackageSource packages = new JavaPackageSource() {
			@Override
			public @NotNull String catalog(@NotNull URI uri) {
				throw new AssertionError("Local inspection must not request catalog documents");
			}

			@Override
			public @NotNull Path archive(@NotNull JavaArchive archive, @NotNull Path destination) {
				throw new AssertionError("Local inspection must not request Java archives");
			}
		};
		return new JavaRuntimeResolver(directory, packages, storage(), false, false);
	}

	private JavaInstallationStorage storage() {
		FileCache cache = new FileCache(directory);
		return installation -> {
			try {
				CacheEntry entry = cache.open(installation);
				return () -> {
					try {
						entry.close();
					} catch (CacheException failure) {
						throw new IOException("Could not release installation", failure);
					}
				};
			} catch (CacheException failure) {
				throw new IOException("Could not access installation", failure);
			}
		};
	}

}
