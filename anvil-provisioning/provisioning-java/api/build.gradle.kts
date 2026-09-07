plugins {
	alias(libs.plugins.toolkit.architecture)
	alias(libs.plugins.toolkit.publish.maven)
	id("unit")
}

architecture {
	kind = api
}

description = "Public Java provisioning contracts for Anvil"

toolkitPublish {
	artifactId.set("provisioning-java-api")
}

dependencies {
	api(projects.anvilApi)
	api(libs.annotations)
}
