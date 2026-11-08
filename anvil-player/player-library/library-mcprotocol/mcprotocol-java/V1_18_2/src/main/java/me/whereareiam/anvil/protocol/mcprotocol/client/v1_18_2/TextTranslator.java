package me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2;

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
 * Translates the Adventure components of the MCProtocolLib 1.18.2 and 1.19.4 releases, Adventure 4.9, to the
 * protocol's text: a translation's arguments are its {@code args()}, a click's value is text, and selectors and NBT
 * components have separators.
 */
public final class TextTranslator extends ComponentTranslator {
	@Override
	protected @NotNull List<? extends ComponentLike> arguments(@NotNull TranslatableComponent translation) {
		return translation.args();
	}

	@Override
	protected @Nullable String clickValue(@NotNull ClickEvent click) {
		return click.value();
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
