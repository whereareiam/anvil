plugins {
	alias(libs.plugins.toolkit.architecture)
	alias(libs.plugins.toolkit.publish.maven)
	id("unit")
}

description = "Editor-independent discovery and live session contracts for Anvil"

toolkitPublish {
	artifactId.set("tooling-api")
}

dependencies {
	api(libs.annotations)
	api(libs.jackson.databind)
}
