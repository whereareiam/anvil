package me.whereareiam.anvil.capability.movement.java.physics.reference;

import com.fasterxml.jackson.databind.JsonNode;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundPing;
import me.whereareiam.anvil.edition.java.protocol.packet.inventory.container.ClientboundContainerAck;
import org.jetbrains.annotations.NotNull;

/**
 * A ping the server of a course sends before a tick runs, or during a join's handshake, as {@code [[server]]} names it.
 *
 * @param tick the tick it comes before, {@code -1} during a join's handshake
 * @param id the ID the client answers with, which fits the action number of a transaction
 */
record Ping(int tick, short id) implements ServerPacket {
	private static final MinecraftVersion PING = MinecraftVersion.parse("1.17");
	private static final int INVENTORY = 0;

	/**
	 * Reads a {@code [[server]]} entry that pings.
	 */
	static @NotNull Ping read(@NotNull JsonNode entry) {
		return new Ping(entry.path("tick").asInt(), (short) entry.path("ping").path("id").asInt());
	}

	/**
	 * Returns the ping as the player's library reports it: from Minecraft 1.17 the ping packet, before it a rejected
	 * transaction of the player's inventory, which servers send a client of those releases to the same end.
	 */
	@Override
	public @NotNull ClientboundPacket packet(@NotNull MinecraftVersion release) {
		if (release.isAtLeast(PING)) return new ClientboundPing(id);

		return new ClientboundContainerAck(INVENTORY, id, false);
	}
}
