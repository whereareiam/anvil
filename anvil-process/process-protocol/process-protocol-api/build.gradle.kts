plugins {
	id("module-api")
	id("module-java-legacy")
}

description = "Shared agent operation descriptors, payloads, and process identities"

toolkitPublish {
	artifactId.set("agent-api")
}

dependencies {
	api(libs.annotations)
	api(libs.jackson.databind)
}
