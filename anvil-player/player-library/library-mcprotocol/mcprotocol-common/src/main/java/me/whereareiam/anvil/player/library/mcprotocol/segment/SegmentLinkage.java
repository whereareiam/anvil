package me.whereareiam.anvil.player.library.mcprotocol.segment;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.player.library.mcprotocol.segment.linkage.ClassPath;
import me.whereareiam.anvil.player.library.mcprotocol.segment.linkage.LinkageReference;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The references of one segment's {@value SegmentDescriptor#LINKAGE}, checked against the class files of a class
 * path: the closure and roots a worker runs on, or, in the build, the locked closure of each release a segment
 * serves.
 *
 * <p>Each line names a class ({@code class a.b.C}), a field ({@code field a.b.C name:Ldescriptor;}) or a method
 * ({@code method a.b.C name(parameters)return}) with binary class names and JVM descriptors, followed by what the
 * build recorded the segment's code to require of it, as keywords: {@code public} or {@code protected} access, a
 * {@code static} or {@code instance} member, and an owner that is a {@code class} or an {@code interface}:</p>
 *
 * <pre>{@code
 * class a.b.C public class
 * field a.b.C NAME:Ljava/lang/String; public static
 * method a.b.Listener onEvent()V public instance interface
 * }</pre>
 *
 * <p>The check reads class files and loads no class: {@link ClassPath} resolves each member as the JVM links it, also
 * through superclasses and interfaces, and enforces each requirement, so a release that turned a class into an
 * interface, an instance member into a static one or a public member into a narrower one fails here instead of with an
 * {@link IncompatibleClassChangeError} or {@link IllegalAccessError} later.</p>
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class SegmentLinkage {
	private final List<LinkageReference> references;

	/**
	 * Reads a linkage manifest.
	 *
	 * @param input content of {@value SegmentDescriptor#LINKAGE}
	 * @param source location used in failure messages
	 * @return the manifest's references in file order
	 * @throws IllegalArgumentException when a line is not a reference or names an unknown requirement
	 */
	public static @NotNull SegmentLinkage read(@NotNull InputStream input, @NotNull String source) {
		List<LinkageReference> references = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isBlank()) continue;

				references.add(LinkageReference.parse(line, source));
			}
		} catch (IOException exception) {
			throw new IllegalArgumentException("Could not read the linkage manifest " + source, exception);
		}

		return new SegmentLinkage(List.copyOf(references));
	}

	/**
	 * Returns the manifest's references.
	 *
	 * @return references in file order
	 */
	public @NotNull List<LinkageReference> references() {
		return references;
	}

	/**
	 * Keeps the references to classes of a library, which tell them apart from references to classes of the build that
	 * do not change between the library's releases.
	 *
	 * @param library the library's classes, such as the release module a segment compiles against
	 * @return the references whose owner the library holds, in file order
	 */
	public @NotNull SegmentLinkage within(@NotNull ClassPath library) {
		return new SegmentLinkage(references.stream().filter(reference -> library.contains(reference.getOwner())).toList());
	}

	/**
	 * Finds why each reference that does not link on a class path fails.
	 *
	 * @param classpath the class path the segment runs on
	 * @return one failure per failing reference in file order, such as {@code missing method a.b.C m()V}
	 */
	public @NotNull List<String> failures(@NotNull ClassPath classpath) {
		List<String> failures = new ArrayList<>();
		for (LinkageReference reference : references) {
			String failure = classpath.linkageFailure(reference);
			if (failure != null) failures.add(failure);
		}
		return failures;
	}

	/**
	 * Finds the first reference that does not link on a class path.
	 *
	 * @param classpath the class path the segment runs on
	 * @return why the first failing reference does not link, such as {@code missing method a.b.C m()V} or
	 * {@code a.b.C is an interface, linked as a class}; empty when every reference links
	 */
	public @NotNull Optional<String> firstFailure(@NotNull ClassPath classpath) {
		for (LinkageReference reference : references) {
			String failure = classpath.linkageFailure(reference);
			if (failure != null) return Optional.of(failure);
		}

		return Optional.empty();
	}
}
