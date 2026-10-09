plugins {
	id("api")
	id("in-server")
}

description = "Shared agent operation descriptors, payloads, and process identities"

dependencies {
	api(libs.annotations)
	api(libs.jackson.databind)
}
