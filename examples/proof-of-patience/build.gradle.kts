plugins {
    java
    id("me.whereareiam.anvil")
    id("me.whereareiam.anvil.platform.paper")
}

dependencies {
    compileOnly(libs.paper)

    testImplementation(libs.junit.jupiter)

    testRuntimeOnly(libs.junit.platform)

    add("anvilProtocols", anvil.protocols.mcprotocol)
}

tasks.test {
    useJUnitPlatform()
}

anvil {
    acceptEula()
    protocol("mcprotocol")
    artifact("plugin-under-test", tasks.named("jar"))
}
