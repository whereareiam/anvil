plugins {
	id("module-api")
	id("module-test-fixtures")
}

description = "Library-neutral simulated-player protocol contracts for Anvil"

dependencies {
	api(projects.anvilApi)
}
