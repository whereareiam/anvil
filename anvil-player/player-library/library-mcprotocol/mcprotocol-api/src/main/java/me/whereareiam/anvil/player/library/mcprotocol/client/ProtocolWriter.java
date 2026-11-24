package me.whereareiam.anvil.player.library.mcprotocol.client;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Writes the values of a custom payload as the game's protocol encodes them: variable-length integers, strings
 * prefixed with their UTF-8 length, booleans as one byte. A client segment sends the bytes it writes on a payload's
 * channel.
 */
public final class ProtocolWriter {
	private static final int SEGMENT_BITS = 0x7F;
	private static final int CONTINUE_BIT = 0x80;

	private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

	/**
	 * Writes an integer as a variable-length integer, seven bits a byte.
	 *
	 * @param value the integer
	 * @return this writer
	 */
	public @NotNull ProtocolWriter varInt(int value) {
		int rest = value;
		while ((rest & ~SEGMENT_BITS) != 0) {
			bytes.write((rest & SEGMENT_BITS) | CONTINUE_BIT);
			rest >>>= 7;
		}
		bytes.write(rest);

		return this;
	}

	/**
	 * Writes a string as its UTF-8 length followed by its bytes.
	 *
	 * @param value the string
	 * @return this writer
	 */
	public @NotNull ProtocolWriter string(@NotNull String value) {
		byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
		varInt(utf8.length);
		bytes.writeBytes(utf8);

		return this;
	}

	/**
	 * Writes a boolean as one byte, 1 for true.
	 *
	 * @param value the boolean
	 * @return this writer
	 */
	public @NotNull ProtocolWriter bool(boolean value) {
		bytes.write(value ? 1 : 0);

		return this;
	}

	/**
	 * Returns what was written.
	 *
	 * @return a new array with the bytes
	 */
	public byte @NotNull [] toByteArray() {
		return bytes.toByteArray();
	}
}
