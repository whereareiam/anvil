import me.whereareiam.anvil.buildlogic.module.TestDirectoryArgument

plugins {
	id("module-java")
	id("packaging-publication")
}

description = "Java Edition side of the built-in movement capability: its worker extension, which moves the client's own player of the Java worker kit as the vanilla client does, on the physics kit"

dependencies {
	implementation(projects.anvilCapability.capabilityPlayer.capabilityPlayerBuiltin.builtinMovement.movementApi)
	implementation(projects.anvilEdition.editionJava.javaData.dataBlock)
	implementation(projects.anvilEdition.editionJava.javaPhysics.physicsGeometry)
	implementation(projects.anvilEdition.editionJava.javaPhysics.physicsEntity)
	implementation(projects.anvilEdition.editionJava.javaPhysics.physicsGeometry)
	implementation(projects.anvilEdition.editionJava.javaProtocol.protocolApi)
	implementation(projects.anvilPlayer.playerApi)

	testImplementation(testFixtures(projects.anvilEdition.editionJava.javaPhysics.physicsEntity))
	testImplementation(testFixtures(projects.anvilEdition.editionJava.javaProtocol.protocolWorker))
	testImplementation(libs.jackson.toml)
}

// The physics courses and the vanilla client's reference traces of them, which ReferenceTraceTest compares with.
tasks.named<Test>("test") {
	jvmArgumentProviders.add(objects.newInstance<TestDirectoryArgument>().apply {
		property.set("anvil.physics.courses")
		directory.set(layout.settingsDirectory.dir("anvil-testkit/physics/courses"))
	})
	jvmArgumentProviders.add(objects.newInstance<TestDirectoryArgument>().apply {
		property.set("anvil.physics.traces")
		directory.set(layout.settingsDirectory.dir("anvil-testkit/physics/traces"))
	})
}
