plugins {
	id("module-assembly")
}

description = "Executable CLI and IDE entry points using Anvil's default engine assembly"

dependencies {
	implementation(projects.anvilLauncher)
	implementation(projects.anvilPlayer.playerApi)
	implementation(projects.anvilTooling.toolingRunner)
}
