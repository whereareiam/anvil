plugins {
	id("module-api")
}

description = "Public API for the built-in session capability"

dependencies {
	api(projects.anvilApi)
}
