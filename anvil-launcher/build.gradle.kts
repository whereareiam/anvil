import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
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

// TODO

plugins {
    alias(libs.plugins.toolkit.architecture)
    alias(libs.plugins.toolkit.publish.maven)
    id("unit")
	id("fixtures")
    id("bundle")
}

architecture {
    kind = assembly
}

description = "Anvil's executable scenario launcher distribution"

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set("")
}

tasks.named<Jar>("jar") {
    archiveClassifier.set("plain")
}

listOf("apiElements", "runtimeElements").forEach { name ->
    configurations.named(name) {
        outgoing.artifacts.clear()
        outgoing.artifact(tasks.named("shadowJar"))
    }
}

dependencies {
    api(projects.anvilApi)
    api(projects.anvilEnvironment.execution.executionApi)

    implementation(projects.anvilAgent.agentClient.clientApi)
    implementation(projects.anvilCapability.capabilityAgentApi)
    implementation(projects.anvilCapability.capabilityApi)
    implementation(projects.anvilCapability.capabilityProtocolApi)
    implementation(projects.anvilEnvironment.cache.cacheApi)
    implementation(projects.anvilEnvironment.provisioning.provisioningArtifact.artifactApi)
    implementation(projects.anvilEnvironment.provisioning.provisioningJava.javaApi)
    implementation(projects.anvilEnvironment.provisioning.provisioningWorkspace.workspaceApi)
    implementation(projects.anvilPlatform.platformApi)
    implementation(projects.anvilProtocol.protocolApi)

    compileOnly(projects.anvilAgent.agentClient)
    compileOnly(projects.anvilCapability)
    compileOnly(projects.anvilEngine)
    compileOnly(projects.anvilEnvironment.cache)
    compileOnly(projects.anvilEnvironment.execution.executionManaged)
    compileOnly(projects.anvilPlatform.platformPlanning)
    compileOnly(projects.anvilProtocol)
    compileOnly(projects.anvilEnvironment.provisioning.provisioningWorkspace)
    compileOnly(projects.anvilEnvironment.provisioning.provisioningArtifact)
    compileOnly(projects.anvilEnvironment.provisioning.provisioningJava)

    compileOnly(libs.mcprotocol)

    embedded(projects.anvilAgent.agentClient) { isTransitive = false }
    embedded(projects.anvilCapability) { isTransitive = false }
    embedded(projects.anvilEngine) { isTransitive = false }
    embedded(projects.anvilEnvironment.cache) { isTransitive = false }
    embedded(projects.anvilEnvironment.execution.executionManaged) { isTransitive = false }
    embedded(projects.anvilPlatform.platformPlanning) { isTransitive = false }
    embedded(projects.anvilProtocol) { isTransitive = false }
    embedded(projects.anvilEnvironment.provisioning.provisioningWorkspace) { isTransitive = false }
    embedded(projects.anvilEnvironment.execution.executionLocal) { isTransitive = false }
    embedded(projects.anvilEnvironment.execution.executionDocker) { isTransitive = false }
    embedded(projects.anvilEnvironment.provisioning.provisioningArtifact) { isTransitive = false }
    embedded(projects.anvilEnvironment.provisioning.provisioningJava) { isTransitive = false }
    embedded(libs.commons.compress)
    embedded(libs.jackson.databind)
    embedded(libs.jackson.parameters)
    embedded(libs.slf4j.api)
    embedded(libs.slf4j.simple)

    testImplementation(projects.anvilAgent.agentClient)
    testImplementation(projects.anvilCapability.capabilityBuiltin.default)
    testImplementation(projects.anvilCapability)
    testImplementation(projects.anvilEngine)
    testImplementation(projects.anvilEnvironment.cache)
    testImplementation(projects.anvilEnvironment.execution.executionLocal)
    testImplementation(projects.anvilEnvironment.execution.executionManaged)
    testImplementation(projects.anvilEnvironment.provisioning.provisioningArtifact)
    testImplementation(projects.anvilEnvironment.provisioning.provisioningJava)
    testImplementation(projects.anvilEnvironment.provisioning.provisioningWorkspace)
    testImplementation(projects.anvilPlatform.platformPlanning)
    testImplementation(projects.anvilProtocol)

    testRuntimeOnly(projects.anvilPlatform.platformBukkit.platformBukkitAgent)
    testRuntimeOnly(projects.anvilPlatform.platformBungeecord.platformBungeecordAgent)
    testRuntimeOnly(projects.anvilPlatform.platformBungeecord.platformBungeecordProvider)
    testRuntimeOnly(projects.anvilPlatform.platformPaper.platformPaperProvider)
    testRuntimeOnly(projects.anvilPlatform.platformSpigot.platformSpigotProvider)
    testRuntimeOnly(projects.anvilPlatform.platformVelocity.platformVelocityAgent)
    testRuntimeOnly(projects.anvilPlatform.platformVelocity.platformVelocityProvider)
    testRuntimeOnly(projects.anvilProtocol.protocolMcprotocol)
}

toolkitPublish {
    artifactId.set("launcher")
    pom {
        name.set("Anvil Launcher")
    }
}

tasks.named("build") {
    dependsOn("shadowJar")
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
