package me.whereareiam.anvil.protocol.mcprotocol.adventure;

import me.whereareiam.anvil.protocol.api.model.text.ShadowColor;
import me.whereareiam.anvil.protocol.api.model.text.content.TextContent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.NBTComponent;
import net.kyori.adventure.text.SelectorComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.Style;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * What one release's Adventure keeps differently from the Adventure API that has not changed since 4.0, for
 * {@link AdventureText#text(Component, AdventureRelease)}. A segment implements it with the accessors its releases
 * share, and {@code checkSegmentLinkage} verifies them against every release of the segment. A member the release's
 * Adventure does not have yet keeps its default.
 *
 * <pre>{@code
 * final class ReleaseAdventure implements AdventureRelease {
 *     public @Nullable String clickValue(@NotNull ClickEvent click) {
 *         return click.value();
 *     }
 * }
 * }</pre>
 */
public interface AdventureRelease {
	/**
	 * Reads what a click acts on: {@code value()} before Adventure 4.22, the click's {@code payload()} from it on.
	 *
	 * @param click the click of a component
	 * @return the URL, file, command, text, page number or custom identifier the click acts on, or {@code null} for a
	 * dialog
	 */
	@Nullable String clickValue(@NotNull ClickEvent click);

	/**
	 * Reads a translation's fallback, which Adventure 4.13 added.
	 *
	 * @param translation the translation
	 * @return its fallback, or {@code null}
	 */
	default @Nullable String fallback(@NotNull TranslatableComponent translation) {
		return null;
	}

	/**
	 * Reads a selector's separator, which Adventure 4.8 added.
	 *
	 * @param selector the selector
	 * @return its separator, or {@code null}
	 */
	default @Nullable Component separator(@NotNull SelectorComponent selector) {
		return null;
	}

	/**
	 * Reads the separator of an NBT component, which Adventure 4.8 added.
	 *
	 * @param nbt the NBT component
	 * @return its separator, or {@code null}
	 */
	default @Nullable Component separator(@NotNull NBTComponent<?, ?> nbt) {
		return null;
	}

	/**
	 * Reads a style's shadow colour, which Adventure 4.18 added.
	 *
	 * @param style the style
	 * @return its shadow colour, or {@code null}
	 */
	default @Nullable ShadowColor shadowColor(@NotNull Style style) {
		return null;
	}

	/**
	 * Reads the content of a component kind the release's Adventure adds, such as Adventure 4.25's object components.
	 *
	 * @param component a component of no kind Adventure 4.0 has
	 * @return its content, or {@code null} when the release has no such kind
	 */
	default @Nullable TextContent content(@NotNull Component component) {
		return null;
	}
}
