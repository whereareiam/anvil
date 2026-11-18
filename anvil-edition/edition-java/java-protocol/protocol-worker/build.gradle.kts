plugins {
	id("module-edition")
	id("module-test-fixtures")
}

architecture {
	dependsOnApis = setOf(projects.anvilPlayer.playerApi.path)
}

description = "Java Edition worker kit that protocol libraries embed: the player's protocol over their connection"

dependencies {
	api(projects.anvilEdition.editionJava.javaData.dataPacket)
	api(projects.anvilEdition.editionJava.javaProtocol.protocolApi)
	api(projects.anvilPlayer.playerApi)

	testFixturesApi(testFixtures(projects.anvilPlayer.playerWorker))
}
