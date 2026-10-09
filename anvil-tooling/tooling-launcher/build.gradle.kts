plugins {
	id("assembly")
}

description = "Executable CLI and IDE entry points using Anvil's default engine assembly"

dependencies {
	implementation(projects.anvilLauncher)
	implementation(projects.anvilProtocol.protocolApi)
	implementation(projects.anvilTooling.toolingRunner)
}
