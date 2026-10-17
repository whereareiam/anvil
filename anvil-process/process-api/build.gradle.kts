plugins {
	id("module-api")
}

description = "Server and proxy platform provider API for Anvil"

toolkitPublish {
	artifactId.set("platform-api")
}

dependencies {
	api(projects.anvilApi)
}
