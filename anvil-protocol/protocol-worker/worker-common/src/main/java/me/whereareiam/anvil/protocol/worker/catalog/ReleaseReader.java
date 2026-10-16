package me.whereareiam.anvil.protocol.worker.catalog;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.Edition;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.protocol.api.model.ProtocolRelease;
import me.whereareiam.anvil.protocol.api.type.ProtocolFeature;
import me.whereareiam.anvil.protocol.api.worker.model.WorkerLibrary;
import me.whereareiam.anvil.protocol.worker.model.ReleaseArtifact;
import me.whereareiam.anvil.protocol.worker.model.ReleaseDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads and validates one release data file of a protocol library in the schema of {@code <id>-releases.toml}.
 *
 * <p>Each {@code [[release]]} row declares {@code version}, {@code edition}, {@code module}, {@code protocol},
 * {@code minecraft}, {@code verified}, {@code java}, optional {@code features} and a non-empty
 * {@code [[release.artifact]]} closure of {@code module}, {@code url} and {@code sha256}. The edition is an opaque
 * identifier that selection only compares with a server's. {@code protocol} is either one number that every listed
 * version speaks or a {@code [release.protocol]} table that maps each listed version to its own number:</p>
 *
 * <pre>{@code
 * [[release]]
 * version = "2.0.0"
 * edition = "example"
 * module = "org.example:client:2.0.0"
 * minecraft = ["1.20.0", "1.20.10"]
 * verified = []
 * java = 8
 *
 * [release.protocol]
 * "1.20.0" = 589
 * "1.20.10" = 594
 * }</pre>
 *
 * <p>Within one file, release versions and release keys are unique, a Minecraft version belongs to at most one
 * release of its edition, verified versions are listed in {@code minecraft}, the closure includes the release module
 * and has unique modules and file names, and a pin is empty or 64 lower-case hexadecimal digits. A release with an empty
 * pin is listed but cannot be launched until it is pinned. Build-logic reads the same schema when it compiles and
 * checks segments.</p>
 */
@RequiredArgsConstructor
final class ReleaseReader {
	private static final Set<String> RELEASE_FIELDS = Set.of("version", "edition", "module", "protocol", "minecraft", "verified", "java", "features", "artifact");
	private static final Set<String> ARTIFACT_FIELDS = Set.of("module", "url", "sha256");
	private static final Pattern MODULE = Pattern.compile("[^:\\s]+:[^:\\s]+:[^:\\s]+");
	private static final Pattern ARTIFACT_MODULE = Pattern.compile("[^:\\s]+:[^:\\s]+:[^:\\s]+(:[^:\\s]+)?");
	private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
	private static final int MINIMUM_JAVA = 8;

	private final @NotNull WorkerLibrary library;
	private final TomlMapper mapper = new TomlMapper();

	/**
	 * Reads every release of one file in file order.
	 *
	 * @param input file content
	 * @param source file name used in failure messages
	 * @param additional whether the file holds user-supplied releases
	 * @return validated releases
	 * @throws IllegalArgumentException when the file is not valid TOML or violates the schema
	 */
	@NotNull List<ReleaseDefinition> read(@NotNull InputStream input, @NotNull String source, boolean additional) {
		JsonNode document;
		try {
			document = mapper.readTree(input);
		} catch (JacksonException exception) {
			throw new IllegalArgumentException(source + " is not valid TOML: " + exception.getOriginalMessage(), exception);
		} catch (IOException exception) {
			throw new IllegalArgumentException("Could not read " + source, exception);
		}

		return new Rows(library, source, additional).read(document);
	}

	@RequiredArgsConstructor
	private static final class Rows {
		private final WorkerLibrary library;
		private final String source;
		private final boolean additional;

		private List<ReleaseDefinition> read(JsonNode document) {
			if (document == null || !document.isObject()) fail("must declare at least one [[release]]");
			unknownFields(document, Set.of("release"), "the file");
			JsonNode rows = document.path("release");
			if (!rows.isArray() || rows.isEmpty()) fail("must declare at least one [[release]]");

			List<ReleaseDefinition> releases = new ArrayList<>();
			Set<String> versions = new HashSet<>();
			Set<MinecraftVersion> keys = new HashSet<>();
			Set<String> minecraft = new HashSet<>();
			for (int index = 0; index < rows.size(); index++) {
				ReleaseDefinition definition = release(rows.get(index), "release #" + (index + 1));
				ProtocolRelease release = definition.getRelease();
				if (!versions.add(release.getLibraryVersion())) fail("declares release " + release.getLibraryVersion() + " more than once");
				for (MinecraftVersion version : release.getMinecraftVersions())
					if (!minecraft.add(release.getEdition() + " " + version))
						fail("assigns " + release.getEdition() + " Minecraft " + version + " to more than one release");
				// Segments name a release by its key alone, whatever its edition.
				if (!keys.add(release.version())) fail("keys more than one release by Minecraft " + release.version());

				releases.add(definition);
			}

			return List.copyOf(releases);
		}

