plugins {
	id("module-api")
}

description = "Public API and protocol-library port of the built-in interaction capability"

dependencies {
	api(projects.anvilApi)
}
