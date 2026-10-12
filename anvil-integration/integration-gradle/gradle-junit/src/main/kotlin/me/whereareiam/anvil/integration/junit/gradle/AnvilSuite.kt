package me.whereareiam.anvil.integration.junit.gradle

import org.gradle.api.Named
import org.gradle.api.provider.SetProperty
import javax.inject.Inject

/**
 * Named group of live tests that runs in a task of its own instead of `anvilTest`.
 *
 * A suite selects its tests by JUnit tag. Its task is `anvil<Name>Test`, for example `anvilUpgradeTest`
 * for the suite `upgrade`, and `anvilTest` leaves out every tag a suite includes:
 *
 * ```
 * anvil {
 *     suites {
 *         register("upgrade") { includeTags("upgrade") }
 *     }
 * }
 * ```
 */
abstract class AnvilSuite @Inject constructor(private val name: String) : Named {
    /** JUnit tags of the tests this suite runs; `anvilTest` leaves them out. */
    abstract val includedTags: SetProperty<String>

    init {
        require(NAME.matches(name)) { "Anvil suite name '$name' must start with a letter and contain only letters and digits" }
    }

    /** Returns the suite's name, which also names its task. */
    override fun getName(): String = name

    /** Adds JUnit tags whose tests this suite runs. */
    fun includeTags(vararg tags: String) {
        includedTags.addAll(*tags)
    }

    /** Name of the task that runs this suite. */
    val taskName: String
        get() = "anvil" + name.replaceFirstChar(Char::uppercaseChar) + "Test"

    private companion object {
        val NAME = Regex("[A-Za-z][A-Za-z0-9]*")
    }
}
