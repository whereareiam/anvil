plugins {
	id("api")
}

description = "Workspace preparation, snapshot publication, and lifetime contracts"

toolkitPublish {
	artifactId.set("provisioning-workspace-api")
}

dependencies {
	api(projects.anvilApi)
	api(libs.annotations)
}
