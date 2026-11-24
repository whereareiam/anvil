plugins {
	id("module-api")
}

description = "Public API for the built-in network capability"

dependencies {
	api(projects.anvilApi)
}
