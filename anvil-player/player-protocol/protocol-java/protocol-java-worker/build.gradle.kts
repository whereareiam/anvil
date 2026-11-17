plugins {
	id("module-api")
	id("module-test-fixtures")
}

architecture {
	sharedApis = setOf(projects.anvilPlayer.playerApi.path)
}

description = "Java Edition worker kit that protocol libraries embed: the player's protocol over their connection"

dependencies {
	api(projects.anvilPlayer.playerApi)
	api(projects.anvilPlayer.playerProtocol.protocolJava.protocolJavaApi)

	implementation(projects.anvilPlayer.playerProtocol.protocolJava.protocolJavaPacket)

	testFixturesApi(testFixtures(projects.anvilPlayer.playerWorker))
}
