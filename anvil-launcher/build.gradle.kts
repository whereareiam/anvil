import java.io.Serializable

class ProtocolAdapterDependencies(
    private val moduleGroup: String,
    private val moduleVersion: String
) : Action<XmlProvider>, Serializable {
    override fun execute(xml: XmlProvider) {
        val dependencies = xml.asNode().appendNode("dependencies")
        listOf("protocol-api", "capability-protocol-api").forEach { artifact ->
            dependencies.appendNode("dependency").apply {
                appendNode("groupId", moduleGroup)
                appendNode("artifactId", artifact)
                appendNode("version", moduleVersion)
            }
        }
    }
}

plugins {
    id("module-assembly")
    id("packaging-shaded-jar")
    id("test-fixtures-consumer")
}

description = "Anvil's executable scenario launcher distribution"

// Consumers also compile against the shaded JAR, which carries the embedded implementations' API types.
// Without the classes variant, projects of this build compile against it too, like consumers of the publication.
configurations.named("apiElements") {
    outgoing.artifacts.clear()
    outgoing.variants.clear()
    outgoing.artifact(tasks.named("shadowJar"))
}

dependencies {
    api(projects.anvilApi)
    api(projects.anvilEnvironment.execution.executionApi)

    implementation(projects.anvilCapability.capabilityApi)
    implementation(projects.anvilCapability.capabilityPlayer.capabilityPlayerApi)
    implementation(projects.anvilCapability.capabilityProcess.capabilityProcessApi)
    implementation(projects.anvilEnvironment.provisioning.provisioningArtifact.artifactApi)
    implementation(projects.anvilEnvironment.provisioning.provisioningJava.javaApi)
    implementation(projects.anvilEnvironment.provisioning.provisioningWorkspace.workspaceApi)
    implementation(projects.anvilPlayer.playerApi)
    implementation(projects.anvilProcess.processApi)
    implementation(projects.anvilProcess.processProtocol.protocolHost.protocolHostApi)
    implementation(projects.anvilWorkbench.workbenchApi)

    compileOnly(projects.anvilCapability.capabilityCommon)
    compileOnly(projects.anvilEngine)
    compileOnly(projects.anvilEnvironment.execution.executionManaged)
    compileOnly(projects.anvilPlayer.playerCommon)
    compileOnly(projects.anvilProcess.processCommon)
    compileOnly(projects.anvilProcess.processProtocol.protocolHost.protocolHostCommon)
    compileOnly(projects.anvilWorkbench)
    compileOnly(projects.anvilWorkbench.workbenchCommon)

    embedded(projects.anvilCapability.capabilityCommon) { isTransitive = false }
    embedded(projects.anvilEngine) { isTransitive = false }
    embedded(projects.anvilEnvironment.cache) { isTransitive = false }
    embedded(projects.anvilEnvironment.execution.executionDocker) { isTransitive = false }
    embedded(projects.anvilEnvironment.execution.executionLocal) { isTransitive = false }
    embedded(projects.anvilEnvironment.execution.executionManaged) { isTransitive = false }
    embedded(projects.anvilEnvironment.provisioning.provisioningArtifact) { isTransitive = false }
    embedded(projects.anvilEnvironment.provisioning.provisioningJava) { isTransitive = false }
    embedded(projects.anvilEnvironment.provisioning.provisioningWorkspace) { isTransitive = false }
    embedded(projects.anvilPlayer.playerCommon) { isTransitive = false }
    embedded(projects.anvilProcess.processCommon) { isTransitive = false }
    embedded(projects.anvilProcess.processProtocol.protocolHost.protocolHostCommon) { isTransitive = false }
    embedded(projects.anvilWorkbench) { isTransitive = false }
    embedded(projects.anvilWorkbench.workbenchCommon) { isTransitive = false }
    embedded(libs.commons.compress)
    embedded(libs.jackson.databind)
    embedded(libs.jackson.parameters)
    embedded(libs.jackson.toml)
    embedded(libs.slf4j.api)
    embedded(libs.slf4j.simple)

    // The embedded local workbench opens its cache through this API; the launcher itself names none of it.
    runtimeOnly(projects.anvilEnvironment.cache.cacheApi)

    testImplementation(projects.anvilCapability.capabilityCommon)
    testImplementation(projects.anvilCapability.capabilityDefault)
    testImplementation(projects.anvilEngine)
    testImplementation(projects.anvilEnvironment.cache)
    testImplementation(projects.anvilEnvironment.execution.executionLocal)
    testImplementation(projects.anvilEnvironment.execution.executionManaged)
    testImplementation(projects.anvilEnvironment.provisioning.provisioningArtifact)
    testImplementation(projects.anvilEnvironment.provisioning.provisioningJava)
    testImplementation(projects.anvilEnvironment.provisioning.provisioningWorkspace)
    testImplementation(projects.anvilPlayer.playerCommon)
    testImplementation(projects.anvilProcess.processCommon)
    testImplementation(projects.anvilProcess.processProtocol.protocolHost.protocolHostCommon)
    testImplementation(projects.anvilWorkbench)
    testImplementation(projects.anvilWorkbench.workbenchCommon)

    testRuntimeOnly(projects.anvilPlayer.playerLibrary.libraryMcprotocol)
    testRuntimeOnly(projects.anvilProcess.processPlatform.platformBukkit.bukkitAgent)
    testRuntimeOnly(projects.anvilProcess.processPlatform.platformBukkit.paperProvider)
    testRuntimeOnly(projects.anvilProcess.processPlatform.platformBukkit.spigotProvider)
    testRuntimeOnly(projects.anvilProcess.processPlatform.platformBungeecord.bungeecordAgent)
    testRuntimeOnly(projects.anvilProcess.processPlatform.platformBungeecord.bungeecordProvider)
    testRuntimeOnly(projects.anvilProcess.processPlatform.platformNeoforge.neoforgeAgent)
    testRuntimeOnly(projects.anvilProcess.processPlatform.platformNeoforge.neoforgeProvider)
    testRuntimeOnly(projects.anvilProcess.processPlatform.platformVelocity.velocityAgent)
    testRuntimeOnly(projects.anvilProcess.processPlatform.platformVelocity.velocityProvider)
}

toolkitPublish {
    artifactId.set("launcher")
    pom {
        name.set("Anvil Launcher")
    }
}

configurations.testRuntimeOnly {
    extendsFrom(configurations["embedded"])
}

val publishedGroup = project.group.toString()
val publishedVersion = project.version.toString()

publishing.publications.create<MavenPublication>("protocolAdapterCompatibility") {
    artifactId = "protocol-adapter-api"
    pom {
        name.set("Anvil protocol adapter compatibility")
        description.set("Compatibility dependencies for the scoped protocol and capability APIs")
        packaging = "pom"
        withXml(ProtocolAdapterDependencies(publishedGroup, publishedVersion))
    }
}

fixtures {
	process()
}
