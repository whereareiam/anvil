plugins {
    alias(libs.plugins.toolkit.architecture)
    alias(libs.plugins.toolkit.publish.maven)
    id("unit")
}

description = "Velocity platform provider for Anvil"

toolkitPublish {
    artifactId.set("platform-velocity-provider")
}

dependencies {
    api(projects.anvilPlatform.platformApi)

    implementation(libs.jackson.databind)
    implementation(libs.jackson.toml)
}
