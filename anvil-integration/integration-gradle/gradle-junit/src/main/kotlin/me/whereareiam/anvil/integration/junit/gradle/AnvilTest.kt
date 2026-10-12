package me.whereareiam.anvil.integration.junit.gradle

import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.testing.Test

/**
 * Gradle `Test` task that runs JUnit tests from the `anvil` source set.
 *
 * The JUnit plugin wires every task of this type the same way: the `anvil` classes and runtime classpath,
 * the engine settings and registered artifacts, and JUnit Platform. `anvilTest` and the task of every
 * suite are of this type, so settings shared by all of them belong in
 * `tasks.withType<AnvilTest>().configureEach { ... }`. Like any Gradle `Test` task, it is cacheable.
 */
@CacheableTask
abstract class AnvilTest : Test()
