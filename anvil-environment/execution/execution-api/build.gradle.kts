plugins {
	id("module-api")
}

description = "Execution plans, providers, endpoints, and process lifetime contracts for Anvil environments"

dependencies {
	api(projects.anvilApi)
}
