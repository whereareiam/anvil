package me.whereareiam.anvil.protocol.mcprotocol.adventure;

import me.whereareiam.anvil.protocol.api.model.text.StyledText;
import me.whereareiam.anvil.protocol.api.model.text.TextClick;
import me.whereareiam.anvil.protocol.api.model.text.TextColor;
import me.whereareiam.anvil.protocol.api.model.text.TextSpan;
import me.whereareiam.anvil.protocol.api.model.text.TextStyle;
import me.whereareiam.anvil.protocol.api.type.ClickAction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.KeybindComponent;
import net.kyori.adventure.text.SelectorComponent;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Translates the Adventure components of MCProtocolLib releases to the text the Java Edition protocol carries: plain
 * text, or {@link StyledText} whose spans carry the style each part inherits. Text appears as written, keybinds and
 * selectors as their names, and a translation as its key followed by each argument, separated by spaces, since a
 * client without the game's language files cannot translate it.
 *
 * <p>Only segments of releases whose components are Adventure's use it; Adventure comes from the release's own
 * closure. Adventure 4.15 added a translation's {@code arguments()} and Adventure 5 removed its {@code args()}, so the
 * earliest segment of each chain creates the text with the accessor its releases share, which
 * {@code checkSegmentLinkage} verifies. A click's value moved the same way, from {@code value()} to Adventure 4.22's
 * {@code payload()}, so the segment that styles text supplies how its releases read it. Everything else calls only
 * Adventure API that has not changed since Adventure 4.0.</p>
 *
 * <pre>{@code
 * private static final AdventureText TEXT = new AdventureText(TranslatableComponent::arguments);
 *
 * String reason = TEXT.plain(disconnect.getReason());
 * StyledText message = TEXT.styled(chat.getContent(), ClickEvent::value);
 * }</pre>
 */
public final class AdventureText {
	private final Function<TranslatableComponent, List<? extends ComponentLike>> arguments;

	/**
	 * Creates the text of releases that read a translation's arguments with one accessor.
	 *
	 * @param arguments reads the arguments of a translation, {@code TranslatableComponent::args} before Adventure
	 * 4.15 and {@code TranslatableComponent::arguments} from it on
	 */
	public AdventureText(@NotNull Function<TranslatableComponent, List<? extends ComponentLike>> arguments) {
		this.arguments = arguments;
	}

	/**
	 * Flattens a component with its children.
	 *
	 * @param component component to flatten
	 * @return the component as plain text
	 */
	public @NotNull String plain(@NotNull Component component) {
		List<TextSpan> spans = new ArrayList<>();
		append(component, TextStyle.NONE, null, spans);

		return spans.stream().map(TextSpan::getText).collect(Collectors.joining());
	}

	/**
	 * Flattens a component with its children to spans in reading order, each with the style it inherits resolved;
	 * neighbouring parts of one style become one span. Text shown on hover is styled on its own, as the game draws it.
	 *
	 * @param component component to flatten
	 * @param clickValue reads what a click acts on, {@code ClickEvent::value} before Adventure 4.22 and the click's
	 * {@code payload()} from it on; {@code null} when the click's payload is no text, such as a dialog
	 * @return the component as styled text
	 */
	public @NotNull StyledText styled(
			@NotNull Component component,
			@NotNull Function<ClickEvent, @Nullable String> clickValue
	) {
		List<TextSpan> spans = new ArrayList<>();
		append(component, TextStyle.NONE, clickValue, spans);

		return new StyledText(spans);
	}

