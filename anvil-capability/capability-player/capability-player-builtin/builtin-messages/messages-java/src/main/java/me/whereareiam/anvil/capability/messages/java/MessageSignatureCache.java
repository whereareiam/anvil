package me.whereareiam.anvil.capability.messages.java;

import me.whereareiam.anvil.protocol.java.api.model.chat.MessageSignature;
import me.whereareiam.anvil.protocol.java.api.model.chat.PackedMessageSignature;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The vanilla client's cache of message signatures, which the server packs the signatures of later packets against.
 * Each player chat message pushes the signatures its sender had seen and then its own to the front, and moves the
 * signatures it pushes back down to the end; the server keeps the same cache for the client, so an index it sends
 * names the same signature here. The client starts a new cache with every login.
 */
final class MessageSignatureCache {
	/**
	 * Number of signatures the vanilla client keeps.
	 */
	private static final int CAPACITY = 128;

	private final MessageSignature[] entries = new MessageSignature[CAPACITY];

	/**
	 * Unpacks a signature: a whole signature is itself, an index names the cached signature.
	 *
	 * @param packed the packed signature
	 * @return the signature, or {@code null} when the cache holds none at that index
	 */
	@Nullable MessageSignature unpack(@NotNull PackedMessageSignature packed) {
		if (packed.getSignature() != null) return packed.getSignature();
		if (packed.getCacheIndex() >= CAPACITY) return null;

		return entries[packed.getCacheIndex()];
	}

	/**
	 * Pushes what a player chat message carries, as the vanilla client does.
	 *
	 * @param lastSeen the signatures the sender had seen, oldest first, already unpacked
	 * @param signature the message's own signature, or {@code null} when it is unsigned
	 */
	void push(@NotNull List<MessageSignature> lastSeen, @Nullable MessageSignature signature) {
		Deque<MessageSignature> queue = new ArrayDeque<>(lastSeen);
		if (signature != null) queue.add(signature);
		Set<MessageSignature> pushed = new HashSet<>(queue);

		for (int index = 0; !queue.isEmpty() && index < CAPACITY; index++) {
			MessageSignature previous = entries[index];
			entries[index] = queue.removeLast();
			if (previous != null && !pushed.contains(previous)) queue.addFirst(previous);
		}
	}
}
