plugins {
	id("module-api")
	id("module-java-legacy")
}

description = "Embedded agent endpoints, native platform access, and operation provider contracts"

dependencies {
	api(projects.anvilAgent.agentApi)
}
