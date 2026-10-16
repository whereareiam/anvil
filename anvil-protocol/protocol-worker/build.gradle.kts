plugins {
	id("module-assembly")
	id("packaging-shaded-root")
}

description = "Native player worker that hosts protocol libraries in version-isolated JVMs"

dependencies {
	api(projects.anvilProtocol.protocolApi)

	implementation(libs.jackson.databind)
	implementation(libs.jackson.toml)
	implementation(libs.minecraft.auth)

	embedded(projects.anvilProtocol.protocolWorker.workerCommon) { isTransitive = false }
}
