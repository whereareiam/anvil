plugins {
	id("module-api")
}

description = "Public API for the built-in ticks capability"

dependencies {
	api(projects.anvilApi)
}
