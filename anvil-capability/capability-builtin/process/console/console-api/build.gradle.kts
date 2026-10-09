plugins {
	id("module-api")
}

description = "Public API for the built-in agent console capability"

dependencies {
	api(projects.anvilApi)
}
