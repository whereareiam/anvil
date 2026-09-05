package me.whereareiam.anvil.gradle

import me.whereareiam.anvil.gradle.internal.AnvilCoordinates

/**
 * Bundled protocol-provider coordinates aligned to the applied plugin version.
 */
class ProtocolDependencies internal constructor(version: String) {
    val mcprotocol: String = AnvilCoordinates.module("protocol-mcprotocol", version)
}
