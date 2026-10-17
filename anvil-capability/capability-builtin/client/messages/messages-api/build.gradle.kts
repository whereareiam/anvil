plugins {
	id("module-api")
}

description = "Public API and protocol-library port of the built-in messages capability"

dependencies {
	api(projects.anvilApi)
}
