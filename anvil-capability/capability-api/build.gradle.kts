plugins {
    alias(libs.plugins.toolkit.architecture)
    alias(libs.plugins.toolkit.publish.maven)
    id("unit")
}

description = "Public contracts for dependency-discovered Anvil capabilities"

dependencies {
    api(projects.anvilApi)
}
