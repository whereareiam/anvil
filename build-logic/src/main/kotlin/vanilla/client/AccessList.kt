package me.whereareiam.anvil.buildlogic.vanilla.client

import org.gradle.api.GradleException

/**
 * The members of a vanilla client whose access the harness widens, read from the `accesstransformer.cfg` files of a
 * harness family and its release, modelled on NeoForge's access transformers: one class or member per line, its
 * access, the class's binary name and the member with its descriptor.
 *
 * <pre>
 * # A comment, also after an entry.
 * public net.minecraft.client.multiplayer.ClientChunkCache$Storage
 * public net.minecraft.client.multiplayer.ClientChunkCache$Storage getIndex(II)I
 * public-f net.minecraft.client.Minecraft options:Lnet/minecraft/client/Options;
 * public net.minecraft.client.renderer.LevelRenderer <init>(Lnet/minecraft/client/Minecraft;)V
 * </pre>
 *
 * The access is `public` or `protected`; `-f` also removes the member's or class's `final`. A line without a member
 * widens the class itself. A method names its descriptor right after its name, a field after a colon. Widening never
 * narrows: a member already more accessible keeps its access.
 *
 * @property entries the entries in the order the files list them
 */
data class AccessList(val entries: List<Entry>) {
	/**
	 * One widened class or member.
	 *
	 * @property access the access it gets
	 * @property definalize whether it loses `final`
	 * @property owner the class's internal name, such as `net/minecraft/client/Minecraft`
	 * @property member the member, `null` for the class itself
	 */
	data class Entry(val access: Access, val definalize: Boolean, val owner: String, val member: Member?)

	/**
	 * A method or field by its name and descriptor.
	 *
	 * @property name the member's name, `<init>` for a constructor
	 * @property descriptor its JVM descriptor
	 * @property method whether it is a method
	 */
	data class Member(val name: String, val descriptor: String, val method: Boolean) {
		override fun toString(): String = if (method) "$name$descriptor" else "$name:$descriptor"
	}

	/**
	 * The access an entry widens to, with its JVM access flag.
	 */
	enum class Access(val flag: Int) {
		PUBLIC(0x0001),
		PROTECTED(0x0004),
	}

	/**
	 * The entries of one class, by its internal name.
	 */
	fun byOwner(): Map<String, List<Entry>> = entries.groupBy { it.owner }

	/**
	 * This list followed by [other], such as a family's list and its release's.
	 *
	 * @throws GradleException when both name the same class or member
	 */
	operator fun plus(other: AccessList): AccessList = AccessList(entries + other.entries).also { it.requireUnique("the access lists") }

	private fun requireUnique(source: String) {
		entries.groupingBy { it.owner to it.member }.eachCount().filterValues { it > 1 }.keys.forEach { (owner, member) ->
			throw GradleException("$source: ${owner.replace('/', '.')}${member?.let { " $it" } ?: ""} is named more than once")
		}
	}

	companion object {
		/**
		 * The file name of a family's or release's list.
		 */
		const val FILE_NAME = "accesstransformer.cfg"

		private val METHOD = Regex("""([\w$<>]+)(\(.*\).+)""")
		private val FIELD = Regex("""([\w$]+):(.+)""")
		private val CLASS = Regex("""[\w$]+(\.[\w$]+)*""")

		/**
		 * Reads the lines of [text], named [source] in failures.
		 *
		 * @throws GradleException when a line is no entry or the lines name a class or member twice
		 */
		fun parse(text: String, source: String): AccessList {
			val entries = text.lines().mapIndexedNotNull { index, line -> entry(line.substringBefore('#').trim(), "$source:${index + 1}") }
			return AccessList(entries).also { it.requireUnique(source) }
		}

		private fun entry(line: String, position: String): Entry? {
			if (line.isEmpty()) return null
			val parts = line.split(Regex("""\s+"""))
			if (parts.size !in 2..3) throw GradleException("$position: expected '<access> <class> [<member>]', found '$line'")
			val access = when (parts[0].removeSuffix("-f")) {
				"public" -> Access.PUBLIC
				"protected" -> Access.PROTECTED
				else -> throw GradleException("$position: access must be public or protected, with -f to remove final, found '${parts[0]}'")
			}
			if (!CLASS.matches(parts[1])) throw GradleException("$position: '${parts[1]}' is no binary class name")
			return Entry(access, parts[0].endsWith("-f"), parts[1].replace('.', '/'), parts.getOrNull(2)?.let { member(it, position) })
		}

		private fun member(text: String, position: String): Member {
			METHOD.matchEntire(text)?.let { return Member(it.groupValues[1], it.groupValues[2], true) }
			FIELD.matchEntire(text)?.let { return Member(it.groupValues[1], it.groupValues[2], false) }
			throw GradleException("$position: '$text' is neither a method such as getIndex(II)I nor a field such as options:Lnet/minecraft/client/Options;")
		}
	}
}
