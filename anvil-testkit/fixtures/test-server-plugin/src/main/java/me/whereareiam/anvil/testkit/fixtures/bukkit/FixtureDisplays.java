package me.whereareiam.anvil.testkit.fixtures.bukkit;

import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.util.Iterator;
import java.util.ServiceLoader;

/**
 * Spawns text displays, which Minecraft 1.19.4 added. The plugin compiles against an older API, so the module
 * {@code V1_19_4}, compiled against Paper 1.19.4 for the Java 17 that its servers run on, implements it, and only
 * servers from 1.19.4 on load it.
 */
public interface FixtureDisplays {
	/**
	 * Spawns a text display showing a text.
	 *
	 * @param location where the display appears
	 * @param text the text it shows
	 * @return entity ID of the display
	 */
	int show(Location location, String text);

	/**
	 * Loads the implementation for this server, or none before Minecraft 1.19.4.
	 *
	 * @param loader the plugin's class loader
	 * @return the implementation, or null when the server has no text displays
	 */
	static FixtureDisplays load(ClassLoader loader) {
		if (!supported(Bukkit.getBukkitVersion())) return null;

		Iterator<FixtureDisplays> implementations = ServiceLoader.load(FixtureDisplays.class, loader).iterator();
		return implementations.hasNext() ? implementations.next() : null;
	}

	/**
	 * Tells whether a Bukkit version, such as {@code 1.21.1-R0.1-SNAPSHOT}, has text displays.
	 */
	static boolean supported(String bukkitVersion) {
		String[] parts = bukkitVersion.split("-")[0].split("\\.");
		int major = Integer.parseInt(parts[0]);
		int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
		int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
		if (major != 1) return major > 1;
		if (minor != 19) return minor > 19;

		return patch >= 4;
	}
}
