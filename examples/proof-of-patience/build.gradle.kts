plugins {
    java
    id("me.whereareiam.anvil")
    id("me.whereareiam.anvil.junit")
    id("me.whereareiam.anvil.capability.default")
    id("me.whereareiam.anvil.platform.paper")
}

dependencies {
    compileOnly(libs.paper)

    testImplementation(libs.junit.jupiter)

    testRuntimeOnly(libs.junit.platform)

    add("anvilRuntimeOnly", anvilModules.protocol.mcprotocol)
}

version = "1.0.0"

// Paper 1.21.11 runs on Java 21, so the plugin must load there whichever JDK builds it.
tasks.withType<JavaCompile>().configureEach {
    options.release = 21
}

tasks.processResources {
    val version = project.version.toString()
    inputs.property("version", version)
    filesMatching("plugin.yml") {
        expand("version" to version)
    }
}

tasks.test {
    useJUnitPlatform()
}

anvil {
    acceptEula()
    engine {
        protocolLibrary("mcprotocol")
    }
    artifact("plugin-under-test", tasks.named("jar"))
}
