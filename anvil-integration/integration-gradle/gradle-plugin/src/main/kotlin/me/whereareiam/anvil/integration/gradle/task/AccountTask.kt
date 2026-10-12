package me.whereareiam.anvil.integration.gradle.task

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.UntrackedTask
import org.gradle.api.tasks.options.Option
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.process.ExecOperations
import javax.inject.Inject

/**
 * Changes one stored account, selected with `--account=<id>`, through the project's protocol library, or
 * through the library selected with `--library=<id>` when several installed libraries offer accounts.
 *
 * Runs the same authentication entry point as the IntelliJ account manager in a separate JVM, so
 * prompts reach the console and credentials stay in the library's account store. [AccountLoginTask]
 * signs the account in and [AccountLogoutTask] removes it.
 */
@UntrackedTask(because = "Interactive sign-in changes the local account store outside the build")
abstract class AccountTask : DefaultTask() {
    /**
     * Tooling runtime containing the authentication entry point and the protocol libraries.
     */
    @get:Classpath
    abstract val runtimeClasspath: ConfigurableFileCollection

    /**
     * Engine properties that select the account directory and protocol library.
     */
    @get:Input
    abstract val engineProperties: MapProperty<String, String>

    /**
     * Project Java toolchain used by the authentication JVM.
     */
    @get:Nested
    abstract val javaLauncher: Property<JavaLauncher>

    /**
     * Account to change.
     */
    @get:Input
    @get:Optional
    abstract val account: Property<String>

    /**
     * Protocol library whose account workflow changes the account, or none for the engine's library.
     */
    @get:Input
    @get:Optional
    abstract val library: Property<String>

    @get:Inject
    protected abstract val processes: ExecOperations

    /**
     * Selects the account with --account=id.
     */
    @Option(option = "account", description = "Account ID")
    fun selectAccount(value: String) {
        account.set(value)
    }

    /**
     * Selects the protocol library with --library=id.
     */
    @Option(option = "library", description = "Protocol library ID; defaults to the engine's library")
    fun selectLibrary(value: String) {
        library.set(value)
    }

    /**
     * Runs the authentication entry point for the selected account with the given further arguments.
     */
    protected fun authenticate(arguments: List<String>) {
        if (!account.isPresent) error("Select an account with --account=<id>")

        processes.javaexec {
            executable = javaLauncher.get().executablePath.asFile.absolutePath
            classpath(runtimeClasspath)
            mainClass.set("me.whereareiam.anvil.tooling.launcher.AnvilAuthentication")
            args(listOf("--account-id", account.get()) + library.map { listOf("--library", it) }.getOrElse(listOf()) + arguments)
            systemProperties(engineProperties.get())
            standardInput = System.`in`
            standardOutput = System.out
            errorOutput = System.err
        }.assertNormalExitValue()
    }
}