		private ReleaseDefinition release(JsonNode row, String position) {
			if (!row.isObject()) fail(position + " must be a table");
			String version = text(row, "version", position);
			String context = "release " + version;
			unknownFields(row, RELEASE_FIELDS, context);

			List<MinecraftVersion> minecraft = versions(row, "minecraft", context);
			if (minecraft.isEmpty()) fail(context + " must list at least one Minecraft version");
			List<MinecraftVersion> verified = versions(row, "verified", context);
			for (MinecraftVersion candidate : verified)
				if (!minecraft.contains(candidate)) fail(context + " verifies Minecraft " + candidate + ", which it does not list");

			String module = module(row, "module", context, MODULE);
			List<ReleaseArtifact> artifacts = artifacts(row.path("artifact"), context);
			if (artifacts.stream().noneMatch(artifact -> artifact.getModule().equals(module)))
				fail(context + " must include its module " + module + " in its artifacts");

			ProtocolRelease release = ProtocolRelease.builder()
					.libraryVersion(version)
					.edition(edition(row, context))
					.minecraftVersions(minecraft)
					.verifiedVersions(verified)
					.protocolNumbers(protocols(row, minecraft, context))
					.javaVersion(number(row, "java", context, MINIMUM_JAVA))
					.features(features(row, context))
					.additional(additional)
					.launchRefusal(launchRefusal(version, artifacts))
					.build();
			return ReleaseDefinition.builder().release(release).module(module).artifacts(artifacts).build();
		}

		private Edition edition(JsonNode row, String context) {
			String edition = text(row, "edition", context);
			try {
				return Edition.of(edition);
			} catch (IllegalArgumentException exception) {
				throw new IllegalArgumentException(source + ": " + context + " edition: " + exception.getMessage(), exception);
			}
		}

		/**
		 * Reads the protocol number of every listed version: one number shared by all of them, or a table with one
		 * number per listed version.
		 */
		private Map<MinecraftVersion, Integer> protocols(JsonNode row, List<MinecraftVersion> minecraft, String context) {
			JsonNode protocol = row.path("protocol");
			Map<MinecraftVersion, Integer> protocols = new LinkedHashMap<>();
			if (!protocol.isObject()) {
				int shared = number(row, "protocol", context, 1);
				minecraft.forEach(version -> protocols.put(version, shared));
				return protocols;
			}

			for (var entry : protocol.properties()) {
				MinecraftVersion version = version(entry.getKey(), context + " protocol");
				if (!minecraft.contains(version)) fail(context + " maps protocol of Minecraft " + version + ", which it does not list");
				if (protocols.containsKey(version)) fail(context + " maps protocol of Minecraft " + version + " more than once");

				protocols.put(version, number(protocol, entry.getKey(), context + " protocol", 1));
			}
			for (MinecraftVersion version : minecraft)
				if (!protocols.containsKey(version)) fail(context + " maps no protocol for Minecraft " + version);

			return protocols;
		}

		private @Nullable String launchRefusal(String version, List<ReleaseArtifact> artifacts) {
			for (ReleaseArtifact artifact : artifacts) {
				if (artifact.pinned()) continue;

				String remedy = additional ? "set its sha256 in " + source : "run `" + library.getPinTask() + "`";
				return library.getName() + " release " + version + " has no pinned checksum for " + artifact.getModule() + "; " + remedy;
			}
			return null;
		}

