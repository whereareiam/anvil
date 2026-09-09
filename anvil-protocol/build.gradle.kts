plugins {
	alias(libs.plugins.toolkit.architecture)
	id("unit")
}

description = "Scenario player management and protocol lifecycle ownership for Anvil"

dependencies {
	compileOnly(projects.anvilApi)
	compileOnly(projects.anvilProtocol.protocolApi)

	testImplementation(projects.anvilApi)
	testImplementation(projects.anvilProtocol.protocolApi)
}

tasks.named("build") {
	dependsOn(
		":anvil-protocol:protocol-api:build",
		":anvil-protocol:protocol-mcprotocol:build"
	)
}
