package me.whereareiam.anvil.protocol.mcprotocol.adventure;

import me.whereareiam.anvil.protocol.api.model.text.ShadowColor;
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
import net.kyori.adventure.text.NBTComponent;
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

/**
 * Translates the Adventure components of MCProtocolLib releases to the {@link Text} the Java Edition protocol carries,
 * with every component's content, style and children, through Adventure API that has not changed since Adventure 4.0.
 *
 * <p>Each chain of client segments extends it once, as its {@code TextTranslator}, with the accessors its releases'
 * Adventure keeps differently: a translation's arguments, which Adventure 4.15 renamed, and a click's value, which
 * Adventure 4.22 moved into a payload, and, where the releases have them, a translation's fallback, the separators of
 * selectors and NBT components, the shadow colour and object components. {@code checkSegmentLinkage} verifies each
 * subclass against every release of its segment, and a later segment whose Adventure moved further brings its own
 * subclass instead. The segment's adapter creates its subclass and hands it to the translations that read text.</p>
 *
 * <pre>{@code
 * final class TextTranslator extends ComponentTranslator {
 *     protected List<? extends ComponentLike> arguments(TranslatableComponent translation) {
 *         return translation.arguments();
 *     }
 *
 *     protected String clickValue(ClickEvent click) {
 *         return click.value();
 *     }
 * }
 *
 * String reason = text.translate(disconnect.getReason()).plain();
 * }</pre>
 */
public abstract class ComponentTranslator {
	/**
	 * Translates a component with its children, keeping the content, style and children of each.
	 *
	 * @param component component to translate
	 * @return the component as text
	 * @throws IllegalArgumentException when the component is of a kind neither Adventure 4.0 nor the release has
	 */
	public final @NotNull Text translate(@NotNull Component component) {
		return Text.builder()
				.content(read(component))
				.style(style(component.style()))
				.children(component.children().stream().map(this::translate).toList())
				.build();
	}

	/**
	 * Reads a translation's arguments: {@code args()} before Adventure 4.15, {@code arguments()} from it on.
	 *
	 * @param translation the translation
	 * @return its arguments, in order
	 */
	protected abstract @NotNull List<? extends ComponentLike> arguments(@NotNull TranslatableComponent translation);

	/**
	 * Reads what a click acts on: {@code value()} before Adventure 4.22, the click's {@code payload()} from it on.
	 *
	 * @param click the click of a component
	 * @return the URL, file, command, text, page number or custom identifier the click acts on, or {@code null} for a
	 * dialog
	 */
	protected abstract @Nullable String clickValue(@NotNull ClickEvent click);

	/**
	 * Reads a translation's fallback, which Adventure 4.13 added.
	 *
	 * @param translation the translation
	 * @return its fallback, or {@code null} when the release's Adventure has none
	 */
	protected @Nullable String fallback(@NotNull TranslatableComponent translation) {
		return null;
	}

	/**
	 * Reads a selector's separator, which Adventure 4.8 added.
	 *
	 * @param selector the selector
	 * @return its separator, or {@code null} when it has none or the release's Adventure has none
	 */
	protected @Nullable Component separator(@NotNull SelectorComponent selector) {
		return null;
	}

	/**
	 * Reads the separator of an NBT component, which Adventure 4.8 added.
	 *
	 * @param nbt the NBT component
	 * @return its separator, or {@code null} when it has none or the release's Adventure has none
	 */
	protected @Nullable Component separator(@NotNull NBTComponent<?, ?> nbt) {
		return null;
	}

	/**
	 * Reads a style's shadow colour, which Adventure 4.18 added.
	 *
	 * @param style the style
	 * @return its shadow colour, or {@code null} when it sets none or the release's Adventure has none
	 */
	protected @Nullable ShadowColor shadowColor(@NotNull Style style) {
		return null;
	}

	/**
	 * Reads the content of a component kind the release's Adventure adds, such as Adventure 4.25's object components.
	 *
	 * @param component a component of no kind Adventure 4.0 has
	 * @return its content, or {@code null} when the release has no such kind
	 */
	protected @Nullable TextContent content(@NotNull Component component) {
		return null;
	}

	private TextContent read(Component component) {
		if (component instanceof TextComponent value) return new LiteralContent(value.content());
		if (component instanceof TranslatableComponent value)
			return new TranslatedContent(value.key(), fallback(value), arguments(value).stream()
					.map(argument -> translate(argument.asComponent()))
					.toList());
		if (component instanceof KeybindComponent value) return new KeybindContent(value.keybind());
		if (component instanceof SelectorComponent value)
			return new SelectorContent(value.pattern(), optional(separator(value)));
		if (component instanceof ScoreComponent value) return new ScoreContent(value.name(), value.objective());
		if (component instanceof BlockNBTComponent value)
			return nbt(value.nbtPath(), value.interpret(), separator(value), NbtSource.BLOCK, value.pos().asString());
		if (component instanceof EntityNBTComponent value)
			return nbt(value.nbtPath(), value.interpret(), separator(value), NbtSource.ENTITY, value.selector());
		if (component instanceof StorageNBTComponent value)
			return nbt(value.nbtPath(), value.interpret(), separator(value), NbtSource.STORAGE, value.storage().asString());

		TextContent added = content(component);
		if (added == null) throw new IllegalArgumentException("Unknown text component " + component.getClass().getName());

		return added;
	}

	private NbtContent nbt(
			String path,
			boolean interpret,
			@Nullable Component separator,
			NbtSource source,
			String target
	) {
		return new NbtContent(path, interpret, optional(separator), source, target);
	}

	private @Nullable Text optional(@Nullable Component component) {
		return component == null ? null : translate(component);
	}

	private TextStyle style(Style style) {
		TextStyle.TextStyleBuilder sent = TextStyle.builder()
				.color(style.color() == null ? null : new TextColor(style.color().value()))
				.shadowColor(shadowColor(style))
				.font(style.font() == null ? null : style.font().asString())
				.insertion(style.insertion())
				.click(style.clickEvent() == null ? null : click(style.clickEvent()))
				.hover(style.hoverEvent() == null ? null : hover(style.hoverEvent()));
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
	private @Nullable TextClick click(ClickEvent click) {
		String action = ClickEvent.Action.NAMES.key(click.action());
		if (action == null) return null;

		String value = clickValue(click);
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

	private @Nullable TextHover hover(HoverEvent<?> hover) {
		if (hover.action() == HoverEvent.Action.SHOW_TEXT)
			return new ShowTextHover(translate((Component) hover.value()));
		if (hover.action() == HoverEvent.Action.SHOW_ITEM) {
			HoverEvent.ShowItem item = (HoverEvent.ShowItem) hover.value();
			return new ShowItemHover(item.item().asString(), item.count());
		}
		if (hover.action() == HoverEvent.Action.SHOW_ENTITY) {
			HoverEvent.ShowEntity entity = (HoverEvent.ShowEntity) hover.value();
			return new ShowEntityHover(entity.type().asString(), entity.id(), optional(entity.name()));
		}

		return null;
	}
}
