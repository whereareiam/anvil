package me.whereareiam.anvil.player.library.mcprotocol.segment;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.player.library.mcprotocol.segment.linkage.ClassPath;
import me.whereareiam.anvil.player.library.mcprotocol.segment.linkage.JdkClasses;
import me.whereareiam.anvil.player.library.mcprotocol.segment.linkage.LinkageCollector;
import me.whereareiam.anvil.player.library.mcprotocol.segment.linkage.LinkageReference;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.regex.Pattern;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The build's entry to the segment linkage of this library, as the JDK's tool interface: the build runs it from the
 * library's own JAR, so the manifest a segment ships, the check of the releases it serves and the worker's self-check
 * are one implementation.
 *
 * <p>Each run names one command and its options; a path list joins paths with the platform's path separator, and a
 * JDK is the Java home of the toolchain whose classes the class paths run on:</p>
 *
 * <pre>{@code
 * write --classes <paths> --classpath <paths> --project-classes <paths> --jdk <home> --output <file>
 * references --linkage <file> --library <paths> --jdk <home>
 * check --linkage <file> --library <paths> --classpath <paths> --jdk <home>
 * uses --of <internal names> --files <paths>
 * }</pre>
 *
 * <p>{@code write} writes the linkage manifest of the classes in the class directories {@code --classes}, compiled
 * against {@code --classpath}, leaving out the packages of those classes and of {@code --project-classes}.
 * {@code references} prints the manifest's references to classes of {@code --library}, one manifest line each, and
 * {@code check} prints why each of them does not link on {@code --classpath}, one failure per line. {@code uses}
 * prints, for each class file of {@code --files}, every class it references whose top-level class is one of the
 * comma-separated {@code --of}, as the file's path and the class's internal name separated by a tab. A run that
 * answers returns 0; a run whose arguments or class files cannot be read prints why to the error stream and returns
 * 1.</p>
 */
public final class SegmentLinkageTool implements ToolProvider {
	/**
	 * The tool's name, by which the build finds it.
	 */
	public static final String NAME = "anvil-segment-linkage";

	@Override
	public String name() {
		return NAME;
	}

	@Override
	public int run(PrintWriter out, PrintWriter err, String... args) {
		try {
			if (args.length == 0) throw new IllegalArgumentException("Name a command: write, references, check or uses");

			Options options = Options.parse(Arrays.asList(args).subList(1, args.length));
			switch (args[0]) {
				case "write" -> write(options);
				case "references" -> references(options, out);
				case "check" -> check(options, out);
				case "uses" -> uses(options, out);
				default -> throw new IllegalArgumentException("Unknown command '" + args[0] + "'; use write, references, check or uses");
			}
			out.flush();
			return 0;
		} catch (IllegalArgumentException | IOException | UncheckedIOException failure) {
			err.println(failure.getMessage());
			err.flush();
			return 1;
		}
	}

	private static void write(Options options) throws IOException {
		LinkageCollector collector = new LinkageCollector();
		for (Path root : options.paths("classes")) {
			if (!Files.isDirectory(root)) continue;

			for (Path file : classFiles(root)) collector.add(Files.readAllBytes(file), file.toString());
		}

		SortedSet<LinkageReference> references;
		try (JdkClasses jdk = JdkClasses.open(options.path("jdk"));
		     ClassPath classpath = ClassPath.open(options.paths("classpath"), jdk);
		     ClassPath projectClasses = ClassPath.open(options.paths("project-classes"), jdk)) {
			references = collector.segmentReferences(classpath, projectClasses);
		}

		Path output = options.path("output");
		Files.createDirectories(output.toAbsolutePath().getParent());
		Files.writeString(output, references.stream().map(reference -> reference.line() + "\n").collect(Collectors.joining()),
				StandardCharsets.UTF_8);
	}

	private static void references(Options options, PrintWriter out) throws IOException {
		try (JdkClasses jdk = JdkClasses.open(options.path("jdk"));
		     ClassPath library = ClassPath.open(options.paths("library"), jdk)) {
			linkage(options.path("linkage")).within(library).references().forEach(reference -> out.println(reference.line()));
		}
	}

	private static void check(Options options, PrintWriter out) throws IOException {
		try (JdkClasses jdk = JdkClasses.open(options.path("jdk"));
		     ClassPath library = ClassPath.open(options.paths("library"), jdk);
		     ClassPath classpath = ClassPath.open(options.paths("classpath"), jdk)) {
			linkage(options.path("linkage")).within(library).failures(classpath).forEach(out::println);
		}
	}

	private static void uses(Options options, PrintWriter out) throws IOException {
		Set<String> used = Set.copyOf(Arrays.asList(options.value("of").split(",")));
		for (Path file : options.paths("files")) {
			LinkageCollector collector = new LinkageCollector();
			collector.add(Files.readAllBytes(file), file.toString());
			for (String referenced : collector.referencedClasses()) {
				int nested = referenced.indexOf('$');
				if (used.contains(nested < 0 ? referenced : referenced.substring(0, nested))) out.println(file + "\t" + referenced);
			}
		}
	}

	private static SegmentLinkage linkage(Path manifest) throws IOException {
		try (InputStream input = Files.newInputStream(manifest)) {
			return SegmentLinkage.read(input, manifest.toString());
		}
	}

	private static List<Path> classFiles(Path root) throws IOException {
		try (Stream<Path> found = Files.walk(root)) {
			return found.filter(Files::isRegularFile).filter(file -> file.getFileName().toString().endsWith(".class")).sorted().toList();
		}
	}

	/**
	 * The options of one run, each given once as {@code --name value}.
	 */
	@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
	private static final class Options {
		private final Map<String, String> values;

		private static Options parse(List<String> arguments) {
			Map<String, String> values = new HashMap<>();
			for (int index = 0; index < arguments.size(); index += 2) {
				String option = arguments.get(index);
				if (!option.startsWith("--") || index + 1 >= arguments.size())
					throw new IllegalArgumentException("Expected an option and its value, not '" + option + "'");
				if (values.put(option.substring(2), arguments.get(index + 1)) != null)
					throw new IllegalArgumentException("The option " + option + " is given more than once");
			}
			return new Options(values);
		}

		private String value(String name) {
			String value = values.get(name);
			if (value == null) throw new IllegalArgumentException("The option --" + name + " is missing");

			return value;
		}

		private Path path(String name) {
			return Path.of(value(name));
		}

		private List<Path> paths(String name) {
			List<Path> paths = new ArrayList<>();
			for (String entry : value(name).split(Pattern.quote(File.pathSeparator)))
				if (!entry.isEmpty()) paths.add(Path.of(entry));
			return paths;
		}
	}
}
