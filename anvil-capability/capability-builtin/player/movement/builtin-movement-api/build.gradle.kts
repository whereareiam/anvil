plugins {
    alias(libs.plugins.toolkit.architecture)
    alias(libs.plugins.toolkit.publish.maven)
    id("unit")
}

description = "Public API for the built-in movement capability"

dependencies {
    api(projects.anvilApi)
}
