package me.whereareiam.anvil.protocol.mcprotocol.client;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * The brand a vanilla client announces to the server: the custom payload on the {@code minecraft:brand} channel that
 * carries {@code vanilla} as a protocol string. Every release since Minecraft 1.13 names the channel and writes the
 * brand the same way, so client segments send this payload in their release's custom payload packet: from Minecraft
 * 1.20.2 on right after acknowledging the login, before the client information, and before that right after the
 * client information that answers the game's start.
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

	private static final int SEGMENT_BITS = 0x7F;
	private static final int CONTINUE_BIT = 0x80;

	/**
	 * Returns the payload of the brand a vanilla client announces: the brand's UTF-8 length as a variable-length
	 * integer, followed by its bytes.
	 *
	 * @return a new array with the payload
	 */
	public static byte @NotNull [] payload() {
		byte[] brand = VANILLA.getBytes(StandardCharsets.UTF_8);
		ByteArrayOutputStream payload = new ByteArrayOutputStream(brand.length + 1);
		int length = brand.length;
		while ((length & ~SEGMENT_BITS) != 0) {
			payload.write((length & SEGMENT_BITS) | CONTINUE_BIT);
			length >>>= 7;
		}
		payload.write(length);
		payload.writeBytes(brand);

		return payload.toByteArray();
	}
}
