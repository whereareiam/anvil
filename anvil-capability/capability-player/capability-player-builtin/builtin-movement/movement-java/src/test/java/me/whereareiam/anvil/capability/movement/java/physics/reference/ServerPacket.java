package me.whereareiam.anvil.capability.movement.java.physics.reference;

import com.fasterxml.jackson.databind.JsonNode;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

/**
 * A packet the server of a course sends before a tick runs, as a {@code [[server]]} entry names it: a teleport, a ping
 * or an animation of the player's own entity; tick {@code -1} is a join's handshake.
 */
sealed interface ServerPacket permits Animate, Ping, Teleport {
	/**
	 * Returns the tick the packet comes before, {@code -1} during a join's handshake.
	 */
	int tick();

	/**
	 * Returns the packet as the player's library reports it for a release.
	 *
	 * @param release the release's Minecraft version
	 * @return the packet
	 */
	@NotNull ClientboundPacket packet(@NotNull MinecraftVersion release);

	/**
	 * Reads a {@code [[server]]} entry.
	 */
	static @NotNull ServerPacket read(@NotNull JsonNode entry) {
		if (entry.has("ping")) return Ping.read(entry);
		if (entry.has("animate")) return Animate.read(entry);

		return Teleport.read(entry);
	}
}
