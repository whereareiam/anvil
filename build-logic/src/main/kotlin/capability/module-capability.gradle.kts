import me.whereareiam.anvil.buildlogic.capability.CapabilityFamilySource
import me.whereareiam.toolkit.publish.maven.extension.ToolkitPublishExtension

plugins {
	id("module-assembly")
	id("packaging-shaded-root")
}

// A built-in capability family root only wires its members: it exports the family API, embeds the
// library-neutral common code into its shaded JAR, which consumers also compile against and whose sources and
// Javadoc JARs describe that code, and brings every library side to the runtime, where the worker of each
// library selects one segment. The folder is the only list of members, so a side cannot be left out of the
// runtime, and a child project that is not a member fails here.
val family = providers.of(CapabilityFamilySource::class) {
	parameters {
		rootDirectory.set(isolated.rootProject.projectDirectory)
		familyDirectory.set(layout.projectDirectory)
	}
}.get()

// The folder names are the published artifact IDs: the root publishes builtin-<family> and each published
// member builtin-<member>, such as builtin-movement-api and builtin-movement-mcprotocol. The IDs are final once
// set, so a build file that sets its own fails instead of silently publishing other coordinates. The Gradle
// capability plugins install builtin-<family>. Members are evaluated after the root, which registers this first.
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
}
