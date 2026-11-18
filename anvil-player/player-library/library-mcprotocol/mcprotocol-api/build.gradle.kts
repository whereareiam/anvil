plugins {
	id("module-api")
	id("module-test-fixtures")
}

description = "Release-neutral client port that MCProtocolLib client segments implement for the Anvil worker"

dependencies {
	api(projects.anvilApi)
	api(projects.anvilEdition.editionJava.javaProtocol.protocolApi)
}
