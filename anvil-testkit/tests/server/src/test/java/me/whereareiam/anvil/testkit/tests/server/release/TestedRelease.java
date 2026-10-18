package me.whereareiam.anvil.testkit.tests.server.release;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.testkit.tests.server.scenario.CompatibilityScenarioFactory;
import org.jetbrains.annotations.NotNull;

/**
 * The release the release suite runs against: the {@code anvil.release} system property, which each
 * {@code releaseTest-<minecraft>} task sets, or the newest release of the live matrix when a test runs on its own.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class TestedRelease {
	private static final String PROPERTY = "anvil.release";

	/**
	 * Returns the Minecraft version of the release under test.
	 *
	 * @return the release's Minecraft version
	 */
	public static @NotNull MinecraftVersion version() {
		String selected = System.getProperty(PROPERTY);
		if (selected != null && !selected.isBlank()) return MinecraftVersion.parse(selected);

		return CompatibilityScenarioFactory.releases().getLast();
	}

	/**
	 * Tells whether the release under test is the given version or newer, such as for a feature the game added in
	 * that version.
	 *
	 * @param version the first version that has the feature
	 * @return whether the release has it
	 */
	public static boolean isAtLeast(@NotNull String version) {
		return version().isAtLeast(MinecraftVersion.parse(version));
	}
}
