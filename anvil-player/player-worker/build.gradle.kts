plugins {
	id("module-api")
}

description = "Worker runtime kit that protocol libraries embed to run worker extensions for their players"

dependencies {
	api(projects.anvilPlayer.playerApi)
}
