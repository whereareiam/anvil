plugins {
    alias(libs.plugins.toolkit.architecture)
    alias(libs.plugins.toolkit.publish.maven)
    id("unit")
}

description = "Public, platform-independent API for Anvil"

toolkitPublish {
    artifactId.set("api")
}

dependencies {
    api(libs.annotations)
}
