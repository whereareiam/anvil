plugins {
	`java-gradle-plugin`
	id("sources")
	id("testing")
	id("toolchain")
	id("me.whereareiam.toolkit.publish.maven")
}

toolkitPublish {
	name.set("pluginMaven")
	includeComponent.set(false)
}

java {
	withJavadocJar()
}

tasks.jar {
	manifest.attributes["Implementation-Version"] = project.version
}
