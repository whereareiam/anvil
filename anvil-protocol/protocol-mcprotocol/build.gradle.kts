plugins {
	id("module-assembly")
	id("packaging-shaded-root")
	id("module-library")
}

// Owning mcprotocol-api makes this family its own API family, so the protocol library API and the worker API it
// implements are shared explicitly.
architecture {
	sharedApis = setOf(
		projects.anvilProtocol.protocolApi.path,
		projects.anvilProtocol.protocolWorker.workerApi.path,
	)
}

description = "Version-isolated MCProtocolLib clients and workers for Anvil"

dependencies {
	api(projects.anvilProtocol.protocolApi)

	implementation(projects.anvilProtocol.protocolMcprotocol.mcprotocolApi)
	// The worker that hosts this library ships as a bundle of its own, which every hosted library shares.
	implementation(projects.anvilProtocol.protocolWorker)
	implementation(libs.minecraft.auth)
	implementation(libs.slf4j.api)

	embedded(projects.anvilProtocol.protocolMcprotocol.mcprotocolCommon) { isTransitive = false }

	runtimeOnly(projects.anvilProtocol.protocolMcprotocol.mcprotocolClient)
	runtimeOnly(libs.slf4j.simple)
}
