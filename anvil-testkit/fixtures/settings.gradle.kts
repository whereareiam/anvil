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

include("test-extension", "test-process", "test-server-plugin", "test-tooling-extension")

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
            library("tooling-extension-api", "me.whereareiam.anvil", "tooling-extension-api").versionRef("anvil")
            library("capability-api", "me.whereareiam.anvil", "capability-api").versionRef("anvil")
            library("platform-api", "me.whereareiam.anvil", "platform-api").versionRef("anvil")
            library("agent-api", "me.whereareiam.anvil", "agent-api").versionRef("anvil")
            library("agent-server-api", "me.whereareiam.anvil", "agent-server-api").versionRef("anvil")
            library("capability-agent-api", "me.whereareiam.anvil", "capability-agent-api").versionRef("anvil")
            library("capability-protocol-api", "me.whereareiam.anvil", "capability-protocol-api").versionRef("anvil")
            library("movement-api", "me.whereareiam.anvil", module("builtin-movement-api", "movement-api")).versionRef("anvil")
            library("protocol-api", "me.whereareiam.anvil", "protocol-api").versionRef("anvil")
            library("session-api", "me.whereareiam.anvil", module("builtin-session-api", "session-api")).versionRef("anvil")
        }
    }
}
