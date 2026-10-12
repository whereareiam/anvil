package me.whereareiam.anvil.buildlogic.module

import me.whereareiam.anvil.buildlogic.module.SharedImplementations.Project
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SharedImplementationsTest {
	private val worker = Project(":anvil-protocol:protocol-worker", shared = true, api = false, dependencies = emptyList())
	private val api = Project(":anvil-protocol:protocol-api", shared = false, api = true, dependencies = emptyList())

	@Test
	fun `lets implementations of the shared implementation's group compile against it`() {
		val common = Project(":anvil-protocol:protocol-mcprotocol:mcprotocol-common", false, false, listOf(worker.path))
		val other = Project(":anvil-protocol:protocol-cloudburst:cloudburst-common", false, false, listOf(worker.path))

		assertEquals(emptyList<String>(), SharedImplementations.violations(listOf(worker, api, common, other)))
	}

	@Test
	fun `refuses consumers outside the group and targets that do not declare themselves shared`() {
		val outside = Project(":anvil-capability", false, false, listOf(worker.path))
		val undeclared = Project(":anvil-protocol:protocol-mcprotocol:mcprotocol-common", false, false, listOf(":anvil-protocol"))

		assertEquals(listOf(
			":anvil-capability:sharedImplementation -> :anvil-protocol:protocol-worker lies outside :anvil-protocol, the group the shared implementation serves",
			":anvil-protocol:protocol-mcprotocol:mcprotocol-common:sharedImplementation -> :anvil-protocol, which is no shared implementation; "
				+ "it must apply id(\"module-shared-implementation\")",
		), SharedImplementations.violations(listOf(worker, Project(":anvil-protocol", false, false, emptyList()), outside, undeclared)))
	}

	@Test
	fun `refuses APIs on either side, chains and shared implementations at the root`() {
		val consumingApi = Project(":anvil-protocol:protocol-mcprotocol:mcprotocol-api", false, true, listOf(worker.path))
		val sharedApi = Project(":anvil-protocol:worker-api", true, true, emptyList())
		val chained = Project(":anvil-protocol:protocol-chain", true, false, listOf(worker.path))
		val rootShared = Project(":anvil-shared", true, false, emptyList())

		assertEquals(listOf(
			":anvil-protocol:protocol-mcprotocol:mcprotocol-api:sharedImplementation is declared by an API, which may depend only on APIs",
			":anvil-protocol:worker-api applies module-shared-implementation, but an API is no shared implementation",
			":anvil-protocol:protocol-chain is a shared implementation and must not declare sharedImplementation dependencies itself",
			":anvil-shared applies module-shared-implementation, but a shared implementation serves the families of its parent and must not be a root project",
		), SharedImplementations.violations(listOf(worker, consumingApi, sharedApi, chained, rootShared)))
	}

	@Test
	fun `refuses a project inside the shared implementation`() {
		val inside = Project(":anvil-protocol:protocol-worker:worker-fixture", false, false, listOf(worker.path))

		assertEquals(
			listOf(":anvil-protocol:protocol-worker:worker-fixture:sharedImplementation -> :anvil-protocol:protocol-worker names the shared implementation that contains it"),
			SharedImplementations.violations(listOf(worker, inside)),
		)
	}
}
