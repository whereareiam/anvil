plugins {
    alias(libs.plugins.toolkit.architecture)
    alias(libs.plugins.toolkit.publish.maven)
    id("unit")
}

description = "JUnit extension integration for Anvil scenarios"

toolkitPublish {
    artifactId.set("junit")
}

architecture {
    kind = assembly
}

dependencies {
    api(projects.anvilApi)
    api(libs.junit.jupiter)

    implementation(projects.anvilLauncher)

    compileOnly(projects.anvilEngine)

    runtimeOnly(libs.junit.platform)
}
