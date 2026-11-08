package me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5;

import me.whereareiam.anvil.protocol.mcprotocol.adventure.AdventureRelease;
import net.kyori.adventure.text.event.ClickEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * What the Adventure of the MCProtocolLib 1.16.5 and 1.17.1 releases, 4.7 and 4.9, keeps differently: a click's
 * value is text, and neither selectors nor NBT components have separators in Adventure 4.7.
 */
public final class ReleaseAdventure implements AdventureRelease {
	@Override
	public @Nullable String clickValue(@NotNull ClickEvent click) {
		return click.value();
	}
}
