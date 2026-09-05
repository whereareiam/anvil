package me.whereareiam.anvil.gradle.task

import me.whereareiam.anvil.gradle.internal.AnvilPluginNames
import me.whereareiam.anvil.protocol.api.provider.ProtocolProviderRegistry
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Optional
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.work.DisableCachingByDefault
import java.net.URLClassLoader

/** Performs an explicit local login or logout without exposing credentials to Gradle inputs. */
@DisableCachingByDefault(because = "Authentication changes owner-local state outside the project")
abstract class AuthenticationTask : DefaultTask() {
    /** Runtime artifacts supplying protocol providers and their dependencies. */
    @get:Classpath
    abstract val runtimeClasspath: ConfigurableFileCollection

    /** Explicit provider selection; omission selects the sole installed provider. */
    @get:Input
    @get:Optional
    abstract val protocolId: Property<String>

    /** Anvil cache containing the owner-only authentication directory. */
    @get:Internal
    abstract val cacheDirectory: DirectoryProperty

    /** Login or logout operation configured by the plugin. */
    @get:Input
    abstract val operation: Property<String>

    /** Named profile selected with --auth-profile=name. */
    @get:Input
    abstract val profile: Property<String>

    /** Selects the owner-local profile name with --auth-profile=name. */
    @Option(option = AnvilPluginNames.PROFILE_OPTION, description = "Authentication profile name")
    fun selectProfile(value: String) {
        profile.set(value)
    }

    /** Executes authentication in the Gradle process; tokens are never task arguments or inputs. */
    @TaskAction
    fun authenticate() {
        val selected = profile.orNull ?: error("--auth-profile=<name> is required")
        val cache = cacheDirectory.get().asFile.toPath()
        val thread = Thread.currentThread()
        val previousLoader = thread.contextClassLoader
        URLClassLoader(runtimeClasspath.files.map { it.toURI().toURL() }.toTypedArray(), javaClass.classLoader).use { loader ->
            try {
                thread.contextClassLoader = loader
                val provider = ProtocolProviderRegistry.discover().select(protocolId.orNull)
                val authentication = provider.authentication(cache).orElseThrow {
                    IllegalStateException("Protocol provider '${provider.id()}' does not support interactive authentication")
                }
                when (operation.get()) {
                    AnvilPluginNames.LOGIN_OPERATION -> authentication.login(selected) { logger.lifecycle(it) }
                    AnvilPluginNames.LOGOUT_OPERATION -> authentication.logout(selected) { logger.lifecycle(it) }
                    else -> error("Unknown authentication operation: ${operation.get()}")
                }
            } finally {
                thread.contextClassLoader = previousLoader
            }
        }
    }
}
