package me.whereareiam.anvil.gradle.task

import me.whereareiam.anvil.api.player.PlayerCapability
import me.whereareiam.anvil.protocol.api.provider.ProtocolProvider
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class AuthenticationTaskTest {
    @TempDir
    lateinit var projectDirectory: Path

    @Test
    fun `uses the sole provider for login and logout and reuses configuration cache`() {
        writeProject(authenticated = true)
        val first = runner("anvilLogin", "--auth-profile=demo", "--configuration-cache").build()
        assertContains(first.output, "fixture-login:demo")
        val second = runner("anvilLogin", "--auth-profile=demo", "--configuration-cache").build()
        assertContains(second.output, "Reusing configuration cache")
        assertContains(second.output, "fixture-login:demo")
        assertContains(runner("anvilLogout", "--auth-profile=demo").build().output, "fixture-logout:demo")
    }

    @Test
    fun `selects the configured provider when multiple providers are installed`() {
        writeProject(authenticated = true, additionalProvider = true)
        val failure = runner("anvilLogin", "--auth-profile=demo").buildAndFail()
        assertContains(failure.output, "Multiple protocol providers are installed")
        projectDirectory.resolve("build.gradle.kts").toFile().appendText("\nanvil { protocol(\"fixture\") }\n")
        assertContains(runner("anvilLogin", "--auth-profile=demo").build().output, "fixture-login:demo")
    }

    @Test
    fun `offline provider reports unsupported authentication without creating a backend`() {
        writeProject(authenticated = false)
        val result = runner("anvilLogin", "--auth-profile=demo").buildAndFail()
        assertContains(result.output, "Protocol provider 'fixture' does not support interactive authentication")
    }

    private fun writeProject(authenticated: Boolean, additionalProvider: Boolean = false) {
        write("settings.gradle.kts", "rootProject.name = \"authentication-fixture\"")
        val apiFiles = listOf(ProtocolProvider::class.java, PlayerCapability::class.java)
            .map { Path.of(it.protectionDomain.codeSource.location.toURI()).toString() }
            .joinToString(", ") { "\"${it.replace("\\", "\\\\").replace("\"", "\\\"")}\"" }
        write("build.gradle.kts", """
            plugins { id("me.whereareiam.anvil.scenarios") }
            dependencies {
                compileOnly(files($apiFiles))
                add("anvilProtocols", files(tasks.named("jar")))
            }
            anvil { cacheDirectory.set(layout.buildDirectory.dir("auth")) }
        """.trimIndent())
        write("src/main/java/example/FixtureProvider.java", """
            package example;
            import java.nio.file.Path;
            import java.util.Optional;
            import java.util.function.Consumer;
            import me.whereareiam.anvil.protocol.api.provider.ProtocolProvider;
            import me.whereareiam.anvil.protocol.api.provider.ProtocolBackend;
            import me.whereareiam.anvil.protocol.api.provider.ProtocolAuthentication;
            public class FixtureProvider implements ProtocolProvider {
                public String id() { return "fixture"; }
                public ProtocolBackend create(Path cache) {
                    throw new AssertionError("Authentication must not create a backend");
                }
                public Optional<ProtocolAuthentication> authentication(Path cache) {
                    ${if (authenticated) """
                    return Optional.of(new ProtocolAuthentication() {
                        public void login(String profile, Consumer<String> output) {
                            output.accept("fixture-login:" + profile);
                        }
                        public void logout(String profile, Consumer<String> output) {
                            output.accept("fixture-logout:" + profile);
                        }
                    });
                    """.trimIndent() else "return Optional.empty();"}
                }
            }
        """.trimIndent())
        if (additionalProvider)
            write("src/main/java/example/OtherProvider.java", """
                package example;
                public final class OtherProvider extends FixtureProvider {
                    public String id() { return "other"; }
                }
            """.trimIndent())
        write("src/main/resources/META-INF/services/me.whereareiam.anvil.protocol.api.provider.ProtocolProvider",
            "example.FixtureProvider\n" + if (additionalProvider) "example.OtherProvider\n" else "")
    }

    private fun write(path: String, content: String) {
        projectDirectory.resolve(path).also { it.parent.createDirectories() }.writeText(content)
    }

    private fun runner(vararg arguments: String): GradleRunner = GradleRunner.create()
        .withProjectDir(projectDirectory.toFile())
        .withPluginClasspath()
        .withArguments(*arguments, "--stacktrace")

    private fun assertContains(output: String, expected: String) {
        assertTrue(output.contains(expected), "Expected '$expected' in:\n$output")
    }
}
