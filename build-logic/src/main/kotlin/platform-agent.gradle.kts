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
