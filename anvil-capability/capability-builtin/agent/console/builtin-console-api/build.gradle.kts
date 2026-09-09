plugins {
	alias(libs.plugins.toolkit.architecture)
	alias(libs.plugins.toolkit.publish.maven)
	id("unit")
}

description = "Public API for the built-in agent console capability"

dependencies {
	api(projects.anvilApi)
}

toolkitPublish {
	artifactId.set("builtin-console-api")
}
