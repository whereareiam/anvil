plugins {
	id("module-api")
}

description = "Public artifact acquisition contracts for Anvil"

toolkitPublish {
	artifactId.set("provisioning-api")
}

dependencies {
	api(libs.annotations)
}
