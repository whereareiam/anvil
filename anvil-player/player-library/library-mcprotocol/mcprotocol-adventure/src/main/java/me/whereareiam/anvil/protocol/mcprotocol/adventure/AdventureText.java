package me.whereareiam.anvil.protocol.mcprotocol.adventure;

import me.whereareiam.anvil.protocol.api.model.text.Text;
import me.whereareiam.anvil.protocol.api.model.text.TextColor;
import me.whereareiam.anvil.protocol.api.model.text.TextStyle;
import me.whereareiam.anvil.protocol.api.model.text.click.ChangePageClick;
import me.whereareiam.anvil.protocol.api.model.text.click.CopyToClipboardClick;
import me.whereareiam.anvil.protocol.api.model.text.click.CustomClick;
import me.whereareiam.anvil.protocol.api.model.text.click.OpenFileClick;
import me.whereareiam.anvil.protocol.api.model.text.click.OpenUrlClick;
import me.whereareiam.anvil.protocol.api.model.text.click.RunCommandClick;
import me.whereareiam.anvil.protocol.api.model.text.click.ShowDialogClick;
import me.whereareiam.anvil.protocol.api.model.text.click.SuggestCommandClick;
import me.whereareiam.anvil.protocol.api.model.text.click.TextClick;
import me.whereareiam.anvil.protocol.api.model.text.content.KeybindContent;
import me.whereareiam.anvil.protocol.api.model.text.content.LiteralContent;
import me.whereareiam.anvil.protocol.api.model.text.content.NbtContent;
import me.whereareiam.anvil.protocol.api.model.text.content.ScoreContent;
import me.whereareiam.anvil.protocol.api.model.text.content.SelectorContent;
import me.whereareiam.anvil.protocol.api.model.text.content.TextContent;
import me.whereareiam.anvil.protocol.api.model.text.content.TranslatedContent;
import me.whereareiam.anvil.protocol.api.model.text.hover.ShowEntityHover;
import me.whereareiam.anvil.protocol.api.model.text.hover.ShowItemHover;
import me.whereareiam.anvil.protocol.api.model.text.hover.ShowTextHover;
import me.whereareiam.anvil.protocol.api.model.text.hover.TextHover;
import me.whereareiam.anvil.protocol.api.type.NbtSource;
import net.kyori.adventure.text.BlockNBTComponent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.EntityNBTComponent;
import net.kyori.adventure.text.KeybindComponent;
import net.kyori.adventure.text.ScoreComponent;
import net.kyori.adventure.text.SelectorComponent;
import net.kyori.adventure.text.StorageNBTComponent;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextDecoration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

