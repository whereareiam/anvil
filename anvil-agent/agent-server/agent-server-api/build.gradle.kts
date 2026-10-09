plugins {
	id("api")
	id("in-server")
}

description = "Embedded agent endpoints, native platform access, and operation provider contracts"

dependencies {
	api(projects.anvilAgent.agentApi)
}
