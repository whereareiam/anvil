plugins {
	id("module-java")
	id("module-java-legacy")
	id("packaging-version-stamp")
}

description = "Server plugin and native agent operations used by live Anvil tests"

// Server-release modules the plugin JAR carries; the plugin loads each only on the servers it compiles for.
val releases = configurations.dependencyScope("releases")
val releaseClasspath = configurations.resolvable("releaseClasspath") {
	extendsFrom(releases.get())
	isTransitive = false
	attributes {
		attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
		attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
	}
}

dependencies {
	compileOnly(anvil.agent.server.api)
	compileOnly(libs.bukkit.paper)

	"releases"(project(":test-server-plugin:V1_19_4"))
	"releases"(project(":test-server-plugin:V1_21_1"))
}

interface ArchiveAccess {
	@get:Inject
	val archives: ArchiveOperations
}

tasks.named<Jar>("jar") {
	val archives = objects.newInstance<ArchiveAccess>().archives
	val jars = releaseClasspath.flatMap { it.elements }
	from(jars.map { files -> files.map { file -> archives.zipTree(file.asFile) } })
	duplicatesStrategy = DuplicatesStrategy.INCLUDE
}