		private List<ReleaseArtifact> artifacts(JsonNode rows, String context) {
			if (!rows.isArray() || rows.isEmpty()) fail(context + " must declare its runtime closure as [[release.artifact]]");

			List<ReleaseArtifact> artifacts = new ArrayList<>();
			Set<String> modules = new HashSet<>();
			Set<String> files = new HashSet<>();
			for (JsonNode row : rows) {
				if (!row.isObject()) fail(context + " artifacts must be tables");
				String module = module(row, "module", context + " artifact", ARTIFACT_MODULE);
				String artifactContext = context + " artifact " + module;
				unknownFields(row, ARTIFACT_FIELDS, artifactContext);

				JsonNode sha256 = row.path("sha256");
				if (!sha256.isTextual() || !(sha256.asText().isEmpty() || SHA256.matcher(sha256.asText()).matches()))
					fail(artifactContext + " must have an empty or 64-digit lower-case hex sha256");

				ReleaseArtifact artifact = ReleaseArtifact.builder()
						.module(module)
						.url(url(text(row, "url", artifactContext), artifactContext))
						.sha256(sha256.asText())
						.build();
				if (!modules.add(module)) fail(context + " lists artifact " + module + " more than once");
				if (!files.add(artifact.fileName())) fail(context + " lists the file " + artifact.fileName() + " more than once");

				artifacts.add(artifact);
			}

			return artifacts;
		}

		private URI url(String text, String context) {
			try {
				URI url = new URI(text);
				String scheme = url.getScheme();
				boolean web = "https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme);
				if (!web || url.getPath() == null || url.getPath().endsWith("/") || url.getPath().isEmpty())
					fail(context + " url '" + text + "' must be an http(s) URL of a file");

				return url;
			} catch (URISyntaxException exception) {
				throw new IllegalArgumentException(source + ": " + context + " url '" + text + "' is not a URI", exception);
			}
		}

		private Set<ProtocolFeature> features(JsonNode row, String context) {
			Set<ProtocolFeature> features = new HashSet<>();
			for (String name : strings(row, "features", context, false)) {
				ProtocolFeature feature = Arrays.stream(ProtocolFeature.values())
						.filter(candidate -> candidate.name().equals(name))
						.findFirst()
						.orElseThrow(() -> new IllegalArgumentException(source + ": " + context + " declares unknown feature "
								+ name + "; known: " + Arrays.toString(ProtocolFeature.values())));
				features.add(feature);
			}

			return features;
		}

		private String module(JsonNode row, String field, String context, Pattern pattern) {
			String value = text(row, field, context);
			if (!pattern.matcher(value).matches()) fail(context + " " + field + " '" + value + "' must be a group:name:version coordinate");

			return value;
		}

		private String text(JsonNode row, String field, String context) {
			JsonNode value = row.path(field);
			if (!value.isTextual() || value.asText().isBlank()) fail(context + " must declare a non-blank " + field);

			return value.asText();
		}

		private int number(JsonNode row, String field, String context, int minimum) {
			JsonNode value = row.path(field);
			if (!value.isIntegralNumber() || !value.canConvertToInt() || value.asInt() < minimum)
				fail(context + " must declare " + field + " as an integer of at least " + minimum);

			return value.asInt();
		}

		private List<MinecraftVersion> versions(JsonNode row, String field, String context) {
			List<MinecraftVersion> versions = new ArrayList<>();
			for (String text : strings(row, field, context, true)) {
				MinecraftVersion version = version(text, context + " " + field);
				if (versions.contains(version)) fail(context + " lists " + field + " " + version + " more than once");

				versions.add(version);
			}

			return versions;
		}

		private MinecraftVersion version(String text, String context) {
			try {
				return MinecraftVersion.parse(text);
			} catch (IllegalArgumentException exception) {
				throw new IllegalArgumentException(source + ": " + context + ": " + exception.getMessage(), exception);
			}
		}

		private List<String> strings(JsonNode row, String field, String context, boolean required) {
			JsonNode value = row.path(field);
			if (value.isMissingNode() && !required) return List.of();

			boolean valid = value.isArray();
			for (JsonNode element : value) valid &= element.isTextual() && !element.asText().isBlank();
			if (!valid) fail(context + " must declare " + field + " as an array of strings");

			List<String> strings = new ArrayList<>();
			value.forEach(element -> strings.add(element.asText()));
			return strings;
		}

		private void unknownFields(JsonNode node, Set<String> known, String context) {
			List<String> unknown = new ArrayList<>();
			node.fieldNames().forEachRemaining(name -> {
				if (!known.contains(name)) unknown.add(name);
			});
			if (!unknown.isEmpty()) fail(context + " has unknown fields " + unknown + "; expected " + known.stream().sorted().toList());
		}

		private void fail(String message) {
			throw new IllegalArgumentException(source + ": " + message);
		}
	}
}
