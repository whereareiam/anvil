package me.whereareiam.anvil.buildlogic.vanilla.client

import java.io.Serializable
import java.util.Locale

/**
 * An operating system and architecture a vanilla client's libraries are chosen for, as Mojang's launcher chooses them:
 * by the rules of each library and the natives it ships for the system.
 *
 * @property os the system as Mojang's rules name it: `osx`, `linux` or `windows`
 * @property arch the architecture, `x86_64` or `arm64`
 */
data class ClientPlatform(val os: String, val arch: String) : Serializable {
	/**
	 * The platform's key, such as `linux-x86_64`.
	 */
	val key: String
		get() = "$os-$arch"

	override fun toString(): String = key

	companion object {
		/**
		 * The platform of the containers `generateVanillaData` runs a client in when the host has no natives for it:
		 * every supported release ships natives for it.
		 */
		val CONTAINER = ClientPlatform("linux", "x86_64")

		/**
		 * Returns the platform of this JVM's machine.
		 *
		 * @throws IllegalStateException when Mojang ships clients for neither its system nor its architecture
		 */
		fun host(): ClientPlatform = of(System.getProperty("os.name"), System.getProperty("os.arch"))

		/**
		 * Returns the platform of a JVM reporting [osName] and [osArch].
		 *
		 * @throws IllegalStateException when Mojang ships clients for neither the system nor the architecture
		 */
		fun of(osName: String, osArch: String): ClientPlatform {
			val name = osName.lowercase(Locale.ROOT)
			val os = when {
				name.startsWith("mac") -> "osx"
				name.startsWith("linux") -> "linux"
				name.startsWith("windows") -> "windows"
				else -> throw IllegalStateException("Mojang ships no vanilla client for $osName")
			}
			val arch = when (osArch.lowercase(Locale.ROOT)) {
				"amd64", "x86_64" -> "x86_64"
				"aarch64", "arm64" -> "arm64"
				else -> throw IllegalStateException("Mojang ships no vanilla client for the $osArch architecture")
			}
			return ClientPlatform(os, arch)
		}
	}
}
