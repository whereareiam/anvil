package me.whereareiam.anvil.environment.provisioning.java;

import me.whereareiam.anvil.api.exception.JavaVersionMismatchException;
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
	void validatesExactFeatureDistributionAndReleaseIndependentlyOfTheHostJvm() {
		var java = resolver();
		var selection = JavaRequirement.builder().featureVersion(21).distribution("temurin").release("21.0.7+6").build();
		String actual = "java.version = 21.0.7\njava.runtime.version = 21.0.7+6\njava.vendor = Eclipse Adoptium\njava.vm.name = OpenJDK 64-Bit Server VM\n";
		java.validate(java.inspect(actual, Path.of("test-java")), selection);
		assertThrows(ProvisioningException.class, () -> java.validate(java.inspect(actual, Path.of("test-java")), selection.toBuilder().featureVersion(17).build()));
		assertThrows(ProvisioningException.class, () -> java.validate(java.inspect(actual, Path.of("test-java")), selection.toBuilder().featureVersion(25).build()));
		assertThrows(ProvisioningException.class, () -> java.validate(java.inspect(actual, Path.of("test-java")), selection.toBuilder().distribution("graalvm-community").build()));
		assertThrows(ProvisioningException.class, () -> java.validate(java.inspect(actual, Path.of("test-java")), selection.toBuilder().release("21.0.7+7").build()));
		java.validate(java.inspect(actual.replace("Eclipse Adoptium", "GraalVM Community"), Path.of("test-java")),
				selection.toBuilder().distribution("graalvm-community").build());
	}

	@Test
	void explainsAFeatureMismatchWithTheExecutableAndNoSourceSpecificRemedy() {
		var java = resolver();
		String actual = "java.version = 25.0.1\njava.runtime.version = 25.0.1+8\njava.vendor = Eclipse Adoptium\njava.vm.name = OpenJDK 64-Bit Server VM\n";

		var failure = assertThrows(JavaVersionMismatchException.class, () -> java.validate(java.inspect(actual, Path.of("jdk-25/bin/java")),
				JavaRequirement.builder().featureVersion(21).build()));
		assertTrue(failure.getMessage().contains("jdk-25"), failure.getMessage());
		assertTrue(failure.getMessage().endsWith("is Java 25.0.1, but the process requires exactly Java 21"), failure.getMessage());
	}

	@Test
	void namesTheDeclaredSourceAsTheRemedyForAnExplicitJavaOfAnotherVersion() {
		var java = resolver();
		int other = Runtime.version().feature() == 21 ? 25 : 21;
		JavaRequirement selection = JavaRequirement.builder().featureVersion(other).build();

		var failure = assertThrows(ProvisioningException.class,
				() -> java.resolve(selection, JavaSource.executable(JavaExecutables.current())));

		assertTrue(failure.getMessage().contains("but the process requires exactly Java " + other), failure.getMessage());
		assertTrue(failure.getMessage().endsWith("; point the Java source at Java " + other + ", or remove it so Anvil uses JAVA_"
				+ other + "_HOME, the cache or a download"), failure.getMessage());
	}

	@Test
	void namesNoVersionRemedyWhenTheDeclaredSourceCannotBeInspected() {
		var java = resolver();
		JavaRequirement selection = JavaRequirement.builder().featureVersion(Runtime.version().feature()).build();
		Path missing = directory.resolve("missing/bin/java");

		var failure = assertThrows(ProvisioningException.class, () -> java.resolve(selection, JavaSource.executable(missing)));

		assertEquals("Could not inspect Java: " + missing, failure.getMessage());
	}

	@Test
	void rejectsUnplannedRequirementsAndUnreadableVersions() {
		var java = resolver();
		var unplanned = assertThrows(ProvisioningException.class, () -> java.resolve(JavaRequirement.builder().build(), null));
		assertTrue(unplanned.getMessage().contains("has no feature version"), unplanned.getMessage());

		var legacy = assertThrows(ProvisioningException.class, () -> java.inspect(
				"java.version = 1.8.0_392\njava.runtime.version = 1.8.0_392-b08\njava.vendor = Temurin\njava.vm.name = OpenJDK\n",
				Path.of("jdk-8/bin/java")));
		assertTrue(legacy.getMessage().contains("reports version '1.8.0_392'"), legacy.getMessage());
	}

	@Test
	void inspectsExplicitInstallationAndRejectsUnmetRequirementsWithoutDownloading() {
		var java = resolver();
		var selection = JavaRequirement.builder()
				.featureVersion(Runtime.version().feature()).build();
		assertEquals(JavaExecutables.current().toAbsolutePath(), java.resolve(selection, null));
		var missing = assertThrows(ProvisioningException.class, () -> java.resolve(selection.toBuilder().featureVersion(99).build(), null));
		assertTrue(missing.getMessage().contains("the current JVM is Java " + Runtime.version().feature()), missing.getMessage());
		assertTrue(missing.getMessage().contains("JAVA_99_HOME"), missing.getMessage());
		assertTrue(missing.getMessage().contains("anvil.java.download=true"), missing.getMessage());
	}

	@Test
	void acceptsAnExplicitExecutableSource() {
		var java = resolver();
		JavaRequirement selection = JavaRequirement.builder().featureVersion(Runtime.version().feature()).build();

		assertEquals(JavaExecutables.current().toAbsolutePath(),
				java.resolve(selection, JavaSource.executable(JavaExecutables.current())));
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
