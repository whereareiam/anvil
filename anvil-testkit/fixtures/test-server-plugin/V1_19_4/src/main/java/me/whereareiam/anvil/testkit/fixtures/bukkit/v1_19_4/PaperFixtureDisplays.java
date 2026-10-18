package me.whereareiam.anvil.testkit.fixtures.bukkit.v1_19_4;

import me.whereareiam.anvil.testkit.fixtures.bukkit.FixtureDisplays;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.TextDisplay;

/**
 * Spawns text displays through the API of Paper 1.19.4, which added them and which later releases keep.
 */
public final class PaperFixtureDisplays implements FixtureDisplays {
	@Override
	public int show(Location location, String text) {
		return location.getWorld()
				.spawn(location, TextDisplay.class, display -> display.text(Component.text(text)))
				.getEntityId();
	}
}
