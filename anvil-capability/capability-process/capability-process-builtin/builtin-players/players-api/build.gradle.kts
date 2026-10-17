plugins {
	id("module-api")
}

description = "Public API for the built-in players capability"

dependencies {
	api(projects.anvilApi)
}
