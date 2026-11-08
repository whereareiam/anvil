package me.whereareiam.anvil.protocol.mcprotocol.adventure;

import me.whereareiam.anvil.protocol.api.model.text.StyledText;
import me.whereareiam.anvil.protocol.api.model.text.TextClick;
import me.whereareiam.anvil.protocol.api.model.text.TextColor;
import me.whereareiam.anvil.protocol.api.model.text.TextSpan;
import me.whereareiam.anvil.protocol.api.model.text.TextStyle;
import me.whereareiam.anvil.protocol.api.type.ClickAction;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
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

	@Test
	void stylesEachPartWithWhatItInheritsAndWhatItSetsItself() {
		Component message = Component.text("[", NamedTextColor.DARK_GRAY)
				.decorate(TextDecoration.BOLD)
				.append(Component.text("Arbor", NamedTextColor.GOLD))
				.append(Component.text("] ").decoration(TextDecoration.BOLD, false))
				.append(Component.text("ready", Style.style(TextDecoration.ITALIC, TextDecoration.UNDERLINED,
						TextDecoration.STRIKETHROUGH, TextDecoration.OBFUSCATED)));
		TextStyle bracket = TextStyle.builder().color(TextColor.DARK_GRAY).bold(true).build();

		assertEquals(new StyledText(List.of(
				new TextSpan("[", bracket),
				new TextSpan("Arbor", bracket.toBuilder().color(new TextColor(0xffaa00)).build()),
				new TextSpan("] ", bracket.toBuilder().bold(false).build()),
				new TextSpan("ready", bracket.toBuilder().italic(true).underlined(true).strikethrough(true).obfuscated(true).build())
		)), TEXT.styled(message, ClickEvent::value));
	}

	@Test
	void joinsNeighbouringPartsOfOneStyleAndStylesTranslationArguments() {
		Component message = Component.translatable("chat.type.text", NamedTextColor.WHITE,
						Component.text("Alice", NamedTextColor.AQUA), Component.text("hello"))
				.append(Component.text("!"));
		TextStyle white = TextStyle.builder().color(TextColor.WHITE).build();

		assertEquals(new StyledText(List.of(
				new TextSpan("chat.type.text ", white),
				new TextSpan("Alice", white.toBuilder().color(TextColor.AQUA).build()),
				new TextSpan(" hello!", white)
		)), TEXT.styled(message, ClickEvent::value));
	}

	@Test
	void readsTheFontInsertionClickAndHoverTextOfAPart() {
		Component message = Component.text("vote")
				.style(style -> style.font(Key.key("minecraft", "uniform")))
				.insertion("/vote")
				.clickEvent(ClickEvent.suggestCommand("/vote "))
				.hoverEvent(HoverEvent.showText(Component.text("Click to ").append(Component.text("vote", NamedTextColor.GREEN))));

		TextStyle style = TEXT.styled(message, click -> "read " + click.value()).getSpans().getFirst().getStyle();

		assertEquals("minecraft:uniform", style.getFont());
		assertEquals("/vote", style.getInsertion());
		assertEquals(new TextClick(ClickAction.SUGGEST_COMMAND, "read /vote "), style.getClick());
		assertEquals(new StyledText(List.of(
				new TextSpan("Click to ", TextStyle.NONE),
				new TextSpan("vote", TextStyle.builder().color(TextColor.GREEN).build())
		)), style.getHover());
	}

	@Test
	void flattensStyledTextToTheSamePlainText() {
		Component message = Component.translatable("multiplayer.player.joined", NamedTextColor.YELLOW, Component.text("Alice"))
				.append(Component.keybind("key.jump", NamedTextColor.RED));

		assertEquals(TEXT.plain(message), TEXT.styled(message, ClickEvent::value).plain());
	}
}
