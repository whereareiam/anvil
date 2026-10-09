import me.whereareiam.anvil.buildlogic.platform.AgentJavaCheck

plugins {
	id("module-java")
}

// The agent this provider installs into its platform's processes. Only its runtime JAR is checked; the provider
// neither compiles against it nor packages it.
val platformAgent = configurations.dependencyScope("platformAgent")
val platformAgentClasspath = configurations.resolvable("platformAgentClasspath") {
	extendsFrom(platformAgent.get())
	isTransitive = false
	attributes {
		attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
		attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
		attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
		attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
	}
}

val agentJavaCheck = tasks.register<AgentJavaCheck>("checkAgentJava") {
	group = LifecycleBasePlugin.VERIFICATION_GROUP
	description = "Verifies that the platform agent runs on the oldest Java the provider's version data declares."
	versionData.from(sourceSets.main.map { it.resources.matching { include("**/*-versions.toml") } })
	agents.from(platformAgentClasspath)
	agentReleases.set(platformAgentClasspath.flatMap { it.incoming.artifacts.resolvedArtifacts }.map { artifacts ->
		artifacts.mapNotNull { it.variant.attributes.getAttribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE) }
	})
	report.set(layout.buildDirectory.file("reports/platform/agent-java.txt"))
}

tasks.named("check") {
	dependsOn(agentJavaCheck)
}
