package me.whereareiam.anvil.protocol.mcprotocol.adventure;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdventureTextTest {
	private static final AdventureText TEXT = new AdventureText(TranslatableComponent::args);

	@Test
	void keepsTextAsWrittenWithItsChildrenInOrder() {
		Component message = Component.text("anvil:")
				.append(Component.text("wel"))
				.append(Component.text("come").append(Component.text("!")));

		assertEquals("anvil:welcome!", TEXT.plain(message));
	}

	@Test
	void ignoresStyle() {
		assertEquals("ready", TEXT.plain(Component.text("ready", NamedTextColor.GREEN)));
	}

	@Test
	void writesATranslationAsItsKeyFollowedByEachArgument() {
		Component message = Component.translatable("chat.type.text", Component.text("Alice"), Component.text("hello"))
				.append(Component.text("!"));

		assertEquals("chat.type.text Alice hello!", TEXT.plain(message));
	}

	@Test
	void flattensNestedTranslationArguments() {
		Component message = Component.translatable("multiplayer.disconnect.kicked",
				Component.translatable("commands.kick.success", Component.text("Alice").append(Component.text("_2"))));

		assertEquals("multiplayer.disconnect.kicked commands.kick.success Alice_2", TEXT.plain(message));
	}

	@Test
	void writesATranslationWithoutArgumentsAsItsKey() {
		assertEquals("disconnect.timeout", TEXT.plain(Component.translatable("disconnect.timeout")));
	}

	@Test
	void writesKeybindsAndSelectorsAsTheirNamesAndIgnoresScores() {
		Component message = Component.text("press ")
				.append(Component.keybind("key.jump"))
				.append(Component.text(" near "))
				.append(Component.selector("@p"))
				.append(Component.score("Alice", "kills"))
				.append(Component.text("!"));

		assertEquals("press key.jump near @p!", TEXT.plain(message));
	}

	@Test
	void readsTranslationArgumentsThroughTheGivenAccessor() {
		AdventureText text = new AdventureText(component -> List.of(Component.text("read")));

		assertEquals("chat.type.text read", text.plain(Component.translatable("chat.type.text", Component.text("Alice"))));
	}

	@Test
	void flattensAnEmptyComponentToEmptyText() {
		assertEquals("", TEXT.plain(Component.empty()));
	}
}
