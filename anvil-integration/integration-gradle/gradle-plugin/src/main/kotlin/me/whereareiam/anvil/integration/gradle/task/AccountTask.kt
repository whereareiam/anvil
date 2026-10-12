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
 * Changes one stored Microsoft account, selected with `--account=<id>`. The account is not tied to a protocol
 * library: every installed library that plays online derives its own session from it.
 *
 * Runs the same authentication entry point as the IntelliJ account manager in a separate JVM, so
 * prompts reach the console and credentials stay in the account file. [AccountLoginTask]
 * signs the account in and prints, for each Minecraft edition the installed libraries speak, whether the edition's
 * players can use it, and [AccountLogoutTask] removes it.
 */
@UntrackedTask(because = "Interactive sign-in changes the local account store outside the build")
abstract class AccountTask : DefaultTask() {
    /**
     * Tooling runtime containing the authentication entry point and the protocol libraries.
     */
    @get:Classpath
    abstract val runtimeClasspath: ConfigurableFileCollection

    /**
     * Engine properties that select the account directory.
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
     * Runs the authentication entry point for the selected account with the given further arguments.
     */
    protected fun authenticate(arguments: List<String>) {
        if (!account.isPresent) error("Select an account with --account=<id>")

        processes.javaexec {
            executable = javaLauncher.get().executablePath.asFile.absolutePath
            classpath(runtimeClasspath)
            mainClass.set("me.whereareiam.anvil.tooling.launcher.AnvilAuthentication")
            args(listOf("--account-id", account.get()) + arguments)
            systemProperties(engineProperties.get())
            standardInput = System.`in`
            standardOutput = System.out
            errorOutput = System.err
        }.assertNormalExitValue()
    }
}
