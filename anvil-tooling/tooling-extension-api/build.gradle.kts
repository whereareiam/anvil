plugins {
	id("module-api")
}

description = "Runtime registration contracts for portable Anvil tooling extensions"

dependencies {
	api(projects.anvilApi)
	api(projects.anvilTooling.toolingApi)
}
