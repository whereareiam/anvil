import java.util.Properties

pluginManagement {
    includeBuild("../../build-logic")

    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://registry.whereareiam.me/maven/packages")
    }
}

rootProject.name = "anvil-test-fixtures"

include("test-extension", "test-process", "test-server-plugin")

val repositoryVersion = providers.fileContents(layout.settingsDirectory.file("../../gradle.properties"))
    .asText.map { text -> Properties().apply { load(text.reader()) }.getProperty("anvilVersion") }
val anvilVersion = providers.gradleProperty("anvilVersion").orElse(repositoryVersion)

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        mavenLocal()
        mavenCentral()
        maven("https://registry.whereareiam.me/maven/packages")
        maven("https://repo.papermc.io/repository/maven-public/")
    }

    versionCatalogs {
        create("libs") {
            from(files("../../gradle/libs.versions.toml"))
        }
        create("anvil") {
            version("anvil", anvilVersion.get())

            // Source composites address Anvil projects by their directory-based names.
            fun module(published: String, source: String = published): String =
                if (gradle.parent == null) published else source

            library("api", "me.whereareiam.anvil", module("api", "anvil-api")).versionRef("anvil")
            library("agent-api", "me.whereareiam.anvil", "agent-api").versionRef("anvil")
            library("agent-server-api", "me.whereareiam.anvil", module("agent-server-api", "server-api")).versionRef("anvil")
            library("capability-agent-api", "me.whereareiam.anvil", "capability-agent-api").versionRef("anvil")
            library("capability-protocol-api", "me.whereareiam.anvil", "capability-protocol-api").versionRef("anvil")
            library("movement-api", "me.whereareiam.anvil", "builtin-movement-api").versionRef("anvil")
            library("protocol-api", "me.whereareiam.anvil", "protocol-api").versionRef("anvil")
            library("session-api", "me.whereareiam.anvil", "builtin-session-api").versionRef("anvil")
        }
    }
}
