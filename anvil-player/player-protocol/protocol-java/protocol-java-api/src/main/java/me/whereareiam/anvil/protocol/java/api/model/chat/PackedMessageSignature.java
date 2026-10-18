package me.whereareiam.anvil.protocol.java.api.model.chat;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A message signature as the server packs it: the index of a signature the client keeps in its signature cache, or,
 * when the client cannot know it yet, the whole signature. The client fills its cache from the player chat it
 * receives, so only the client can unpack an index.
 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PackedMessageSignature {
	/**
	 * The index into the client's signature cache, or {@code -1} when the signature is sent whole.
	 */
	int cacheIndex;
	/**
	 * The whole signature, or {@code null} when the signature is an index into the client's cache.
	 */
	@Nullable MessageSignature signature;

	/**
	 * Packs a signature as an index into the client's signature cache.
	 *
	 * @param cacheIndex the index, from zero
	 * @return the packed signature
	 */
	public static @NotNull PackedMessageSignature cached(int cacheIndex) {
		if (cacheIndex < 0) throw new IllegalArgumentException("A cache index is not negative: " + cacheIndex);

		return new PackedMessageSignature(cacheIndex, null);
	}

	/**
	 * Packs a whole signature.
	 *
	 * @param signature the signature
	 * @return the packed signature
	 */
	public static @NotNull PackedMessageSignature full(@NotNull MessageSignature signature) {
		return new PackedMessageSignature(-1, signature);
	}
}
