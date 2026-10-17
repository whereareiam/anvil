package me.whereareiam.anvil.protocol.mcprotocol.adventure;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.KeybindComponent;
import net.kyori.adventure.text.SelectorComponent;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import org.jetbrains.annotations.NotNull;

/**
 * Flattens the Adventure components of MCProtocolLib releases to the plain text the Java Edition protocol
 * carries. Text appears as written, keybinds and selectors as their names, and a translation as its key followed by
 * each argument, separated by spaces, since a client without the game's language files cannot translate it.
 *
 * <p>Only segments of releases whose components are Adventure's use it; Adventure comes from the release's own
 * closure. It calls only Adventure API that has not changed since Adventure 4.0, so the segments of every such
 * release share it.</p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AdventureText {
	/**
	 * Flattens a component with its children.
	 *
	 * @param component component to flatten
	 * @return the component as plain text
	 */
	public static @NotNull String plain(@NotNull Component component) {
		StringBuilder text = new StringBuilder();
		append(component, text);

		return text.toString();
	}

	private static void append(Component component, StringBuilder text) {
		if (component instanceof TextComponent value) text.append(value.content());
		if (component instanceof KeybindComponent value) text.append(value.keybind());
		if (component instanceof SelectorComponent value) text.append(value.pattern());
		if (component instanceof TranslatableComponent value) {
			text.append(value.key());
			for (Component argument : value.args()) {
				text.append(' ');
				append(argument, text);
			}
		}
		for (Component child : component.children()) append(child, text);
	}
}
