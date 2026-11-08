package me.whereareiam.anvil.protocol.api.model.text;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Text as the player's game shows it: runs of text in reading order, each in the style the game draws it with. A
 * server sends text as a tree of components whose children inherit their parent's style; the spans are that tree
 * flattened in reading order, each with the style it inherits resolved, so a test reads the colour of a word without
 * walking the tree. Each span is all the text of one style up to the next change of style, however the server split
 * it into components.
 *
 * <p>The game's own translations need its language files, which a simulated player does not have, so a translation
 * appears as its key followed by each argument, separated by spaces, as in the {@link #plain() plain text}. A server
 * that translates text for each player before it sends it, by the language in the player's client information,
 * sends the translated text itself.</p>
 *
 * <pre>{@code
 * StyledText welcome = chat.received("Welcome");
 * TextSpan name = welcome.span("Arbor").orElseThrow();
 * assertEquals(TextColor.GOLD, name.getStyle().getColor());
 * assertTrue(name.getStyle().isBold());
 * assertEquals(new TextClick(ClickAction.RUN_COMMAND, "/arbor help"), name.getStyle().getClick());
 * }</pre>
 */
@Value
public class StyledText {
	/**
	 * The runs of text in reading order.
	 */
	@NotNull List<TextSpan> spans;

	/**
	 * Creates text from a copy of its spans.
	 *
	 * @param spans the runs of text in reading order
	 */
	public StyledText(@NotNull List<TextSpan> spans) {
		this.spans = List.copyOf(spans);
	}

	/**
	 * Creates text drawn without any style, such as the text of a protocol that carries no styles.
	 *
	 * @param text the plain text
	 * @return the text as one unstyled span, or no span when it is empty
	 */
	public static @NotNull StyledText unstyled(@NotNull String text) {
		return new StyledText(text.isEmpty() ? List.of() : List.of(new TextSpan(text, TextStyle.NONE)));
	}

	/**
	 * Returns the text without its styles: every span's text in reading order.
	 *
	 * @return the plain text
	 */
	public @NotNull String plain() {
		return spans.stream().map(TextSpan::getText).collect(Collectors.joining());
	}

	/**
	 * Finds the first span whose text contains the supplied text. Text in one style is in one span; text that crosses a
	 * change of style is in none.
	 *
	 * @param text required span content
	 * @return the first span containing it, or empty when no span does
	 */
	public @NotNull Optional<TextSpan> span(@NotNull String text) {
		return spans.stream().filter(span -> span.getText().contains(text)).findFirst();
	}
}
