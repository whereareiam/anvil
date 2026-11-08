package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import me.whereareiam.anvil.protocol.mcprotocol.adventure.ComponentTranslator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.NBTComponent;
import net.kyori.adventure.text.SelectorComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.ClickEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Translates the Adventure components of the MCProtocolLib 1.20.6 and 1.21.1 releases, Adventure 4.15, to the
 * protocol's text: a translation's arguments are its {@code arguments()} and it has a fallback, a click's value is text,
 * and selectors and NBT components have separators. The segment from 1.21.11 on brings its own translator.
 */
public final class TextTranslator extends ComponentTranslator {
	@Override
	protected @NotNull List<? extends ComponentLike> arguments(@NotNull TranslatableComponent translation) {
		return translation.arguments();
	}

	@Override
	protected @Nullable String clickValue(@NotNull ClickEvent click) {
		return click.value();
	}

	@Override
	protected @Nullable String fallback(@NotNull TranslatableComponent translation) {
		return translation.fallback();
	}

	@Override
	protected @Nullable Component separator(@NotNull SelectorComponent selector) {
		return selector.separator();
	}

	@Override
	protected @Nullable Component separator(@NotNull NBTComponent<?, ?> nbt) {
		return nbt.separator();
	}
}
