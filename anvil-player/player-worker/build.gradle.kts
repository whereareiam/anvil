plugins {
	id("module-api")
	id("module-test-fixtures")
}

description = "Worker runtime kit that protocol libraries embed to run worker extensions for their players"

dependencies {
	api(projects.anvilPlayer.playerApi)
}
