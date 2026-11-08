package me.whereareiam.anvil.protocol.api.model.text;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.anvil.protocol.api.model.text.content.KeybindContent;
import me.whereareiam.anvil.protocol.api.model.text.content.LiteralContent;
import me.whereareiam.anvil.protocol.api.model.text.content.NbtContent;
import me.whereareiam.anvil.protocol.api.model.text.content.PlayerHeadContent;
import me.whereareiam.anvil.protocol.api.model.text.content.ScoreContent;
import me.whereareiam.anvil.protocol.api.model.text.content.SelectorContent;
import me.whereareiam.anvil.protocol.api.model.text.content.SpriteContent;
import me.whereareiam.anvil.protocol.api.model.text.content.TextContent;
import me.whereareiam.anvil.protocol.api.model.text.content.TranslatedContent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Text a server sends a player, as the game's text component it sent: what the component contributes, the style it
 * sets and its children, which inherit that style. Nothing the client knows about the text is left out, so a test can
 * check which translation a server sent and with which arguments, as well as how the text looks.
 *
 * <p>{@link #spans()} works out what the player sees: runs of text in reading order, each in the style it inherits.
 * The game's own translations need its language files, which a simulated player does not have, so a translation is
 * seen as its key followed by each argument, separated by spaces; a server that translates for each player before it
 * sends text, by the language in the player's client information, sends the translated text itself.</p>
 *
 * <pre>{@code
 * Text welcome = chat.received("Welcome");
 * SpanStyle arbor = welcome.span("Arbor").orElseThrow().getStyle();
 * assertEquals(TextColor.GOLD, arbor.getColor());
 * assertTrue(arbor.isBold());
 * assertEquals(new RunCommandClick("/arbor help"), arbor.getClick());
 *
 * TranslatedContent joined = welcome.find(TranslatedContent.class).orElseThrow();
 * assertEquals("multiplayer.player.joined", joined.getKey());
 * assertEquals("Ivan", joined.getArguments().getFirst().plain());
 * }</pre>
 */
@Value
@Builder(toBuilder = true)
public class Text {
	/**
	 * What the component itself contributes, before its children.
	 */
	@NotNull TextContent content;
	/**
	 * The style the component sets; what it leaves unset it inherits from its parent.
	 */
	@NotNull
	@Builder.Default
	TextStyle style = TextStyle.NONE;
	/**
	 * The components that follow this one's content, in order, each inheriting its style.
	 */
	@NotNull
	@Singular
	List<Text> children;

	/**
	 * Creates unstyled literal text without children, such as the text of a protocol that carries no components.
	 *
	 * @param text the text
	 * @return the text as one literal component
	 */
	public static @NotNull Text literal(@NotNull String text) {
		return Text.builder().content(new LiteralContent(text)).build();
	}

	/**
	 * Returns the text without its styles: every span's text in reading order.
	 *
	 * @return the plain text
	 */
	public @NotNull String plain() {
		return spans().stream().map(TextSpan::getText).collect(Collectors.joining());
	}

	/**
	 * Works out what the player sees: runs of text in reading order, each in the style the game draws it with, which
	 * every component inherits from its parents and a translation's arguments from the translation. Each span is all
	 * the text of one style up to the next change of style, however the server split it into components. Scores, NBT,
	 * sprites and heads contribute no text.
	 *
	 * @return the spans in reading order
	 */
	public @NotNull List<TextSpan> spans() {
		List<TextSpan> spans = new ArrayList<>();
		append(this, SpanStyle.NONE, spans);

		return List.copyOf(spans);
	}

	/**
	 * Finds the first span whose text contains the supplied text. Text in one style is in one span; text that crosses a
	 * change of style is in none.
	 *
	 * @param text required span content
	 * @return the first span containing it, or empty when no span does
	 */
	public @NotNull Optional<TextSpan> span(@NotNull String text) {
		return spans().stream().filter(span -> span.getText().contains(text)).findFirst();
	}

	/**
	 * Finds the first content of a kind in reading order: this component's, then its children's, with a translation's
	 * arguments before the components that follow the translation.
	 *
	 * @param type the kind of content, such as {@code TranslatedContent.class}
	 * @param <C> the kind of content
	 * @return the first such content, or empty when the text has none
	 */
	public <C extends TextContent> @NotNull Optional<C> find(@NotNull Class<C> type) {
		if (type.isInstance(content)) return Optional.of(type.cast(content));
		if (content instanceof TranslatedContent translated) {
			for (Text argument : translated.getArguments()) {
				Optional<C> found = argument.find(type);
				if (found.isPresent()) return found;
			}
		}
		for (Text child : children) {
			Optional<C> found = child.find(type);
			if (found.isPresent()) return found;
		}

		return Optional.empty();
	}

	private static void append(Text text, SpanStyle inherited, List<TextSpan> spans) {
		SpanStyle style = inherited.inherit(text.style);
		switch (text.content) {
			case LiteralContent literal -> add(literal.getText(), style, spans);
			case TranslatedContent translated -> {
				add(translated.getKey(), style, spans);
				for (Text argument : translated.getArguments()) {
					add(" ", style, spans);
					append(argument, style, spans);
				}
			}
			case KeybindContent keybind -> add(keybind.getKeybind(), style, spans);
			case SelectorContent selector -> add(selector.getPattern(), style, spans);
			case ScoreContent ignored -> {
			}
			case NbtContent ignored -> {
			}
			case SpriteContent ignored -> {
			}
			case PlayerHeadContent ignored -> {
			}
		}
		for (Text child : text.children) append(child, style, spans);
	}

	/**
	 * Adds text to the spans, continuing the last span when it has the same style.
	 */
	private static void add(String text, SpanStyle style, List<TextSpan> spans) {
		if (text.isEmpty()) return;

		TextSpan last = spans.isEmpty() ? null : spans.getLast();
		if (last != null && last.getStyle().equals(style)) {
			spans.set(spans.size() - 1, new TextSpan(last.getText() + text, style));
			return;
		}
		spans.add(new TextSpan(text, style));
	}
}
