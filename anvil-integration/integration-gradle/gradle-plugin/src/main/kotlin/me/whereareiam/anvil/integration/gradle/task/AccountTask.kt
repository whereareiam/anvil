package me.whereareiam.anvil.integration.gradle.task

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.api.tasks.options.Option
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.process.ExecOperations
import javax.inject.Inject

/**
 * Signs a stored account in or out through the project's protocol library.
 *
 * Runs the same authentication entry point as the IntelliJ account manager in a separate JVM, so
 * prompts reach the console and credentials stay in the library's account store. Select exactly one
 * of `--login=<id>` or `--logout=<id>`.
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
     * Account to sign in.
     */
    @get:Input
    @get:Optional
    abstract val login: Property<String>

    /**
     * Account to remove.
     */
    @get:Input
    @get:Optional
    abstract val logout: Property<String>

    @get:Inject
    protected abstract val processes: ExecOperations

    /**
     * Selects the account to sign in with --login=id.
     */
    @Option(option = "login", description = "Account ID to sign in and store")
    fun selectLogin(value: String) {
        login.set(value)
    }

    /**
     * Selects the account to remove with --logout=id.
     */
    @Option(option = "logout", description = "Account ID to remove from the account store")
    fun selectLogout(value: String) {
        logout.set(value)
    }

    /**
     * Runs one sign-in or sign-out in the tooling runtime.
     */
    @TaskAction
    fun authenticate() {
        if (login.isPresent == logout.isPresent) error("Select exactly one of --login=<account> or --logout=<account>")

        val arguments = mutableListOf("--account-id", login.orNull ?: logout.get())
        if (logout.isPresent) arguments += "--logout"

        processes.javaexec {
            executable = javaLauncher.get().executablePath.asFile.absolutePath
            classpath(runtimeClasspath)
            mainClass.set("me.whereareiam.anvil.tooling.launcher.AnvilAuthentication")
            args(arguments)
            systemProperties(engineProperties.get())
            standardInput = System.`in`
            standardOutput = System.out
            errorOutput = System.err
        }.assertNormalExitValue()
    }
}
