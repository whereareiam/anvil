plugins {
	id("module-api")
	id("module-java-legacy")
}

description = "Embedded agent endpoints, native platform access, and operation provider contracts"

toolkitPublish {
	artifactId.set("agent-server-api")
}

dependencies {
	api(projects.anvilProcess.processProtocol.processProtocolApi)
}
