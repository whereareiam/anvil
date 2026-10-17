plugins {
	id("module-java")
}

description = "Host-side agent connections, sessions, and player observation"

dependencies {
	compileOnly(projects.anvilProcess.processProtocol.protocolHost.protocolHostApi)
	compileOnly(projects.anvilApi)

	testImplementation(projects.anvilProcess.processProtocol.protocolHost.protocolHostApi)
	testImplementation(projects.anvilApi)
}
