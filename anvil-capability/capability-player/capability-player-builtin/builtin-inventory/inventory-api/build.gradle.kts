plugins {
	id("module-api")
}

description = "Public API and protocol-library port of the built-in inventory capability"

dependencies {
	api(projects.anvilApi)
}
