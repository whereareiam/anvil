package me.whereareiam.anvil.protocol.mcprotocol.adventure;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.KeybindComponent;
import net.kyori.adventure.text.SelectorComponent;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Function;

/**
 * Flattens the Adventure components of MCProtocolLib releases to the plain text the Java Edition protocol
 * carries. Text appears as written, keybinds and selectors as their names, and a translation as its key followed by
 * each argument, separated by spaces, since a client without the game's language files cannot translate it.
 *
 * <p>Only segments of releases whose components are Adventure's use it; Adventure comes from the release's own
 * closure. Adventure 4.15 added a translation's {@code arguments()} and Adventure 5 removed its {@code args()}, so the
 * earliest segment of each chain creates the text with the accessor its releases share, which
 * {@code checkSegmentLinkage} verifies; everything else calls only Adventure API that has not changed since
 * Adventure 4.0.</p>
 *
 * <pre>{@code
 * private static final AdventureText TEXT = new AdventureText(TranslatableComponent::arguments);
 *
 * String reason = TEXT.plain(disconnect.getReason());
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
}
