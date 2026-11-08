package me.whereareiam.anvil.protocol.api.model.text;

import me.whereareiam.anvil.protocol.api.model.text.click.RunCommandClick;
import me.whereareiam.anvil.protocol.api.model.text.content.KeybindContent;
import me.whereareiam.anvil.protocol.api.model.text.content.LiteralContent;
import me.whereareiam.anvil.protocol.api.model.text.content.ScoreContent;
import me.whereareiam.anvil.protocol.api.model.text.content.SpriteContent;
import me.whereareiam.anvil.protocol.api.model.text.content.TranslatedContent;
import me.whereareiam.anvil.protocol.api.model.text.hover.ShowTextHover;
import org.junit.jupiter.api.Test;

import java.util.List;

import static me.whereareiam.anvil.protocol.api.type.TextDecoration.BOLD;
import static me.whereareiam.anvil.protocol.api.type.TextDecoration.ITALIC;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextTest {
	@Test
	void resolvesWhatEachPartInheritsAndWhatItSetsItself() {
		Text message = Text.builder()
				.content(new LiteralContent("["))
				.style(TextStyle.builder().color(TextColor.DARK_GRAY).decoration(BOLD, true).build())
				.child(text("Arbor", TextStyle.builder().color(TextColor.GOLD).decoration(ITALIC, true).build()))
				.child(text("] ", TextStyle.builder().decoration(BOLD, false).build()))
				.child(text("help", TextStyle.builder().click(new RunCommandClick("/arbor help")).build()))
				.build();
		SpanStyle bracket = SpanStyle.builder().color(TextColor.DARK_GRAY).bold(true).build();

		assertEquals(List.of(
				new TextSpan("[", bracket),
				new TextSpan("Arbor", bracket.toBuilder().color(TextColor.GOLD).italic(true).build()),
				new TextSpan("] ", bracket.toBuilder().bold(false).build()),
				new TextSpan("help", bracket.toBuilder().click(new RunCommandClick("/arbor help")).build())
		), message.spans());
	}

	@Test
	void joinsNeighbouringPartsOfOneStyleAndShowsATranslationAsItsKeyAndArguments() {
		TextStyle yellow = TextStyle.builder().color(TextColor.YELLOW).build();
		Text message = Text.builder()
				.content(new TranslatedContent("multiplayer.player.joined", null, List.of(
						Text.literal("Ivan"),
						text("!", TextStyle.builder().color(TextColor.RED).build()))))
				.style(yellow)
				.child(Text.literal(" Welcome"))
				.build();
		SpanStyle drawn = SpanStyle.builder().color(TextColor.YELLOW).build();

		assertEquals(List.of(
				new TextSpan("multiplayer.player.joined Ivan ", drawn),
				new TextSpan("!", drawn.toBuilder().color(TextColor.RED).build()),
				new TextSpan(" Welcome", drawn)
		), message.spans());
		assertEquals("multiplayer.player.joined Ivan ! Welcome", message.plain());
	}

	@Test
	void showsKeybindsAndNoTextForScoresOrSprites() {
		Text message = Text.builder()
				.content(new KeybindContent("key.jump"))
				.child(Text.builder().content(new ScoreContent("Alice", "kills")).build())
				.child(Text.builder().content(new SpriteContent("minecraft:blocks", "minecraft:block/stone")).build())
				.build();

		assertEquals("key.jump", message.plain());
	}

	@Test
	void findsTheFirstContentOfAKindWithATranslationsArgumentsBeforeWhatFollowsIt() {
		Text message = Text.builder()
				.content(new TranslatedContent("arbor.welcome", null, List.of(Text.literal("argument"))))
				.child(Text.literal("child"))
				.build();

		assertEquals("arbor.welcome", message.find(TranslatedContent.class).orElseThrow().getKey());
		assertEquals(new LiteralContent("argument"), message.find(LiteralContent.class).orElseThrow());
		assertTrue(message.find(KeybindContent.class).isEmpty());
	}

	@Test
	void findsTheSpanThatHoldsAWord() {
		ShowTextHover tip = new ShowTextHover(Text.literal("Open the help"));
		Text message = Text.builder()
				.content(new LiteralContent("Use "))
				.child(text("/help", TextStyle.builder().hover(tip).build()))
				.build();

		assertEquals(tip, message.span("help").orElseThrow().getStyle().getHover());
		assertTrue(message.span("Use /help").isEmpty());
	}

	private static Text text(String text, TextStyle style) {
		return Text.builder().content(new LiteralContent(text)).style(style).build();
	}
}
