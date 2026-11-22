package me.whereareiam.anvil.capability.movement.java.physics.reference;

import com.fasterxml.jackson.databind.JsonNode;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.capability.movement.java.physics.TestClient;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.interaction.ClientboundAnimate;
import me.whereareiam.anvil.edition.java.protocol.type.interaction.AnimateAction;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * An animation the server of a course plays of the player's own entity before a tick runs, as {@code [[server]]} names
 * it, such as a swing of one of its arms.
 *
 * @param tick the tick it comes before
 * @param action what the server animates
 */
record Animate(int tick, @NotNull AnimateAction action) implements ServerPacket {
	/**
	 * Reads a {@code [[server]]} entry that animates the player.
	 */
	static @NotNull Animate read(@NotNull JsonNode entry) {
		String animation = entry.path("animate").path("animation").asText();
		return new Animate(entry.path("tick").asInt(), AnimateAction.valueOf(animation.toUpperCase(Locale.ROOT)));
	}

	/**
	 * Returns the animate packet of the player's own entity, which every release reports alike.
	 */
	@Override
	public @NotNull ClientboundPacket packet(@NotNull MinecraftVersion release) {
		return new ClientboundAnimate(TestClient.ENTITY_ID, action);
	}
}
