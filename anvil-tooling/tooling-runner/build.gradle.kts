plugins {
	id("jvm")
	id("publication")
}

description = "Reusable foreground scenario runner for Anvil tooling"

dependencies {
	api(projects.anvilApi)
	api(projects.anvilTooling.toolingApi)
	api(projects.anvilTooling.toolingExtensionApi)

	implementation(libs.jackson.databind)
}