/**
 * Translates the Adventure components of MCProtocolLib releases to the text the Java Edition protocol carries: plain
 * text, or the {@link Text} the server sent, with every component's content, style and children. Plain text joins the
 * text as written, keybinds and selectors as their names, and a translation as its key followed by each argument,
 * separated by spaces, since a client without the game's language files cannot translate it.
 *
 * <p>Only segments of releases whose components are Adventure's use it; Adventure comes from the release's own
 * closure. Adventure 4.15 added a translation's {@code arguments()} and Adventure 5 removed its {@code args()}, so the
 * earliest segment of each chain creates the text with the accessor its releases share, which
 * {@code checkSegmentLinkage} verifies. What else moved, such as a click's value or the shadow colour, the segment
 * supplies as an {@link AdventureRelease}. Everything here calls only Adventure API that has not changed since
 * Adventure 4.0.</p>
 *
 * <pre>{@code
 * private static final AdventureText TEXT = new AdventureText(TranslatableComponent::arguments);
 *
 * String reason = TEXT.plain(disconnect.getReason());
 * Text message = TEXT.text(chat.getContent(), new ReleaseAdventure());
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
		StringBuilder text = new StringBuilder();
		append(component, text);

		return text.toString();
	}

	private void append(Component component, StringBuilder text) {
		if (component instanceof TextComponent value) text.append(value.content());
		if (component instanceof KeybindComponent value) text.append(value.keybind());
		if (component instanceof SelectorComponent value) text.append(value.pattern());
		if (component instanceof TranslatableComponent value) {
			text.append(value.key());
			for (ComponentLike argument : arguments.apply(value)) {
				text.append(' ');
				append(argument.asComponent(), text);
			}
		}
		for (Component child : component.children()) append(child, text);
	}

	/**
	 * Translates a component with its children, keeping the content, style and children of each.
	 *
	 * @param component component to translate
	 * @param release what the release's Adventure keeps differently
	 * @return the component as text
	 * @throws IllegalArgumentException when the component is of a kind neither Adventure 4.0 nor the release has
	 */
	public @NotNull Text text(@NotNull Component component, @NotNull AdventureRelease release) {
		return Text.builder()
				.content(content(component, release))
				.style(style(component.style(), release))
				.children(component.children().stream().map(child -> text(child, release)).toList())
				.build();
	}

	private TextContent content(Component component, AdventureRelease release) {
		if (component instanceof TextComponent value) return new LiteralContent(value.content());
		if (component instanceof TranslatableComponent value)
			return new TranslatedContent(value.key(), release.fallback(value), arguments.apply(value).stream()
					.map(argument -> text(argument.asComponent(), release))
					.toList());
		if (component instanceof KeybindComponent value) return new KeybindContent(value.keybind());
		if (component instanceof SelectorComponent value)
			return new SelectorContent(value.pattern(), optional(release.separator(value), release));
		if (component instanceof ScoreComponent value) return new ScoreContent(value.name(), value.objective());
		if (component instanceof BlockNBTComponent value)
			return nbt(value.nbtPath(), value.interpret(), release.separator(value), NbtSource.BLOCK, value.pos().asString(), release);
		if (component instanceof EntityNBTComponent value)
			return nbt(value.nbtPath(), value.interpret(), release.separator(value), NbtSource.ENTITY, value.selector(), release);
		if (component instanceof StorageNBTComponent value)
			return nbt(value.nbtPath(), value.interpret(), release.separator(value), NbtSource.STORAGE, value.storage().asString(), release);

		TextContent added = release.content(component);
		if (added == null) throw new IllegalArgumentException("Unknown text component " + component.getClass().getName());

		return added;
	}

	private NbtContent nbt(
			String path,
			boolean interpret,
			@Nullable Component separator,
			NbtSource source,
			String target,
			AdventureRelease release
	) {
		return new NbtContent(path, interpret, optional(separator, release), source, target);
	}

	private @Nullable Text optional(@Nullable Component component, AdventureRelease release) {
		return component == null ? null : text(component, release);
	}

	private TextStyle style(Style style, AdventureRelease release) {
		TextStyle.TextStyleBuilder sent = TextStyle.builder()
				.color(style.color() == null ? null : new TextColor(style.color().value()))
				.shadowColor(release.shadowColor(style))
				.font(style.font() == null ? null : style.font().asString())
				.insertion(style.insertion())
				.click(style.clickEvent() == null ? null : click(style.clickEvent(), release))
				.hover(style.hoverEvent() == null ? null : hover(style.hoverEvent(), release));
		for (TextDecoration decoration : TextDecoration.values()) {
			TextDecoration.State state = style.decoration(decoration);
			if (state != TextDecoration.State.NOT_SET)
				sent.decoration(decoration(decoration), state == TextDecoration.State.TRUE);
		}

		return sent.build();
	}

	private static me.whereareiam.anvil.protocol.api.type.TextDecoration decoration(TextDecoration decoration) {
		return switch (decoration) {
			case OBFUSCATED -> me.whereareiam.anvil.protocol.api.type.TextDecoration.OBFUSCATED;
			case BOLD -> me.whereareiam.anvil.protocol.api.type.TextDecoration.BOLD;
			case STRIKETHROUGH -> me.whereareiam.anvil.protocol.api.type.TextDecoration.STRIKETHROUGH;
			case UNDERLINED -> me.whereareiam.anvil.protocol.api.type.TextDecoration.UNDERLINED;
			case ITALIC -> me.whereareiam.anvil.protocol.api.type.TextDecoration.ITALIC;
		};
	}

	/**
	 * Reads a click by the action's name, which Adventure keeps while its action type changes, and the value the
	 * release reads; an action the protocol does not know leaves the text without a click.
	 */
	private static @Nullable TextClick click(ClickEvent click, AdventureRelease release) {
		String action = ClickEvent.Action.NAMES.key(click.action());
		if (action == null) return null;

		String value = release.clickValue(click);
		return switch (action) {
			case "open_url" -> new OpenUrlClick(value);
			case "open_file" -> new OpenFileClick(value);
			case "run_command" -> new RunCommandClick(value);
			case "suggest_command" -> new SuggestCommandClick(value);
			case "change_page" -> new ChangePageClick(Integer.parseInt(value));
			case "copy_to_clipboard" -> new CopyToClipboardClick(value);
			case "show_dialog" -> new ShowDialogClick();
			case "custom" -> new CustomClick(value);
			default -> null;
		};
	}

	private @Nullable TextHover hover(HoverEvent<?> hover, AdventureRelease release) {
		if (hover.action() == HoverEvent.Action.SHOW_TEXT)
			return new ShowTextHover(text((Component) hover.value(), release));
		if (hover.action() == HoverEvent.Action.SHOW_ITEM) {
			HoverEvent.ShowItem item = (HoverEvent.ShowItem) hover.value();
			return new ShowItemHover(item.item().asString(), item.count());
		}
		if (hover.action() == HoverEvent.Action.SHOW_ENTITY) {
			HoverEvent.ShowEntity entity = (HoverEvent.ShowEntity) hover.value();
			return new ShowEntityHover(entity.type().asString(), entity.id(), optional(entity.name(), release));
		}

		return null;
	}
}
