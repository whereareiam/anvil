plugins {
	id("module-api")
}

description = "Public API for the built-in server capability"

dependencies {
	api(projects.anvilApi)
}
