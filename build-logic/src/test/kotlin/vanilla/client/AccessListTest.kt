package me.whereareiam.anvil.buildlogic.vanilla.client

import org.gradle.api.GradleException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AccessListTest {
	@Test
	fun `reads classes, methods and fields with their access, skipping comments and blank lines`() {
		val list = AccessList.parse("""
			# The client's chunk storage.
			public net.minecraft.client.multiplayer.ClientChunkCache${'$'}Storage
			public net.minecraft.client.multiplayer.ClientChunkCache${'$'}Storage getIndex(II)I # by column

			public-f net.minecraft.client.Minecraft options:Lnet/minecraft/client/Options;
			protected net.minecraft.client.renderer.LevelRenderer <init>(Lnet/minecraft/client/Minecraft;)V
		""".trimIndent(), "accesstransformer.cfg")

		assertEquals(listOf(
			AccessList.Entry(AccessList.Access.PUBLIC, false, "net/minecraft/client/multiplayer/ClientChunkCache\$Storage", null),
			AccessList.Entry(AccessList.Access.PUBLIC, false, "net/minecraft/client/multiplayer/ClientChunkCache\$Storage",
				AccessList.Member("getIndex", "(II)I", true)),
			AccessList.Entry(AccessList.Access.PUBLIC, true, "net/minecraft/client/Minecraft",
				AccessList.Member("options", "Lnet/minecraft/client/Options;", false)),
			AccessList.Entry(AccessList.Access.PROTECTED, false, "net/minecraft/client/renderer/LevelRenderer",
				AccessList.Member("<init>", "(Lnet/minecraft/client/Minecraft;)V", true)),
		), list.entries)
	}

	@Test
	fun `names the line of an entry it cannot read`() {
		val access = assertThrows<GradleException> { AccessList.parse("\nprivate a.B c:I", "family.cfg") }
		val member = assertThrows<GradleException> { AccessList.parse("public a.B c", "family.cfg") }
		val shape = assertThrows<GradleException> { AccessList.parse("public", "family.cfg") }

		assertTrue(access.message!!.startsWith("family.cfg:2: access must be public or protected"), access.message)
		assertTrue(member.message!!.startsWith("family.cfg:1: 'c' is neither a method"), member.message)
		assertTrue(shape.message!!.startsWith("family.cfg:1: expected"), shape.message)
	}

	@Test
	fun `refuses a class or member named twice, within a list and across a family's and a release's`() {
		val within = assertThrows<GradleException> { AccessList.parse("public a.B c:I\npublic-f a.B c:I", "release.cfg") }
		val family = AccessList.parse("public a.B\npublic a.B c:I", "family.cfg")
		val release = AccessList.parse("public a.B d()V", "release.cfg")

		assertEquals("release.cfg: a.B c:I is named more than once", within.message)
		assertEquals(3, (family + release).entries.size)
		val across = assertThrows<GradleException> { family + AccessList.parse("protected a.B", "release.cfg") }
		assertEquals("the access lists: a.B is named more than once", across.message)
	}

	@Test
	fun `groups the entries by their class`() {
		val list = AccessList.parse("public a.B c:I\npublic a.C\npublic a.B d()V", "family.cfg")

		assertEquals(listOf("a/B", "a/C"), list.byOwner().keys.toList())
		assertEquals(listOf("c:I", "d()V"), list.byOwner().getValue("a/B").map { it.member.toString() })
	}
}
