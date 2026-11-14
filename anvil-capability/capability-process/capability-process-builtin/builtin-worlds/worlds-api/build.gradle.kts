plugins {
	id("module-api")
}

description = "Public API for the built-in worlds capability"

dependencies {
	api(projects.anvilApi)
}
