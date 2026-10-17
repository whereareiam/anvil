package me.whereareiam.anvil.protocol.mcprotocol.adventure;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdventureTextTest {
	@Test
	void keepsTextAsWrittenWithItsChildrenInOrder() {
		Component message = Component.text("anvil:")
				.append(Component.text("wel"))
				.append(Component.text("come").append(Component.text("!")));

		assertEquals("anvil:welcome!", AdventureText.plain(message));
	}

	@Test
	void ignoresStyle() {
		assertEquals("ready", AdventureText.plain(Component.text("ready", NamedTextColor.GREEN)));
	}

	@Test
	void writesATranslationAsItsKeyFollowedByEachArgument() {
		Component message = Component.translatable("chat.type.text", Component.text("Alice"), Component.text("hello"))
				.append(Component.text("!"));

		assertEquals("chat.type.text Alice hello!", AdventureText.plain(message));
	}

	@Test
	void flattensNestedTranslationArguments() {
		Component message = Component.translatable("multiplayer.disconnect.kicked",
				Component.translatable("commands.kick.success", Component.text("Alice").append(Component.text("_2"))));

		assertEquals("multiplayer.disconnect.kicked commands.kick.success Alice_2", AdventureText.plain(message));
	}

	@Test
	void writesATranslationWithoutArgumentsAsItsKey() {
		assertEquals("disconnect.timeout", AdventureText.plain(Component.translatable("disconnect.timeout")));
	}

	@Test
	void writesKeybindsAndSelectorsAsTheirNamesAndIgnoresScores() {
		Component message = Component.text("press ")
				.append(Component.keybind("key.jump"))
				.append(Component.text(" near "))
				.append(Component.selector("@p"))
				.append(Component.score("Alice", "kills"))
				.append(Component.text("!"));

		assertEquals("press key.jump near @p!", AdventureText.plain(message));
	}

	@Test
	void flattensAnEmptyComponentToEmptyText() {
		assertEquals("", AdventureText.plain(Component.empty()));
	}
}
