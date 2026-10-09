import me.whereareiam.anvil.buildlogic.adapter.CheckAdapterLookup
import me.whereareiam.anvil.buildlogic.adapter.LibrarySideSource

plugins {
	java
	id("module-java")
}

// A library side folder exports all of its segments at runtime; the worker selects one per release. Its only
// child projects are segments, and a misnamed child fails here instead of silently leaving the runtime.
val side = providers.of(LibrarySideSource::class) {
	parameters {
		rootDirectory.set(isolated.rootProject.projectDirectory)
		sideDirectory.set(layout.projectDirectory)
	}
}.get()

dependencies {
	side.segments.forEach { segment ->
		runtimeOnly(project("${project.path}:${segment.projectName}"))
	}
}

// A side with sources wires its capability into the worker and must take adapters from the worker's lookup.
val adapterLookup = tasks.register<CheckAdapterLookup>("checkAdapterLookup") {
	group = LifecycleBasePlugin.VERIFICATION_GROUP
	description = "Verifies that the side's classes obtain adapters through the worker instead of a service lookup."
	classes.from(sourceSets.main.map { it.output.classesDirs })
	report.set(layout.buildDirectory.file("reports/segment/adapter-lookup.txt"))
}

tasks.named("check") {
	dependsOn(adapterLookup)
}
