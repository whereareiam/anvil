package me.whereareiam.anvil.player.library.mcprotocol.client;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.api.type.ClientLoader;
import org.jetbrains.annotations.NotNull;

/**
 * The brand a client announces to the server: the custom payload on the {@code minecraft:brand} channel that carries
 * the client's name as a protocol string, {@code vanilla} for a vanilla client and {@code neoforge} for a NeoForge
 * one. Every release since Minecraft 1.13 names the channel and writes the brand the same way; when a release sends
 * it is up to that release's client segment.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ClientBrand {
	/**
	 * The channel of the brand payload.
	 */
	public static final String CHANNEL = "minecraft:brand";
	/**
	 * The brand of a client without mods.
	 */
	public static final String VANILLA = "vanilla";
	/**
	 * The brand of a NeoForge client.
	 */
	public static final String NEOFORGE = "neoforge";

	/**
	 * Returns the payload of the brand a vanilla client announces.
	 *
	 * @return a new array with the payload
	 */
	public static byte @NotNull [] payload() {
		return payload(ClientLoader.VANILLA);
	}

	/**
	 * Returns the payload of the brand a client of a mod loader announces: the brand as a protocol string.
	 *
	 * @param loader the mod loader the client presents itself as
	 * @return a new array with the payload
	 */
	public static byte @NotNull [] payload(@NotNull ClientLoader loader) {
		String brand = switch (loader) {
			case VANILLA -> VANILLA;
			case NEOFORGE -> NEOFORGE;
		};

		return new ProtocolWriter().string(brand).toByteArray();
	}
}
