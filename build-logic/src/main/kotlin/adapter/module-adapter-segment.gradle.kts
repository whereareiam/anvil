import me.whereareiam.anvil.buildlogic.library.LibraryRepositories
import me.whereareiam.anvil.buildlogic.library.releaseClosureClasspath
import me.whereareiam.anvil.buildlogic.library.releaseModule
import me.whereareiam.anvil.buildlogic.adapter.SegmentExtension
import me.whereareiam.anvil.buildlogic.adapter.SegmentPackageRule
import me.whereareiam.anvil.buildlogic.adapter.SegmentPlanSource
import me.whereareiam.anvil.buildlogic.adapter.SyncSegmentBase
import me.whereareiam.anvil.buildlogic.adapter.WriteSegmentManifest
import me.whereareiam.anvil.buildlogic.adapter.linkage.CheckSegmentLinkage

plugins {
	java
	id("module-java")
	id("packaging-publication")
}

// A segment adapts one feature to one library release: its folder V<major>_<minor>[_<patch>] names the release
// key it starts at, and its parent folder names the library.
val plan = providers.of(SegmentPlanSource::class) {
	parameters {
		rootDirectory.set(isolated.rootProject.projectDirectory)
		segmentDirectory.set(layout.projectDirectory)
	}
}.get()

// Only the side folder puts its segments on the runtime classpath. The build-libraries settings plugin fails a
// side folder that does not apply `module-adapter`, so a segment requires that plugin.
if (gradle.extensions.findByType<LibraryRepositories>() == null)
	throw GradleException("Segment $path needs the library layout checks: add id(\"build-libraries\") to the settings plugins")

// Segments of different side folders share project names, and Gradle conflates projects with equal
// group:name, so the side folder is part of the group and of the JAR name.
group = "me.whereareiam.anvil.${plan.owner}"
description = "${plan.owner} adapter for ${plan.library} releases from Minecraft ${plan.segment.since}"
base {
	archivesName.set("${plan.owner}-${project.name}")
}

// A segment may extend an earlier segment of its side and take its classes except its adapters, which serve only
// the base's release, and the classes it replaces with its own. A segment offers its own classes and resources, with
// the classes it takes from its base, through a variant of its own, so the chain resolves transitively and a class
// that one segment replaced never returns to the segments extending it.
val segmentProjectPath = path
val segment = extensions.create("segment", SegmentExtension::class, dependencies, segmentProjectPath, plan.segment)
val segmentBase = configurations.dependencyScope(SegmentExtension.BASE_CONFIGURATION)
val segmentClassesUsage = objects.named<Usage>(SegmentExtension.CLASSES_USAGE)
val baseClasses = configurations.resolvable("segmentBaseClasses") {
	extendsFrom(segmentBase.get())
	attributes { attribute(Usage.USAGE_ATTRIBUTE, segmentClassesUsage) }
}
val sharedBase = tasks.register<SyncSegmentBase>("syncSegmentBase") {
	description = "Collects the classes the segment takes from its base chain, without the bases' adapters."
	base.from(baseClasses)
	segmentPath.set(segmentProjectPath)
	replaced.set(segment.replaced)
	destination.set(layout.buildDirectory.dir("segment/base"))
}
configurations.consumable("segmentClassesElements") {
	attributes { attribute(Usage.USAGE_ATTRIBUTE, segmentClassesUsage) }
	outgoing.artifact(tasks.named<JavaCompile>("compileJava").flatMap { it.destinationDirectory })
	outgoing.artifact(tasks.named<ProcessResources>("processResources").map { it.destinationDir }) {
		builtBy(tasks.named("processResources"))
	}
	outgoing.artifact(sharedBase.flatMap { it.destination })
}
val sharedBaseClasses = files(sharedBase.flatMap { it.destination })

// The release the segment starts at is its compile baseline; the worker supplies the library at runtime.
dependencies {
	compileOnly(plan.release.module)
	compileOnly(sharedBaseClasses)
	testImplementation(plan.release.module)
	testImplementation(sharedBaseClasses)
}

// The worker loads one segment per side, so a segment carries the classes of its base chain.
tasks.named<Jar>("jar") {
	from(sharedBaseClasses)
}

// Classes outside the versioned package fail the compilation itself.
tasks.named<JavaCompile>("compileJava") {
	inputs.property("segmentPackage", plan.segment.packageName)
	doLast(SegmentPackageRule(plan.segment.packageName))
}

val toolchainJdk = javaToolchains.launcherFor(java.toolchain)
val projectClasses = configurations.named("compileClasspath").map { classpath ->
	classpath.incoming.artifactView { componentFilter { it is ProjectComponentIdentifier } }.files
}

val manifest = tasks.register<WriteSegmentManifest>("writeSegmentManifest") {
	description = "Writes the segment's selection properties and linkage manifest."
	library.set(plan.library)
	owner.set(plan.owner)
	since.set(plan.segment.since.toString())
	classes.from(sourceSets.main.map { it.output.classesDirs })
	classes.from(sharedBaseClasses)
	classpath.from(configurations.named("compileClasspath"))
	projectDependencies.from(projectClasses)
	jdk.set(toolchainJdk)
	destination.set(layout.buildDirectory.dir("generated/segment"))
}

tasks.named<ProcessResources>("processResources") {
	from(manifest)
}

// The release module's dependency graph tells which references belong to the library; the locked closures of
// release data are what workers load, so the references must link on the compiled release's closure and on the
// closure of every later release the segment serves.
val libraryModule = releaseModule(plan.release)
val compiledRelease = releaseClosureClasspath(plan.release)
val laterReleaseClasspaths = plan.laterReleases.map { releaseClosureClasspath(it) }

// Classes taken from a base chain were compiled against the release of an earlier segment, so the releases of the
// earlier segments also tell which of their references belong to the library.
val baseLibraryModules = plan.earlierReleases.map { releaseModule(it) }

val linkageCheck = tasks.register<CheckSegmentLinkage>("checkSegmentLinkage") {
	group = LifecycleBasePlugin.VERIFICATION_GROUP
	description = "Verifies that the segment links against every library release it serves, as workers load them."
	linkage.set(manifest.flatMap { it.destination.file("META-INF/anvil/segment/linkage.txt") })
	library.from(libraryModule)
	library.from(segment.base.map { baseLibraryModules }.orElse(emptyList()))
	release.set(compiledRelease)
	laterReleases.set(laterReleaseClasspaths)
	jdk.set(toolchainJdk)
	report.set(layout.buildDirectory.file("reports/segment/linkage.txt"))
}

tasks.named("check") {
	dependsOn(linkageCheck)
}
