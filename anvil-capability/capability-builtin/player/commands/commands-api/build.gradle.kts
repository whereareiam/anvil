plugins {
	id("module-api")
}

description = "Public API and protocol-library port of the built-in commands capability"

dependencies {
	api(projects.anvilApi)
}
