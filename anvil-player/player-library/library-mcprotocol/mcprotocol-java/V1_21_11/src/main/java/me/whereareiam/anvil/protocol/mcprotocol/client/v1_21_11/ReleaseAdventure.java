package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.protocol.api.model.text.ShadowColor;
import me.whereareiam.anvil.protocol.api.model.text.content.PlayerHeadContent;
import me.whereareiam.anvil.protocol.api.model.text.content.SpriteContent;
import me.whereareiam.anvil.protocol.api.model.text.content.TextContent;
import me.whereareiam.anvil.protocol.mcprotocol.adventure.AdventureRelease;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.NBTComponent;
import net.kyori.adventure.text.ObjectComponent;
import net.kyori.adventure.text.SelectorComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.object.PlayerHeadObjectContents;
import net.kyori.adventure.text.object.SpriteObjectContents;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * What the Adventure of the MCProtocolLib releases from 1.21.11 on, 4.25 and 5, keeps differently: a click's value
 * is a typed payload, translations have fallbacks, selectors and NBT components have separators, styles have shadow
 * colours, and object components draw sprites and player heads.
 */
public final class ReleaseAdventure implements AdventureRelease {
	@Override
	public @Nullable String clickValue(@NotNull ClickEvent click) {
		ClickEvent.Payload payload = click.payload();
		if (payload instanceof ClickEvent.Payload.Text text) return text.value();
		if (payload instanceof ClickEvent.Payload.Int page) return Integer.toString(page.integer());
		if (payload instanceof ClickEvent.Payload.Custom custom) return custom.key().asString();

		return null;
	}

	@Override
	public @Nullable String fallback(@NotNull TranslatableComponent translation) {
		return translation.fallback();
	}

	@Override
	public @Nullable Component separator(@NotNull SelectorComponent selector) {
		return selector.separator();
	}

	@Override
	public @Nullable Component separator(@NotNull NBTComponent<?, ?> nbt) {
		return nbt.separator();
	}

	@Override
	public @Nullable ShadowColor shadowColor(@NotNull Style style) {
		return style.shadowColor() == null ? null : new ShadowColor(style.shadowColor().value());
	}

	@Override
	public @Nullable TextContent content(@NotNull Component component) {
		if (!(component instanceof ObjectComponent object)) return null;
		if (object.contents() instanceof SpriteObjectContents sprite)
			return new SpriteContent(sprite.atlas().asString(), sprite.sprite().asString());
		if (!(object.contents() instanceof PlayerHeadObjectContents head)) return null;

		return new PlayerHeadContent(head.name(), head.id(), head.texture() == null ? null : head.texture().asString(), head.hat());
	}
}
