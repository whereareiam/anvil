plugins {
	id("module-api")
	id("module-test-fixtures")
}

architecture {
	sharedApis = setOf(projects.anvilPlayer.playerApi.path)
}

description = "Physics of Minecraft: Java Edition entities as the vanilla client computes them, over the world a player's client knows"

dependencies {
	api(projects.anvilPlayer.playerProtocol.protocolJava.protocolJavaApi)
	api(projects.anvilPlayer.playerProtocol.protocolJava.protocolJavaBlock)
	api(projects.anvilPlayer.playerProtocol.protocolJava.protocolJavaGeometry)

	testFixturesApi(projects.anvilPlayer.playerProtocol.protocolJava.protocolJavaBlock)
	testFixturesApi(testFixtures(projects.anvilPlayer.playerProtocol.protocolJava.protocolJavaWorker))
}
