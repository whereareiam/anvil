plugins {
	id("module-edition")
	id("module-test-fixtures")
}

architecture {
	dependsOnApis = setOf(projects.anvilPlayer.playerApi.path)
}

description = "Physics of Minecraft: Java Edition entities as the vanilla client computes them, over the world a player's client knows"

dependencies {
	api(projects.anvilEdition.editionJava.javaData.dataBlock)
	api(projects.anvilEdition.editionJava.javaPhysics.physicsGeometry)
	api(projects.anvilEdition.editionJava.javaProtocol.protocolApi)

	testFixturesApi(projects.anvilEdition.editionJava.javaData.dataBlock)
	testFixturesApi(testFixtures(projects.anvilEdition.editionJava.javaProtocol.protocolWorker))
}
