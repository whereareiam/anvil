plugins {
    alias(libs.plugins.toolkit.architecture)
    alias(libs.plugins.toolkit.publish.maven)
    id("unit")
}

description = "Paper platform provider for Anvil"

toolkitPublish {
    artifactId.set("platform-paper-provider")
}

dependencies {
    api(projects.anvilPlatform.platformApi)

    implementation(libs.jackson.databind)
    implementation(libs.jackson.yaml)
}
