plugins {
	id("module-api")
}

description = "Public API and host-to-worker channel contract of the built-in packets capability"

dependencies {
	api(projects.anvilApi)
	api(projects.anvilEdition.editionJava.javaProtocol.protocolApi)
	api(projects.anvilPlayer.playerApi)
}
