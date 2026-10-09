plugins {
	id("api")
}

description = "Public, platform-independent API for Anvil"

toolkitPublish {
	artifactId.set("api")
}

dependencies {
	api(libs.annotations)
}
