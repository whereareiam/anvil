plugins {
	id("module-java")
}

description = "Scenario player management and protocol lifecycle ownership for Anvil"

dependencies {
	compileOnly(projects.anvilApi)
	compileOnly(projects.anvilPlayer.playerApi)

	testImplementation(projects.anvilApi)
	testImplementation(projects.anvilPlayer.playerApi)
}
