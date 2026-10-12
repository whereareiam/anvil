package me.whereareiam.anvil.integration.gradle.task

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText

class AccountTaskTest {
    @TempDir
    lateinit var directory: Path

    private val testedVersion = requireNotNull(System.getProperty("anvil.test.version"))
    private val repository = Path.of(requireNotNull(System.getProperty("anvil.test.repository"))).toUri()

    @Test
    fun `requires an account`() {
        project()

        for (task in listOf("anvilLogin", "anvilLogout")) {
            val result = runner(task).buildAndFail()
            assertTrue(result.output.contains("Select an account with --account=<id>"), result.output)
        }
    }

    @Test
    fun `signs out in the configured account directory without choosing a library`() {
        project("protocol-mcprotocol", "protocol-cloudburst")
        write("accounts/stored.json", "{\"schemaVersion\":2,\"accountId\":\"stored\",\"microsoft\":{},\"libraries\":{}}")

        val missing = runner("anvilLogout", "--account=ci-player").build()
        val removed = runner("anvilLogout", "--account=stored").build()

        assertTrue(missing.output.contains("Account 'ci-player' did not exist."), missing.output)
        assertTrue(removed.output.contains("Removed account 'stored'."), removed.output)
        assertFalse(directory.resolve("accounts/stored.json").exists())
    }

    private fun project(vararg libraries: String = arrayOf("protocol-mcprotocol")) {
        val dependencies = libraries.joinToString("; ") { "add(\"anvilRuntimeOnly\", \"me.whereareiam.anvil:$it:$testedVersion\")" }
        write("settings.gradle.kts", "rootProject.name = \"account-fixture\"")
        write("build.gradle.kts", """
            plugins { id("me.whereareiam.anvil") }
            repositories {
                maven { url = uri("$repository") }
                mavenCentral()
                maven("https://repo.opencollab.dev/main/")
                maven("https://repo.opencollab.dev/maven-snapshots/")
            }
            dependencies { $dependencies }
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
