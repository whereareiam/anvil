plugins {
	alias(libs.plugins.toolkit.architecture)
	alias(libs.plugins.toolkit.publish.maven)
	id("unit")
}

architecture {
	kind = api
}

description = "Java selection, inspection, acquisition, and installation contracts"

toolkitPublish {
	artifactId.set("provisioning-java-api")
}

dependencies {
	api(projects.anvilApi)
	api(libs.annotations)
}
