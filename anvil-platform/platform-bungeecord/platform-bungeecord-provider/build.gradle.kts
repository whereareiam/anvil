plugins {
    alias(libs.plugins.toolkit.architecture)
    alias(libs.plugins.toolkit.publish.maven)
    id("unit")
}

description = "BungeeCord platform provider for Anvil"

toolkitPublish {
    artifactId.set("platform-bungeecord-provider")
}

dependencies {
    api(projects.anvilPlatform.platformApi)

    implementation(libs.jackson.yaml)
}
