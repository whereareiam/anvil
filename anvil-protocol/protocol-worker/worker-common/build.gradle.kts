plugins {
	id("module-java")
}

description = "Native worker host, worker shell, transport, segment verification and release catalog"

dependencies {
	implementation(libs.jackson.databind)
	implementation(libs.jackson.toml)

	compileOnly(projects.anvilApi)
	compileOnly(projects.anvilProtocol.protocolApi)
	compileOnly(projects.anvilProtocol.protocolWorker.workerApi)

	testImplementation(projects.anvilApi)
	testImplementation(projects.anvilCapability)
	testImplementation(projects.anvilCapability.capabilityProtocolApi)
	testImplementation(projects.anvilProtocol.protocolApi)
	testImplementation(projects.anvilProtocol.protocolWorker.workerApi)
}
