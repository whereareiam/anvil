plugins {
	id("module-assembly")
	id("packaging-shaded-root")
}

// Owning worker-api makes this family its own API family, so the protocol library API it hosts libraries behind is
// shared explicitly.
architecture {
	sharedApis = setOf(projects.anvilProtocol.protocolApi.path)
}

description = "Native player worker that hosts protocol libraries in version-isolated JVMs"

dependencies {
	api(projects.anvilProtocol.protocolWorker.workerApi)

	implementation(libs.jackson.databind)
	implementation(libs.jackson.toml)

	embedded(projects.anvilProtocol.protocolWorker.workerCommon) { isTransitive = false }
}
