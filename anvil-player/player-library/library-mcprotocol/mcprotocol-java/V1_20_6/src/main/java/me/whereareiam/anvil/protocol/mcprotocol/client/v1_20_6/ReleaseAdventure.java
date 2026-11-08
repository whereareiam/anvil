package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import me.whereareiam.anvil.protocol.mcprotocol.adventure.AdventureRelease;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.NBTComponent;
import net.kyori.adventure.text.SelectorComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.ClickEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * What the Adventure of the MCProtocolLib 1.20.6 and 1.21.1 releases, 4.15, keeps differently: a click's value is
 * text, translations have fallbacks, and selectors and NBT components have separators.
 */
public final class ReleaseAdventure implements AdventureRelease {
	@Override
	public @Nullable String clickValue(@NotNull ClickEvent click) {
		return click.value();
	}

	@Override
	public @Nullable String fallback(@NotNull TranslatableComponent translation) {
		return translation.fallback();
	}

	@Override
	public @Nullable Component separator(@NotNull SelectorComponent selector) {
		return selector.separator();
	}

	@Override
	public @Nullable Component separator(@NotNull NBTComponent<?, ?> nbt) {
		return nbt.separator();
	}
}
