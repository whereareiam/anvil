plugins {
	id("jvm")
	id("in-server")
	id("descriptor-version")
}

description = "Server plugin and native agent operations used by live Anvil tests"

dependencies {
	compileOnly(anvil.agent.server.api)
	compileOnly(libs.bukkit.paper)
}
