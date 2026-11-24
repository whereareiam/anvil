package me.whereareiam.anvil.player.library.mcprotocol.client;

import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;

/**
 * Reads the values of a received custom payload as the game's protocol encodes them, in the order they were written;
 * the counterpart of {@link ProtocolWriter}.
 */
public final class ProtocolReader {
	private static final int SEGMENT_BITS = 0x7F;
	private static final int CONTINUE_BIT = 0x80;
	private static final int MAXIMUM_SHIFT = 32;

	private final byte[] bytes;
	private int position;

	/**
	 * Creates a reader at the start of a payload.
	 *
	 * @param bytes the payload's bytes
	 */
	public ProtocolReader(byte @NotNull [] bytes) {
		this.bytes = bytes;
	}

	/**
	 * Reads a variable-length integer.
	 *
	 * @return the integer
	 * @throws IllegalArgumentException when the payload ends inside it or it is longer than five bytes
	 */
	public int varInt() {
		int value = 0;
		for (int shift = 0; shift < MAXIMUM_SHIFT + 7; shift += 7) {
			int next = next();
			value |= (next & SEGMENT_BITS) << shift;
			if ((next & CONTINUE_BIT) == 0) return value;
		}

		throw new IllegalArgumentException("A variable-length integer is longer than five bytes");
	}

	/**
	 * Reads a string written as its UTF-8 length followed by its bytes.
	 *
	 * @return the string
	 * @throws IllegalArgumentException when the payload ends inside it
	 */
	public @NotNull String string() {
		int length = varInt();
		if (length < 0 || position + length > bytes.length)
			throw new IllegalArgumentException("A string runs past the end of the payload");

		String value = new String(bytes, position, length, StandardCharsets.UTF_8);
		position += length;

		return value;
	}

	/**
	 * Reads a boolean written as one byte.
	 *
	 * @return whether the byte is not 0
	 * @throws IllegalArgumentException when the payload ends
	 */
	public boolean bool() {
		return next() != 0;
	}

	private int next() {
		if (position >= bytes.length) throw new IllegalArgumentException("The payload ends early");

		return bytes[position++] & 0xFF;
	}
}
