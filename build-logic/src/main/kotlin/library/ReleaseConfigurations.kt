package me.whereareiam.anvil.buildlogic.library

import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ResolvableConfiguration
import org.gradle.api.attributes.AttributeContainer
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.newInstance

/**
 * Creates the configuration that resolves the locked worker runtime closure of [release]: exactly the
 * `[[release.artifact]]` modules of release data, classifiers included, without their dependencies and in their
 * listed order. This is what a worker downloads, so segment linkage checks and worker tests resolve it rather than
 * the release module's dependency graph.
 */
fun Project.releaseClosure(release: LibraryRelease): NamedDomainObjectProvider<ResolvableConfiguration> {
	val name = "libraryRelease" + release.key.format("_") + "Closure"
	val modules = release.artifacts.map { artifact ->
		(dependencies.create(artifact.module) as ExternalModuleDependency).apply { isTransitive = false }
	}
	val scope = configurations.dependencyScope(name) {
		dependencies.addAll(modules)
	}
	return configurations.resolvable("${name}Classpath") {
		extendsFrom(scope.get())
		isTransitive = false
		attributes { runtimeJars(this@releaseClosure) }
	}
}

/**
 * Describes the locked worker runtime closure of [release] as a task input; see [releaseClosure].
 */
fun Project.releaseClosureClasspath(release: LibraryRelease): ReleaseClasspath = objects.newInstance<ReleaseClasspath>().apply {
	version.set(release.version)
	minecraft.set(release.key.toString())
	files.from(releaseClosure(release))
}

/**
 * Creates the configuration that resolves [release]'s `module` with its runtime dependencies, as Gradle's own
 * dependency resolution sees it. Segments compile against this module, and `pinLibraryReleases` regenerates the
 * locked closure from it.
 */
internal fun Project.releaseModule(release: LibraryRelease): NamedDomainObjectProvider<ResolvableConfiguration> {
	val name = "libraryRelease" + release.key.format("_") + "Module"
	val module = dependencies.create(release.module)
	val scope = configurations.dependencyScope(name) {
		dependencies.add(module)
	}
	return configurations.resolvable("${name}Classpath") {
		extendsFrom(scope.get())
		attributes { runtimeJars(this@releaseModule) }
	}
}

private fun AttributeContainer.runtimeJars(project: Project) {
	attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage.JAVA_RUNTIME))
	attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category.LIBRARY))
	attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(LibraryElements.JAR))
	attribute(Bundling.BUNDLING_ATTRIBUTE, project.objects.named(Bundling.EXTERNAL))
}
