plugins {
	id("module-shared-implementation")
}

description = "Library-neutral native player worker that protocol library families embed: worker process, transport, worker shell, segments and release data"

dependencies {
	implementation(libs.jackson.databind)
	implementation(libs.jackson.toml)

	compileOnly(projects.anvilApi)
	compileOnly(projects.anvilProtocol.protocolApi)

	testImplementation(projects.anvilApi)
	testImplementation(projects.anvilCapability)
	testImplementation(projects.anvilCapability.capabilityProtocolApi)
	testImplementation(projects.anvilProtocol.protocolApi)
}
