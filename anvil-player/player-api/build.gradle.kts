plugins {
	id("module-api")
}

description = "Library-neutral simulated-player protocol contracts for Anvil"

toolkitPublish {
	artifactId.set("protocol-api")
}

dependencies {
	api(projects.anvilApi)
}
