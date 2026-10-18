package me.whereareiam.anvil.protocol.java.api.model.chat;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * The signature a player's chat message was signed with, which identifies the message from Minecraft 1.19 on. Two
 * signatures are equal when their bytes are. The signature keeps its own copy of the bytes, so it stays unchanged
 * whatever happens to the array it was created from or to an array {@link #getBytes()} returned.
 */
@Value
public class MessageSignature {
	/**
	 * The signature's bytes, 256 of them.
	 */
	byte @NotNull [] bytes;

	/**
	 * Creates the signature from a copy of its bytes.
	 *
	 * @param bytes the signature's bytes
	 */
	public MessageSignature(byte @NotNull [] bytes) {
		this.bytes = bytes.clone();
	}

	/**
	 * Returns a copy of the signature's bytes.
	 *
	 * @return the signature's bytes
	 */
	public byte @NotNull [] getBytes() {
		return bytes.clone();
	}
}
