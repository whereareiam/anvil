package me.whereareiam.anvil.capability.messages.internal;

import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyMessageTextTest {
	@Test
	void keepsChatArgumentsAndNestedChildren() {
		var chat = Component.text("prefix ").append(Component.translatable("chat.type.text",
				Component.text("Alice"), Component.text("hello-from-anvil"))).append(Component.text(" suffix"));
		assertEquals("prefix Alice hello-from-anvil suffix", new LegacyMessageText().render(chat));
	}

	@Test
	void preservesLiteralMessagesAndUnknownTranslationKeys() {
		var renderer = new LegacyMessageText();
		assertEquals("literal", renderer.render(Component.text("literal")));
		assertEquals("unknown.key", renderer.render(Component.translatable("unknown.key")));
	}
}
