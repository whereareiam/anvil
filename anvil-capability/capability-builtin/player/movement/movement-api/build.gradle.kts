plugins {
	id("api")
}

description = "Public API and protocol-library port of the built-in movement capability"

dependencies {
	api(projects.anvilApi)
}
