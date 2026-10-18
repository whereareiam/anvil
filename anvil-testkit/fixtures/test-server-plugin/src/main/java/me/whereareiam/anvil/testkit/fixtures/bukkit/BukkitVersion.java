package me.whereareiam.anvil.testkit.fixtures.bukkit;

/**
 * Compares the Minecraft version a Bukkit version string names, such as {@code 1.21.1-R0.1-SNAPSHOT} or Paper's
 * {@code 26.3.build.202-stable}, by its leading numeric components.
 */
final class BukkitVersion {
	private BukkitVersion() {
	}

	/**
	 * Tells whether a Bukkit version names a Minecraft version at or above another.
	 *
	 * @param bukkitVersion the server's Bukkit version
	 * @param minimum the lowest Minecraft version's major, minor and patch numbers
	 * @return whether the server's Minecraft version is at least the minimum
	 */
	static boolean atLeast(String bukkitVersion, int... minimum) {
		String[] parts = bukkitVersion.split("-")[0].split("\\.");
		for (int index = 0; index < minimum.length; index++) {
			int part = index < parts.length && parts[index].chars().allMatch(Character::isDigit)
					? Integer.parseInt(parts[index])
					: 0;
			if (part != minimum[index]) return part > minimum[index];
		}

		return true;
	}
}
