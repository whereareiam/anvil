plugins {
	id("module-adapter-segment")
}

dependencies {
	implementation(projects.anvilProtocol.protocolMcprotocol.mcprotocolApi)
}

// Packetlib 2.1 opens one io_uring ring per event-loop thread, and a ring for every core can exceed the locked
// memory a machine allows; tests that connect use as few threads as workers do.
tasks.test {
	systemProperty("io.netty.eventLoopThreads", 4)
}
