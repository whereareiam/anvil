package me.whereareiam.anvil.testkit.tests.server.physics;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.testkit.tests.server.scenario.PhysicsScenarioFactory;
import org.jetbrains.annotations.NotNull;

/**
 * The release the physics suite runs against: the {@code anvil.release} system property, which each
 * {@code physicsTest-<minecraft>} task sets, or the newest release of the physics matrix when a test runs on its own.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PhysicsRelease {
	private static final String PROPERTY = "anvil.release";

	/**
	 * Returns the Minecraft version of the release under test.
	 *
	 * @return the release's Minecraft version
	 */
	public static @NotNull MinecraftVersion version() {
		String selected = System.getProperty(PROPERTY);
		if (selected != null && !selected.isBlank()) return MinecraftVersion.parse(selected);

		return PhysicsScenarioFactory.releases().getLast();
	}

	/**
	 * Tells whether the release under test is the given version or newer.
	 *
	 * @param version the first version that has the behaviour
	 * @return whether the release has it
	 */
	public static boolean isAtLeast(@NotNull String version) {
		return version().isAtLeast(MinecraftVersion.parse(version));
	}
}
