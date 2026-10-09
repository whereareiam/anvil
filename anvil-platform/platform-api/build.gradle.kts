plugins {
	id("module-api")
}

description = "Server and proxy platform provider API for Anvil"

dependencies {
	api(projects.anvilApi)
}
