package me.whereareiam.anvil.capability.messages.internal;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Preserves literal text and translation arguments from pre-signed-chat packets.
 * Translation templates are client assets; headless observations must not discard their arguments.
 */
final class LegacyMessageText {
	private final PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();

	String render(Component component) {
		String own = plain.serialize(component.children(List.of()));
		if (component instanceof TranslatableComponent translated && !translated.args().isEmpty())
			own = translated.args().stream().map(this::render).collect(Collectors.joining(" "));
		return own + component.children().stream().map(this::render).collect(Collectors.joining());
	}
}
