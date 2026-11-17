plugins {
	id("module-api")
}

description = "Public API and host-to-worker channel contract of the built-in packets capability"

dependencies {
	api(projects.anvilApi)
	api(projects.anvilPlayer.playerApi)
	api(projects.anvilPlayer.playerProtocol.protocolJava.protocolJavaApi)
}
