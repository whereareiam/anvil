plugins {
	alias(libs.plugins.toolkit.architecture)
	alias(libs.plugins.toolkit.publish.maven)
	id("unit")
}

architecture {
	kind = api
}

description = "Runtime registration contracts for portable Anvil tooling extensions"

toolkitPublish {
	artifactId.set("tooling-extension-api")
}

dependencies {
	api(projects.anvilApi)
	api(projects.anvilTooling.toolingApi)
}
