plugins {
	id("module-adapter")
	id("module-assembly")
}

description = "MCProtocolLib client segments; the worker loads the one selected for its release"

toolkitPublish {
	artifactId.set("mcprotocol-client")
}
