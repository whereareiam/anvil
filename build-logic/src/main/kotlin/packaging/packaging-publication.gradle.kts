plugins {
	id("me.whereareiam.toolkit.publish.maven")
}

// The POM, the BOM and the documentation describe each published module with its description. Build files
// assign it after their plugins are applied, so it is checked once the project is evaluated.
afterEvaluate {
	if (description.isNullOrBlank())
		throw GradleException("$path is published and must declare a description")
}
