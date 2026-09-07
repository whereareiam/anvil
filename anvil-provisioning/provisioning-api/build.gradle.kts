plugins {
	alias(libs.plugins.toolkit.architecture)
	alias(libs.plugins.toolkit.publish.maven)
	id("unit")
}

description = "Public provisioning contracts for Anvil"

toolkitPublish {
	artifactId.set("provisioning-api")
}

dependencies {
	api(libs.annotations)
}
