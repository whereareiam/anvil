package me.whereareiam.anvil.buildlogic.adapter.linkage

/**
 * A class, field or method that compiled code links against, with what the JVM requires of it when linking.
 *
 * [owner] is a JVM internal class name (`a/b/C$D`); [member] is `name:descriptor` for a field and
 * `name(parameters)return` for a method. A segment's `META-INF/anvil/segment/linkage.txt` holds one reference per
 * line: the kind, the owner as a binary class name that `Class.forName` accepts, the member, then the
 * [requirements] as keywords in a fixed order:
 *
 * ```
 * class a.b.C public class
 * class a.b.Listener public interface
 * class a.b.Packet public
 * field a.b.C NAME:Ljava/lang/String; public static
 * method a.b.C send(La/b/Packet;)V public instance class
 * method a.b.Listener onEvent()V public instance interface
 * method a.b.Base helper()V protected instance
 * ```
 */
data class LinkageReference(
	val kind: Kind,
	val owner: String,
	val member: String = "",
	val requirements: Set<Requirement> = emptySet(),
) : Comparable<LinkageReference> {
	/**
	 * What the reference names.
	 */
	enum class Kind(val keyword: String) {
		CLASS("class"),
		FIELD("field"),
		METHOD("method"),
	}

	/**
	 * What a release must provide for the reference to link. Each failure is an `IncompatibleClassChangeError`
	 * or an `IllegalAccessError` at runtime, not a missing member.
	 */
	enum class Requirement(val keyword: String) {
		/**
		 * The class or member is public, as it was on the release the code compiled against.
		 */
		PUBLIC("public"),

		/**
		 * The member is at least protected, as it was on the release the code compiled against.
		 */
		PROTECTED("protected"),

		/**
		 * The member is static: `getstatic`, `putstatic` and `invokestatic` link it.
		 */
		STATIC("static"),

		/**
		 * The member is not static: field access and invocations through an instance link it.
		 */
		INSTANCE("instance"),

		/**
		 * The owner is a class: a superclass, an instantiated class, or the owner of a class method reference.
		 */
		CLASS("class"),

		/**
		 * The owner is an interface: an implemented interface or the owner of an interface method reference.
		 */
		INTERFACE("interface"),
	}

	/**
	 * The manifest line of this reference.
	 */
	val line: String
		get() = (listOf(kind.keyword, owner.replace('/', '.'), member) + requirements.sorted().map(Requirement::keyword))
			.filter(String::isNotEmpty)
			.joinToString(" ")

	override fun compareTo(other: LinkageReference): Int =
		compareValuesBy(this, other, { it.owner }, { it.kind }, { it.member }, { it.line })

	override fun toString(): String = line

	companion object {
		/**
		 * Reads one manifest line.
		 *
		 * @throws IllegalArgumentException when the line is not a reference
		 */
		fun parse(line: String): LinkageReference {
			val parts = line.trim().split(' ')
			val kind = Kind.entries.firstOrNull { it.keyword == parts.first() }
			val memberParts = if (kind == Kind.CLASS) 0 else 1
			require(kind != null && parts.size >= 2 + memberParts) { "'$line' is not a linkage reference" }
			val requirements = parts.drop(2 + memberParts).map { keyword ->
				Requirement.entries.firstOrNull { it.keyword == keyword } ?: throw IllegalArgumentException("'$line' has an unknown requirement '$keyword'")
			}
			return LinkageReference(kind, parts[1].replace('.', '/'), if (memberParts == 1) parts[2] else "", requirements.toSet())
		}
	}
}
