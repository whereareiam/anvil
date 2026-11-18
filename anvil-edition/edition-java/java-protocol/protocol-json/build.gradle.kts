plugins {
	id("module-edition")
}

architecture {
	dependsOnApis = setOf(projects.anvilPlayer.playerApi.path)
}

description = "The JSON form of the Java Edition protocol's values, shared by the worker channel and recordings"

dependencies {
	api(projects.anvilEdition.editionJava.javaProtocol.protocolApi)
	api(libs.jackson.databind)

	implementation(libs.jackson.parameters)
}
