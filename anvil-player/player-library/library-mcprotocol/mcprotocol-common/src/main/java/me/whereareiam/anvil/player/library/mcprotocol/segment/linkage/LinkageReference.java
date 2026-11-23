package me.whereareiam.anvil.player.library.mcprotocol.segment.linkage;

import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A class, field or method that compiled code links against, with what the JVM requires of it when linking.
 *
 * <p>{@link #getOwner()} is a JVM internal class name ({@code a/b/C$D}); {@link #getMember()} is
 * {@code name:descriptor} for a field and {@code name(parameters)return} for a method, and empty for a class. A
 * segment's linkage manifest holds one reference per line: the kind, the owner as a binary class name, the member,
 * then the requirements as keywords in a fixed order:</p>
 *
 * <pre>{@code
 * class a.b.C public class
 * class a.b.Listener public interface
 * class a.b.Packet public
 * field a.b.C NAME:Ljava/lang/String; public static
 * method a.b.C send(La/b/Packet;)V public instance class
 * method a.b.Listener onEvent()V public instance interface
 * method a.b.Base helper()V protected instance
 * }</pre>
 */
@Value
public class LinkageReference implements Comparable<LinkageReference> {
	private static final Comparator<LinkageReference> ORDER = Comparator.comparing(LinkageReference::getOwner)
			.thenComparing(LinkageReference::getKind)
			.thenComparing(LinkageReference::getMember)
			.thenComparing(LinkageReference::line);

	@NotNull Kind kind;
	@NotNull String owner;
	@NotNull String member;
	@NotNull Set<Requirement> requirements;

	/**
	 * Creates a reference.
	 *
	 * @param kind what the reference names
	 * @param owner internal name of the class that owns it
	 * @param member {@code name:descriptor} of a field, {@code name(parameters)return} of a method, empty for a class
	 * @param requirements what a release must provide for the reference to link
	 */
	public LinkageReference(@NotNull Kind kind, @NotNull String owner, @NotNull String member, @NotNull Set<Requirement> requirements) {
		this.kind = kind;
		this.owner = owner;
		this.member = member;
		this.requirements = requirements.isEmpty()
				? Collections.emptySet()
				: Collections.unmodifiableSet(EnumSet.copyOf(requirements));
	}

	/**
	 * Reads one manifest line.
	 *
	 * @param line the line, surrounding whitespace ignored
	 * @param source where the line was read, for the failure message
	 * @return the reference
	 * @throws IllegalArgumentException when the line is not a reference or names an unknown requirement
	 */
	public static @NotNull LinkageReference parse(@NotNull String line, @NotNull String source) {
		String trimmed = line.trim();
		String[] parts = trimmed.split(" ");
		Kind kind = Kind.of(parts[0]);
		int memberParts = kind == Kind.CLASS ? 0 : 1;
		if (kind == null || parts.length < 2 + memberParts) throw invalid(trimmed, source);

		String member = memberParts == 1 ? parts[2] : "";
		if (kind == Kind.FIELD && member.indexOf(':') <= 0) throw invalid(trimmed, source);
		if (kind == Kind.METHOD && member.indexOf('(') <= 0) throw invalid(trimmed, source);

		Set<Requirement> requirements = EnumSet.noneOf(Requirement.class);
		for (String keyword : Arrays.asList(parts).subList(2 + memberParts, parts.length)) {
			Requirement requirement = Requirement.of(keyword);
			if (requirement == null)
				throw new IllegalArgumentException("'" + trimmed + "' in " + source + " has an unknown requirement '" + keyword + "'");

			requirements.add(requirement);
		}
		return new LinkageReference(kind, parts[1].replace('.', '/'), member, requirements);
	}

	/**
	 * Returns the manifest line of this reference.
	 *
	 * @return kind, binary owner name, member and requirement keywords in their fixed order
	 */
	public @NotNull String line() {
		List<String> parts = new ArrayList<>();
		parts.add(kind.keyword());
		parts.add(binaryOwner());
		if (!member.isEmpty()) parts.add(member);
		requirements.stream().sorted().map(Requirement::keyword).forEach(parts::add);
		return String.join(" ", parts);
	}

	/**
	 * Returns the owner as a binary class name, as failures name it.
	 *
	 * @return owner with dots for slashes
	 */
	public @NotNull String binaryOwner() {
		return owner.replace('/', '.');
	}

	/**
	 * Returns this reference for another owner, with other requirements.
	 *
	 * @param owner internal name of the new owner
	 * @param requirements the new requirements
	 * @return the moved reference
	 */
	public @NotNull LinkageReference with(@NotNull String owner, @NotNull Set<Requirement> requirements) {
		return new LinkageReference(kind, owner, member, requirements);
	}

	@Override
	public int compareTo(@NotNull LinkageReference other) {
		return ORDER.compare(this, other);
	}

	@Override
	public String toString() {
		return line();
	}

	private static IllegalArgumentException invalid(String line, String source) {
		return new IllegalArgumentException("'" + line + "' in " + source + " is not a linkage reference");
	}

	/**
	 * What a reference names.
	 */
	public enum Kind {
		CLASS, FIELD, METHOD;

		/**
		 * Returns the manifest keyword of the kind.
		 *
		 * @return {@code class}, {@code field} or {@code method}
		 */
		public @NotNull String keyword() {
			return name().toLowerCase(Locale.ROOT);
		}

		private static @Nullable Kind of(String keyword) {
			return Arrays.stream(values()).filter(kind -> kind.keyword().equals(keyword)).findFirst().orElse(null);
		}
	}

	/**
	 * What a release must provide for a reference to link. Each failure is an {@link IncompatibleClassChangeError} or
	 * an {@link IllegalAccessError} at run time, not a missing member.
	 */
	public enum Requirement {
		/**
		 * The class or member is public, as it was on the release the code compiled against.
		 */
		PUBLIC,

		/**
		 * The member is at least protected, as it was on the release the code compiled against.
		 */
		PROTECTED,

		/**
		 * The member is static: {@code getstatic}, {@code putstatic} and {@code invokestatic} link it.
		 */
		STATIC,

		/**
		 * The member is not static: field access and invocations through an instance link it.
		 */
		INSTANCE,

		/**
		 * The owner is a class: a superclass, an instantiated class, or the owner of a class method reference.
		 */
		CLASS,

		/**
		 * The owner is an interface: an implemented interface or the owner of an interface method reference.
		 */
		INTERFACE;

		/**
		 * Returns the manifest keyword of the requirement.
		 *
		 * @return the lower-case name, such as {@code public}
		 */
		public @NotNull String keyword() {
			return name().toLowerCase(Locale.ROOT);
		}

		private static @Nullable Requirement of(String keyword) {
			return Arrays.stream(values()).filter(requirement -> requirement.keyword().equals(keyword)).findFirst().orElse(null);
		}
	}
}
