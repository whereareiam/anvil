import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("unit")
    id("bundle")
    id("me.whereareiam.toolkit.publish.maven")
}

tasks.named<ShadowJar>("shadowJar") {
    archiveClassifier.set("")
    mergeServiceFiles()
}

val agentVersion = project.version.toString()

tasks.withType<ProcessResources>().configureEach {
    val descriptorVersion = agentVersion
    inputs.property("agentVersion", agentVersion)
    filesMatching(listOf("plugin.yml", "bungee.yml")) {
        expand("version" to descriptorVersion)
    }
}

tasks.withType<Jar>().configureEach {
    val descriptorVersion = agentVersion
    inputs.property("agentVersion", agentVersion)
    manifest.attributes["Implementation-Version"] = agentVersion
    filesMatching("velocity-plugin.json") {
        expand("version" to descriptorVersion)
    }
}

tasks.named<Jar>("jar") {
    archiveClassifier.set("plain")
}

configurations.named("runtimeElements") {
    outgoing.artifacts.clear()
    outgoing.artifact(tasks.named("shadowJar"))
}

tasks.named("build") {
	dependsOn("shadowJar")
}
