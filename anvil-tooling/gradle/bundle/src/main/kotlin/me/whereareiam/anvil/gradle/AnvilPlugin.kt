package me.whereareiam.anvil.gradle

import me.whereareiam.anvil.junit.gradle.AnvilJunitPlugin
import me.whereareiam.anvil.gradle.unit.DefaultPlugin
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Combined entry point for automated JUnit tests and foreground scenarios.
 */
class AnvilPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply(AnvilJunitPlugin::class.java)
        project.pluginManager.apply(AnvilScenariosPlugin::class.java)
        project.pluginManager.apply(DefaultPlugin::class.java)
    }
}
