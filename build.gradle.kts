plugins {
    alias(libs.plugins.toolkit.architecture)
}

allprojects {
    group = "me.whereareiam.anvil"
    version = providers.gradleProperty("anvilVersion").get()
}
