plugins {
	alias(libs.plugins.toolkit.architecture)
	alias(libs.plugins.toolkit.publish.maven)
	id("unit")
}

description = "Reusable foreground scenario runner for Anvil tooling"

toolkitPublish {
	artifactId.set("tooling-runner")
}

dependencies {
	api(projects.anvilApi)
	api(projects.anvilTooling.toolingApi)
	api(projects.anvilTooling.toolingExtensionApi)

	implementation(libs.jackson.databind)
}
