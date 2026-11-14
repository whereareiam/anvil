import me.whereareiam.anvil.buildlogic.capability.CapabilityFamilySource
import me.whereareiam.anvil.buildlogic.capability.ProtocolEditions
import me.whereareiam.toolkit.publish.maven.extension.ToolkitPublishExtension

plugins {
	id("module-assembly")
	id("packaging-shaded-root")
}

// A built-in capability family root only wires its members: it exports the family API, embeds the
// edition-neutral common code into its shaded JAR, which consumers also compile against and whose sources and
// Javadoc JARs describe that code, and brings every edition side to the runtime, where each player's worker
// installs the extensions of the edition it speaks. A process family's agent channel and platform sides go to the
// runtime as well: the host provider reads the channel's payloads, and declares the channel's JAR and each platform
// side's through agentExtensions(), which Anvil finds on the runtime class path and installs into every process of
// their platforms. The folder is the only list of members, so a side cannot be
// left out of the runtime, and a child project that is not a member fails here.
val family = providers.of(CapabilityFamilySource::class) {
	parameters {
		rootDirectory.set(isolated.rootProject.projectDirectory)
		familyDirectory.set(layout.projectDirectory)
	}
}.get()

// The folder names are the published artifact IDs: the root publishes its folder name builtin-<family> and each
// published member builtin-<member>, such as builtin-movement-api and builtin-movement-java. The IDs are
// final once set, so a build file that sets its own fails instead of silently publishing other coordinates, such as
// builtin-worlds-agent and builtin-worlds-bukkit for a process family's agent members. The
// Gradle capability plugins install builtin-<family>. Members are evaluated after the root, which registers this first.
extensions.configure<ToolkitPublishExtension> {
	artifactId.set("builtin-${family.name}")
	artifactId.disallowChanges()
}
family.published.forEach { member ->
	val memberProject = project("${project.path}:$member")
	memberProject.pluginManager.withPlugin("me.whereareiam.toolkit.publish.maven") {
		memberProject.extensions.configure<ToolkitPublishExtension> {
			artifactId.set("builtin-$member")
			artifactId.disallowChanges()
		}
	}
}

dependencies {
	"api"(project("${project.path}:${family.api}"))
	family.common?.let { common ->
		"embedded"(project("${project.path}:$common")) { isTransitive = false }
	}
	family.sides.forEach { side ->
		"runtimeOnly"(project("${project.path}:$side"))
	}
	// The host provider in the common code reads the agent channel's operations and payloads.
	family.agent?.let { agent ->
		"compileOnly"(project("${project.path}:$agent"))
		"runtimeOnly"(project("${project.path}:$agent"))
	}
	family.platformSides.forEach { side ->
		"runtimeOnly"(project("${project.path}:$side"))
	}
	// The host provider in the common code names the protocols its worker extensions speak, and the root reads that
	// code for its sources and Javadoc.
	family.editions.forEach { edition ->
		"compileOnly"(project(ProtocolEditions.apiPath(edition)))
	}
}
