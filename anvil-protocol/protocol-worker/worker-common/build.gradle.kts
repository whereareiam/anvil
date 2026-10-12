plugins {
	id("module-java")
}

description = "Native worker host, worker shell, transport, segment verification, release catalog and stored accounts"

dependencies {
	implementation(libs.jackson.databind)
	implementation(libs.jackson.toml)
	implementation(libs.minecraft.auth)

	compileOnly(projects.anvilApi)
	compileOnly(projects.anvilProtocol.protocolApi)

	testImplementation(projects.anvilApi)
	testImplementation(projects.anvilCapability)
	testImplementation(projects.anvilCapability.capabilityProtocolApi)
	testImplementation(projects.anvilProtocol.protocolApi)
}
