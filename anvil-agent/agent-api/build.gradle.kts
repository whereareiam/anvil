plugins {
    alias(libs.plugins.toolkit.architecture)
    alias(libs.plugins.toolkit.publish.maven)
    id("agent-java")
}

description = "Public agent contracts used by Anvil platform integrations"

dependencies {
    api(libs.jackson.databind)
}
