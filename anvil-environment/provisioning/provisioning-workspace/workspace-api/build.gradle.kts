plugins {
	alias(libs.plugins.toolkit.architecture)
	alias(libs.plugins.toolkit.publish.maven)
	id("unit")
}

architecture {
	kind = api
}

description = "Workspace preparation, snapshot publication, and lifetime contracts"

toolkitPublish {
	artifactId.set("provisioning-workspace-api")
}

dependencies {
	api(projects.anvilApi)
	api(libs.annotations)
}
