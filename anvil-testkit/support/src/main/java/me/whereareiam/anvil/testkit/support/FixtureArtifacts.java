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
	private static final String ANVIL_ARTIFACT_PREFIX = "anvil.artifact.testkit-";

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

	public static @NotNull Path javaLibrary() {
		return artifact("java-library");
	}

	/**
	 * Returns the pinned GrimAC plugin JAR the physics suite runs on a Paper server. The build downloads it only for the
	 * tasks that run against it, and it reaches only test workspaces.
	 *
	 * @return the verified GrimAC JAR
	 */
	public static @NotNull Path grim() {
		return artifact("grim");
	}

	/**
	 * Returns the pinned Sonar plugin JAR the physics suite runs on a Velocity proxy. The build downloads it only for
	 * the tasks that run against it, and it reaches only test workspaces.
	 *
	 * @return the verified Sonar JAR
	 */
	public static @NotNull Path sonar() {
		return artifact("sonar");
	}

	private static Path artifact(String name) {
		String property = PROPERTY_PREFIX + name;
		String configured = System.getProperty(property);
		if (configured == null || configured.isBlank()) configured = System.getProperty(ANVIL_ARTIFACT_PREFIX + name);
		if (configured == null || configured.isBlank())
			throw new IllegalStateException("The Anvil runtime did not supply fixture artifact '" + name
					+ "' through " + property + " or " + ANVIL_ARTIFACT_PREFIX + name);

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
