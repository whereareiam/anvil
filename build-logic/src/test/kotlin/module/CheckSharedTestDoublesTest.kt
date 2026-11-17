package me.whereareiam.anvil.buildlogic.module

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CheckSharedTestDoublesTest {
	private val contracts = mapOf("demo.Channel" to "TestChannel", "demo.Context" to "TestContext")

	@Test
	fun `finds the contracts with a shared test double among the interfaces a class names`() {
		assertEquals(
			listOf("demo.Context", "demo.Channel"),
			CheckSharedTestDoubles.implementedContracts(listOf("demo/Context", "java/lang/Runnable", "demo/Channel"), contracts),
		)
	}

	@Test
	fun `accepts a class that implements none of them`() {
		assertEquals(emptyList<String>(), CheckSharedTestDoubles.implementedContracts(listOf("demo/ChannelListener"), contracts))
	}
}
