plugins {
	id("module-assembly")
	id("packaging-shaded-root")
	id("module-library")
}

// Owning cloudburst-api makes this family its own API family, so the protocol library API it
// implements, worker contracts included, is shared explicitly.
architecture {
	sharedApis = setOf(projects.anvilProtocol.protocolApi.path)
}

description = "Version-isolated CloudburstMC Protocol clients for Bedrock players in Anvil"

dependencies {
	api(projects.anvilProtocol.protocolApi)

	implementation(projects.anvilProtocol.protocolCloudburst.cloudburstApi)
	// The worker that hosts this library ships as a bundle of its own, which every hosted library shares.
	implementation(projects.anvilProtocol.protocolWorker)
	implementation(libs.minecraft.auth)
	implementation(libs.slf4j.api)

	embedded(projects.anvilProtocol.protocolCloudburst.cloudburstCommon) { isTransitive = false }

	runtimeOnly(projects.anvilProtocol.protocolCloudburst.cloudburstClient)
	runtimeOnly(libs.slf4j.simple)
}
