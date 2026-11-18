plugins {
	id("module-java")
}

description = "Client-side agent connections, sessions, and player observation"

dependencies {
	compileOnly(projects.anvilApi)
	compileOnly(projects.anvilProcess.processAgent.agentClient.clientApi)

	testImplementation(projects.anvilApi)
	testImplementation(projects.anvilProcess.processAgent.agentClient.clientApi)
}
