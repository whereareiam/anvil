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
