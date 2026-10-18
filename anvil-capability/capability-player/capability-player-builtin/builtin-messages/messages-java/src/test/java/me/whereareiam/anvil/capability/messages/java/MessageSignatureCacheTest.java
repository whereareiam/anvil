package me.whereareiam.anvil.capability.messages.java;

import me.whereareiam.anvil.protocol.java.api.model.chat.MessageSignature;
import me.whereareiam.anvil.protocol.java.api.model.chat.PackedMessageSignature;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MessageSignatureCacheTest {
	@Test
	void pushesTheMessageItselfToTheFrontAfterTheSignaturesItsSenderHadSeen() {
		MessageSignatureCache cache = new MessageSignatureCache();

		cache.push(List.of(signature(1), signature(2)), signature(3));

		assertEquals(signature(3), cache.unpack(PackedMessageSignature.cached(0)));
		assertEquals(signature(2), cache.unpack(PackedMessageSignature.cached(1)));
		assertEquals(signature(1), cache.unpack(PackedMessageSignature.cached(2)));
		assertNull(cache.unpack(PackedMessageSignature.cached(3)));
	}

	@Test
	void movesEarlierSignaturesDownWithoutRepeatingThosePushedAgain() {
		MessageSignatureCache cache = new MessageSignatureCache();
		cache.push(List.of(), signature(1));
		cache.push(List.of(), signature(2));

		cache.push(List.of(signature(1)), signature(3));

		assertEquals(signature(3), cache.unpack(PackedMessageSignature.cached(0)));
		assertEquals(signature(1), cache.unpack(PackedMessageSignature.cached(1)));
		assertEquals(signature(2), cache.unpack(PackedMessageSignature.cached(2)));
		assertNull(cache.unpack(PackedMessageSignature.cached(3)));
	}

	@Test
	void pushesNothingOfItsOwnForAnUnsignedMessage() {
		MessageSignatureCache cache = new MessageSignatureCache();

		cache.push(List.of(signature(1)), null);

		assertEquals(signature(1), cache.unpack(PackedMessageSignature.cached(0)));
		assertNull(cache.unpack(PackedMessageSignature.cached(1)));
	}

	@Test
	void keepsTheNewestHundredTwentyEightSignatures() {
		MessageSignatureCache cache = new MessageSignatureCache();

		IntStream.range(0, 130).forEach(seed -> cache.push(List.of(), signature(seed)));

		assertEquals(signature(129), cache.unpack(PackedMessageSignature.cached(0)));
		assertEquals(signature(2), cache.unpack(PackedMessageSignature.cached(127)));
		assertNull(cache.unpack(PackedMessageSignature.cached(128)));
	}

	@Test
	void unpacksAWholeSignatureAsItself() {
		assertEquals(signature(9), new MessageSignatureCache().unpack(PackedMessageSignature.full(signature(9))));
	}

	private static MessageSignature signature(int seed) {
		byte[] bytes = new byte[256];
		bytes[0] = (byte) seed;
		bytes[1] = (byte) (seed >> 8);

		return new MessageSignature(bytes);
	}
}
