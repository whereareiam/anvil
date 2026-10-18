package me.whereareiam.anvil.protocol.mcprotocol.client;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class ClientBrandTest {
	@Test
	void writesTheVanillaBrandAsAProtocolString() {
		byte[] brand = "vanilla".getBytes(StandardCharsets.UTF_8);
		byte[] expected = new byte[brand.length + 1];
		expected[0] = (byte) brand.length;
		System.arraycopy(brand, 0, expected, 1, brand.length);

		assertArrayEquals(expected, ClientBrand.payload());
	}
}
