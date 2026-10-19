plugins {
	id("module-api")
}

description = "Providers that run the service containers of Anvil scenarios"

dependencies {
	api(projects.anvilApi)
}
