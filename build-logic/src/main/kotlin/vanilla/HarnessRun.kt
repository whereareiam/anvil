package me.whereareiam.anvil.buildlogic.vanilla

import java.io.File
import java.io.Serializable

/**
 * One release `generateVanillaData` runs: the family harness compiled against the release's client, and the client
 * `prepareVanillaClient-<minecraft>` prepared.
 *
 * @property minecraft the Minecraft version
 * @property java the Java feature version the live matrix runs the release's server on, which the client runs on too
 * @property mainClass the family harness's main class
 * @property harness the harness's class path without the client: the family's classes compiled against this
 * release, the shared classes and their libraries
 * @property prepared the directory of the prepared client
 */
data class HarnessRun(
	val minecraft: String,
	val java: Int,
	val mainClass: String,
	val harness: List<File>,
	val prepared: File,
) : Serializable
