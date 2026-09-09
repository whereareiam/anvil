import me.whereareiam.toolkit.versioning.extension.ToolkitVersioningExtension

plugins {
    alias(libs.plugins.toolkit.architecture)
    alias(libs.plugins.toolkit.versioning)
    alias(libs.plugins.toolkit.publish.maven) apply false
}

val projectVersion = extensions.getByType<ToolkitVersioningExtension>().apply {
    defaultVersion.set(providers.gradleProperty("anvilVersion"))
}.resolvedVersion().get()

allprojects {
    group = "me.whereareiam.anvil"
    version = projectVersion
}