	/**
	 * Appends a component's own text and then its children's. Without a click reader the text stays unstyled, so
	 * flattening to plain text reads no style.
	 */
	private void append(
			Component component,
			TextStyle inherited,
			@Nullable Function<ClickEvent, String> clickValue,
			List<TextSpan> spans
	) {
		TextStyle style = clickValue == null ? inherited : resolve(component.style(), inherited, clickValue);
		if (component instanceof TextComponent value) add(value.content(), style, spans);
		if (component instanceof KeybindComponent value) add(value.keybind(), style, spans);
		if (component instanceof SelectorComponent value) add(value.pattern(), style, spans);
		if (component instanceof TranslatableComponent value) {
			add(value.key(), style, spans);
			for (ComponentLike argument : arguments.apply(value)) {
				add(" ", style, spans);
				append(argument.asComponent(), style, clickValue, spans);
			}
		}
		for (Component child : component.children()) append(child, style, clickValue, spans);
	}

	/**
	 * Adds text to the spans, continuing the last span when it has the same style, so neighbouring spans always
	 * differ in style.
	 */
	private static void add(String text, TextStyle style, List<TextSpan> spans) {
		if (text.isEmpty()) return;

		TextSpan last = spans.isEmpty() ? null : spans.getLast();
		if (last != null && last.getStyle().equals(style)) {
			spans.set(spans.size() - 1, new TextSpan(last.getText() + text, style));
			return;
		}
		spans.add(new TextSpan(text, style));
	}

	/**
	 * Resolves a component's style against the style it inherits: what the component sets wins, what it leaves unset
	 * is inherited.
	 */
	private TextStyle resolve(Style style, TextStyle inherited, Function<ClickEvent, String> clickValue) {
		TextStyle.TextStyleBuilder resolved = inherited.toBuilder()
				.bold(decorated(style, TextDecoration.BOLD, inherited.isBold()))
				.italic(decorated(style, TextDecoration.ITALIC, inherited.isItalic()))
				.underlined(decorated(style, TextDecoration.UNDERLINED, inherited.isUnderlined()))
				.strikethrough(decorated(style, TextDecoration.STRIKETHROUGH, inherited.isStrikethrough()))
				.obfuscated(decorated(style, TextDecoration.OBFUSCATED, inherited.isObfuscated()));
		if (style.color() != null) resolved.color(new TextColor(style.color().value()));
		if (style.font() != null) resolved.font(style.font().asString());
		if (style.insertion() != null) resolved.insertion(style.insertion());
		if (style.clickEvent() != null) resolved.click(click(style.clickEvent(), clickValue));
		if (style.hoverEvent() != null) resolved.hover(hover(style.hoverEvent(), clickValue));

		return resolved.build();
	}

	private static boolean decorated(Style style, TextDecoration decoration, boolean inherited) {
		TextDecoration.State state = style.decoration(decoration);
		if (state == TextDecoration.State.NOT_SET) return inherited;

		return state == TextDecoration.State.TRUE;
	}

	/**
	 * Reads a click by the action's name, which Adventure keeps while its action type changes; an action the protocol
	 * does not know leaves the text without a click.
	 */
	private static @Nullable TextClick click(ClickEvent click, Function<ClickEvent, String> clickValue) {
		String name = ClickEvent.Action.NAMES.key(click.action());
		if (name == null) return null;

		ClickAction action = switch (name) {
			case "open_url" -> ClickAction.OPEN_URL;
			case "open_file" -> ClickAction.OPEN_FILE;
			case "run_command" -> ClickAction.RUN_COMMAND;
			case "suggest_command" -> ClickAction.SUGGEST_COMMAND;
			case "change_page" -> ClickAction.CHANGE_PAGE;
			case "copy_to_clipboard" -> ClickAction.COPY_TO_CLIPBOARD;
			case "show_dialog" -> ClickAction.SHOW_DIALOG;
			case "custom" -> ClickAction.CUSTOM;
			default -> null;
		};

		return action == null ? null : new TextClick(action, clickValue.apply(click));
	}

	private @Nullable StyledText hover(HoverEvent<?> hover, Function<ClickEvent, String> clickValue) {
		if (hover.action() != HoverEvent.Action.SHOW_TEXT) return null;

		return styled((Component) hover.value(), clickValue);
	}
}
