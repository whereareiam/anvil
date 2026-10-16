import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
	id("module-assembly")
	id("packaging-shaded-jar")
	id("packaging-version-stamp")
}

description = "Anvil platform agent for Lumi"

// Lumi ships its own, older Jackson, and its plugin class loader asks the server's class loader first, so the
// Jackson the agent embeds would never load. The plugin is therefore only a bootstrap: the agent itself, from the
// agent source set, and the agent server it embeds form a nested JAR that the bootstrap loads child first.
val agent = sourceSets.create("agent")

dependencies {
	compileOnly(libs.lumi)

	"agentCompileOnly"(projects.anvilAgent.agentServer.agentServerApi)
	"agentCompileOnly"(libs.lumi)

	embedded(projects.anvilAgent.agentServer) { isTransitive = false }
}

val agentJar = tasks.register<ShadowJar>("agentJar") {
	description = "Assembles the agent and the agent server it embeds into the JAR the Lumi plugin loads."
	archiveClassifier.set("agent")
	from(agent.output)
	configurations.set(project.configurations.named("embeddedClasspath").map { setOf<Configuration>(it) })
	filesMatching("META-INF/services/**") {
		duplicatesStrategy = DuplicatesStrategy.INCLUDE
	}
	mergeServiceFiles()
}

tasks.named<ShadowJar>("shadowJar") {
	configurations.set(emptySet())
	from(agentJar) {
		into("META-INF/anvil")
		rename { "agent.jar" }
	}
}
