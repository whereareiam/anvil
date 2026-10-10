plugins {
	id("net.neoforged.moddev")
	id("module-assembly")
	id("packaging-shaded-jar")
	id("packaging-version-stamp")
}

description = "Anvil platform agent for NeoForge"

// The agent compiles against the oldest NeoForge release that Anvil's version data lists and uses only names that
// later releases keep, so one mod JAR loads on every supported release.
neoForge {
	version = libs.versions.neoforge.get()
}

dependencies {
	compileOnly(projects.anvilApi)
	compileOnly(projects.anvilAgent.agentServer.agentServerApi)

	embedded(projects.anvilAgent.agentServer) { isTransitive = false }
}

// NeoForge refuses a mod whose version does not start with a digit, which a branch-qualified build such as
// dev-a123bcd has. The mod version only identifies the agent in NeoForge's mod list, so those builds get a
// numeric prefix; the JAR manifest keeps the exact Anvil version.
val modVersion = provider { project.version.toString().let { if (it.first().isDigit()) it else "0.0.0-$it" } }

tasks.processResources {
	val version = modVersion
	inputs.property("modVersion", version)
	filesMatching("META-INF/neoforge.mods.toml") {
		expand("modVersion" to version.get())
	}
}
