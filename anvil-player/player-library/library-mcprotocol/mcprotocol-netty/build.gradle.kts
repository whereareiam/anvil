plugins {
	id("module-api")
}

description = "Netty handlers that hand over each packet an MCProtocolLib client segment's session receives"

dependencies {
	api(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolApi)

	compileOnly(libs.netty.all)

	testImplementation(testFixtures(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolApi))
	testImplementation(libs.netty.all)
}
