package me.whereareiam.anvil.buildlogic.library

import java.io.Serializable

/**
 * A Minecraft: Java Edition release version, compared component by component like the runtime model in
 * `anvil-api`: `1.16.5 < 1.21.11 < 26.1 < 26.1.2`, and `1.20` equals `1.20.0`.
 */
class MinecraftVersion private constructor(private val components: List<Int>) : Comparable<MinecraftVersion>, Serializable {
	/**
	 * Canonical components joined with [separator], always with at least major and minor.
	 */
	fun format(separator: String): String = (0 until maxOf(2, components.size)).joinToString(separator) { component(it).toString() }

	override fun compareTo(other: MinecraftVersion): Int {
		for (index in 0 until maxOf(components.size, other.components.size)) {
			val difference = component(index).compareTo(other.component(index))
			if (difference != 0) return difference
		}
		return 0
	}

	override fun equals(other: Any?): Boolean = other is MinecraftVersion && components == other.components

	override fun hashCode(): Int = components.hashCode()

	override fun toString(): String = format(".")

	private fun component(index: Int): Int = components.getOrElse(index) { 0 }

	companion object {
		private const val serialVersionUID = 1L
		private val release = Regex("""\d+(\.\d+){1,2}""")

		/**
		 * Parses a dotted release version such as `1.16.5`, `1.20` or `26.1.2`.
		 *
		 * @throws IllegalArgumentException when the text is not a release version
		 */
		fun parse(text: String): MinecraftVersion {
			require(release.matches(text)) { "'$text' is not a Minecraft release version" }
			val components = text.split('.').map(String::toInt).toMutableList()
			while (components.size > 1 && components.last() == 0)
				components.removeAt(components.lastIndex)
			return MinecraftVersion(components.toList())
		}

		/**
		 * Selects the value whose start version is the greatest one not newer than [target]. Version-specific
		 * code and data apply from their start until the next value starts.
		 */
		fun <T> floor(values: Collection<T>, start: (T) -> MinecraftVersion, target: MinecraftVersion): T? =
			values.filter { start(it) <= target }.maxByOrNull(start)
	}
}
