plugins {
	alias(libs.plugins.toolkit.architecture)
	alias(libs.plugins.toolkit.publish.maven)
	id("unit")
}

description = "Public artifact acquisition contracts for Anvil"

toolkitPublish {
	artifactId.set("provisioning-api")
}

dependencies {
	api(libs.annotations)
}
