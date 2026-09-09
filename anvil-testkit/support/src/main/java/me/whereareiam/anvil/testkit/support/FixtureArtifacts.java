package me.whereareiam.anvil.testkit.support;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * Exact fixture artifacts resolved and supplied by the Gradle test task.
 */
public final class FixtureArtifacts {
	private static final String PROPERTY_PREFIX = "anvil.testkit.fixture.";

	public static @NotNull Path process() {
		return artifact("process");
	}

	public static @NotNull Path serverPlugin() {
		return artifact("server-plugin");
	}

	public static @NotNull Path extension() {
		return artifact("extension-normal");
	}

	public static @NotNull Path brokenExtension() {
		return artifact("extension-broken");
	}

	public static @NotNull Path observationExtension() {
		return artifact("extension-observation");
	}

	private static Path artifact(String name) {
		String property = PROPERTY_PREFIX + name;
		String configured = System.getProperty(property);
		if (configured == null || configured.isBlank())
			throw new IllegalStateException("The test task did not supply fixture artifact '" + name + "' through " + property);

		Path artifact;
		try {
			artifact = Path.of(configured).toAbsolutePath().normalize();
		} catch (InvalidPathException failure) {
			throw new IllegalStateException("Invalid fixture artifact path supplied through " + property, failure);
		}
		if (!Files.isRegularFile(artifact) || !Files.isReadable(artifact))
			throw new IllegalStateException("The fixture artifact supplied through " + property + " is not a readable file: " + artifact);

		return artifact;
	}
}
