package me.whereareiam.anvil.protocol.java.api.model.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SharedFlagsTest {
	@Test
	void readsEachFlagFromItsBit() {
		assertEquals(SharedFlags.builder().onFire(true).sneaking(true).invisible(true).glowing(true).build(),
				SharedFlags.of((byte) 0x63));
		assertEquals(SharedFlags.builder().sprinting(true).swimming(true).gliding(true).build(),
				SharedFlags.of((byte) 0x9C));
		assertEquals(SharedFlags.builder().build(), SharedFlags.of((byte) 0));
	}
}
