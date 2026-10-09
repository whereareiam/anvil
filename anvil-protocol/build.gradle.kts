plugins {
	id("jvm")
}

description = "Scenario player management and protocol lifecycle ownership for Anvil"

dependencies {
	compileOnly(projects.anvilApi)
	compileOnly(projects.anvilProtocol.protocolApi)

	testImplementation(projects.anvilApi)
	testImplementation(projects.anvilProtocol.protocolApi)
}
