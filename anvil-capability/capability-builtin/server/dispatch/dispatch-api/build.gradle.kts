plugins {
	id("module-api")
}

description = "Public API for the built-in dispatch capability"

dependencies {
	api(projects.anvilApi)
}
