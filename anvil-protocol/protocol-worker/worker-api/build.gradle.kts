plugins {
	id("module-api")
}

description = "Contracts a protocol library implements to run its players in Anvil's native worker"

toolkitPublish {
	artifactId.set("protocol-worker-api")
}

dependencies {
	api(projects.anvilApi)
	api(projects.anvilProtocol.protocolApi)

	// Lombok's Jackson builders of the worker messages only annotate them; the worker reads them with its own Jackson.
	compileOnly(libs.jackson.databind)
}
