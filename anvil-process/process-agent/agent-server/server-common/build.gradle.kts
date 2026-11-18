plugins {
	id("module-java")
	id("module-java-legacy")
}

description = "Authenticated agent endpoint and operation handlers embedded in Minecraft processes"

dependencies {
	compileOnly(projects.anvilProcess.processAgent.agentServer.serverApi)
	compileOnly(libs.jackson.databind)

	testImplementation(projects.anvilProcess.processAgent.agentServer.serverApi)
	testImplementation(projects.anvilProcess.processAgent.agentClient.clientApi)
	testImplementation(projects.anvilProcess.processAgent.agentClient.clientCommon)
	testImplementation(libs.jackson.databind)
}
