plugins {
	id("api")
}

description = "Java selection, inspection, acquisition, and installation contracts"

toolkitPublish {
	artifactId.set("provisioning-java-api")
}

dependencies {
	api(projects.anvilApi)
	api(libs.annotations)
}
