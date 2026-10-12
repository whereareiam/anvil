// An implementation that the sibling families of its parent embed, such as the library-neutral native worker of
// anvil-protocol. Implementations below that parent compile against it through their sharedImplementation
// configuration, and their assemblies embed it; verifySharedImplementations, which verifyArchitecture runs, confines
// that use. Applying this convention is the declaration it checks.
plugins {
	id("module-java")
}
