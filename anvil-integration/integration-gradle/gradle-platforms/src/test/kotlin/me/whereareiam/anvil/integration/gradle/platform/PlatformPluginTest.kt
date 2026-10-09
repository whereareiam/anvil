package me.whereareiam.anvil.integration.gradle.platform

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

class PlatformPluginTest {
    @TempDir
    lateinit var projectDirectory: Path

    @Test
    fun `paper plugin adds platform dependencies to the anvil source set`() {
        projectDirectory.resolve("settings.gradle.kts").writeText("rootProject.name = \"platform-fixture\"")
        projectDirectory.resolve("build.gradle.kts").writeText("""
            plugins {
                id("me.whereareiam.anvil.platform.paper")
            }
            check(configurations.names.none { it in setOf("anvilPlatforms", "anvilUnits") })
            tasks.register("verify") {
                doLast {
                    val dependencies = configurations.getByName("anvilImplementation").allDependencies
                        .map { it.name }
                    check("platform-paper-provider" in dependencies)
                    check("platform-bukkit-agent" in dependencies)
                }
            }
        """.trimIndent())

        val result = GradleRunner.create()
            .withProjectDir(projectDirectory.toFile())
            .withPluginClasspath()
            .withArguments("verify")
            .build()
        assertTrue(result.output.contains("BUILD SUCCESSFUL"), result.output)
    }
}
