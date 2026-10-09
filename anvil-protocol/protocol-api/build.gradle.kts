plugins {
	id("module-api")
}

description = "Library-neutral simulated-player protocol contracts for Anvil"

dependencies {
	api(projects.anvilApi)
}
