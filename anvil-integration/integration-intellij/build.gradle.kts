plugins {
	base
	alias(libs.plugins.toolkit.architecture)
}

architecture {
	// IntelliJ is a frontend for tooling, with IDE-specific contracts in intellij-api.
	family = projects.anvilTooling.path
}

tasks.named("build") {
	dependsOn(subprojects.map { "${it.path}:build" })
}

tasks.register("test") {
	group = "verification"
	description = "Runs the engine (including API contracts), UI, Gradle-adapter, and plugin-assembly tests."
	dependsOn(subprojects.map { "${it.path}:test" })
}
