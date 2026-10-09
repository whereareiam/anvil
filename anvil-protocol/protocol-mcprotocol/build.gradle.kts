plugins {
	id("module-assembly")
	id("packaging-shaded-root")
	id("module-library")
}

// Owning mcprotocol-api makes this family its own API family, so the protocol library API it implements is shared explicitly.
architecture {
	sharedApis = setOf(projects.anvilProtocol.protocolApi.path)
}

description = "Version-isolated MCProtocolLib clients and workers for Anvil"

dependencies {
	api(projects.anvilProtocol.protocolApi)

	implementation(projects.anvilProtocol.protocolMcprotocol.mcprotocolApi)
	implementation(libs.jackson.databind)
	implementation(libs.jackson.toml)
	implementation(libs.minecraft.auth)
	implementation(libs.slf4j.api)

	embedded(projects.anvilProtocol.protocolMcprotocol.mcprotocolCommon) { isTransitive = false }

	runtimeOnly(projects.anvilProtocol.protocolMcprotocol.mcprotocolClient)
	runtimeOnly(libs.slf4j.simple)
}
