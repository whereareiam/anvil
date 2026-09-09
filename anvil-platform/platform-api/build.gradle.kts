plugins {
    alias(libs.plugins.toolkit.architecture)
    alias(libs.plugins.toolkit.publish.maven)
    id("unit")
}

description = "Server and proxy platform provider API for Anvil"

dependencies {
    api(projects.anvilApi)
}
