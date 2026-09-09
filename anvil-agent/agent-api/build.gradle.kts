plugins {
    alias(libs.plugins.toolkit.architecture)
    alias(libs.plugins.toolkit.publish.maven)
    id("unit")
}

description = "Shared agent operation descriptors, payloads, and process identities"

dependencies {
    api(libs.annotations)
    api(libs.jackson.databind)
}
