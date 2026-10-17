package me.whereareiam.anvil.testkit.fixtures.bukkit.v1_21_1;

import me.whereareiam.anvil.testkit.fixtures.bukkit.FixtureDisplays;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.TextDisplay;

/**
 * Spawns text displays through the API of Paper 1.21.1, which later releases keep.
 */
public final class PaperFixtureDisplays implements FixtureDisplays {
	@Override
	public int show(Location location, String text) {
		return location.getWorld().spawn(location, TextDisplay.class, display -> display.text(Component.text(text))).getEntityId();
	}
}
