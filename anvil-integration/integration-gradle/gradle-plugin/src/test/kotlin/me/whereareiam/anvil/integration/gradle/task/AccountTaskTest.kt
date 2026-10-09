package me.whereareiam.anvil.integration.gradle.task

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class AccountTaskTest {
    @TempDir
    lateinit var directory: Path

    private val testedVersion = requireNotNull(System.getProperty("anvil.test.version"))
    private val repository = Path.of(requireNotNull(System.getProperty("anvil.test.repository"))).toUri()

    @Test
    fun `requires exactly one account operation`() {
        project()

        val result = runner("anvilAccount").buildAndFail()
        assertTrue(result.output.contains("Select exactly one of --login=<account> or --logout=<account>"), result.output)
    }

    @Test
    fun `signs out through the sole library in the configured account directory`() {
        project()

        val result = runner("anvilAccount", "--logout=ci-player").build()
        assertTrue(result.output.contains("Authentication account 'ci-player' did not exist."), result.output)
    }

    private fun project() {
        write("settings.gradle.kts", "rootProject.name = \"account-fixture\"")
        write("build.gradle.kts", """
            plugins { id("me.whereareiam.anvil") }
            repositories {
                maven { url = uri("$repository") }
                mavenCentral()
                maven("https://repo.opencollab.dev/main/")
                maven("https://repo.opencollab.dev/maven-snapshots/")
            }
            dependencies { add("anvilRuntimeOnly", "me.whereareiam.anvil:protocol-mcprotocol:$testedVersion") }
            anvil { engine { accountsDirectory.set(layout.projectDirectory.dir("accounts")) } }
        """.trimIndent())
    }

    private fun runner(vararg arguments: String): GradleRunner = GradleRunner.create()
        .withProjectDir(directory.toFile())
        .withPluginClasspath()
        .withArguments(*arguments, "-PanvilVersion=$testedVersion", "--configuration-cache", "--stacktrace")

    private fun write(file: String, content: String) {
        directory.resolve(file).also { it.parent.createDirectories() }.writeText(content)
    }
}
