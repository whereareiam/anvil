import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
	id("module-assembly")
	id("packaging-shaded-jar")
	id("packaging-version-stamp")
}

description = "Anvil platform agent for Lumi"

dependencies {
	compileOnly(projects.anvilAgent.agentServer.agentServerApi)
	compileOnly(libs.lumi)

	embedded(projects.anvilAgent.agentServer) { isTransitive = false }
}

// Lumi ships its own, older Jackson, and its plugin class loader asks the server's class loader first, so the
// Jackson the agent embeds would never load. The agent carries its Jackson under its own package instead.
tasks.named<ShadowJar>("shadowJar") {
	relocate("com.fasterxml.jackson", "me.whereareiam.anvil.agent.lumi.jackson")
}
