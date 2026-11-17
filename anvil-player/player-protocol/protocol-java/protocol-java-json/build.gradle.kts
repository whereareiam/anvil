plugins {
	id("module-api")
}

architecture {
	sharedApis = setOf(projects.anvilPlayer.playerApi.path)
}

description = "The JSON form of the Java Edition protocol's values, shared by the worker channel and recordings"

dependencies {
	api(projects.anvilPlayer.playerProtocol.protocolJava.protocolJavaApi)
	api(libs.jackson.databind)

	implementation(libs.jackson.parameters)
}
