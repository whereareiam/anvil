package me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5;

import me.whereareiam.anvil.protocol.mcprotocol.adventure.ComponentTranslator;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.ClickEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Translates the Adventure components of the MCProtocolLib 1.16.5 and 1.17.1 releases, Adventure 4.7 and 4.9, to the
 * protocol's text: a translation's arguments are its {@code args()}, a click's value is text, and neither selectors nor
 * NBT components have separators in Adventure 4.7.
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
}
