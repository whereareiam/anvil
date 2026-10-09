package me.whereareiam.anvil.protocol.mcprotocol.catalog;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.protocol.api.model.ProtocolRelease;
import me.whereareiam.anvil.protocol.api.type.ProtocolFeature;
import me.whereareiam.anvil.protocol.mcprotocol.model.ReleaseArtifact;
import me.whereareiam.anvil.protocol.mcprotocol.model.ReleaseDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads and validates one MCProtocolLib release data file in the schema of {@code mcprotocol-releases.toml}.
 *
 * <p>Each {@code [[release]]} row declares {@code version}, {@code module}, {@code protocol}, {@code minecraft},
 * {@code verified}, {@code java}, optional {@code features} and a non-empty {@code [[release.artifact]]} closure
 * of {@code module}, {@code url} and {@code sha256}. Within one file, release versions and Minecraft versions are
 * unique, verified versions are listed in {@code minecraft}, the closure includes the release module and has
 * unique modules and file names, and a pin is empty or 64 lower-case hexadecimal digits. A release with an empty
 * pin is listed but cannot be launched until it is pinned. Build-logic reads the same schema when it compiles and
 * checks segments.</p>
 */
final class McProtocolReleaseReader {
	private static final Set<String> RELEASE_FIELDS = Set.of("version", "module", "protocol", "minecraft", "verified", "java", "features", "artifact");
	private static final Set<String> ARTIFACT_FIELDS = Set.of("module", "url", "sha256");
	private static final Pattern MODULE = Pattern.compile("[^:\\s]+:[^:\\s]+:[^:\\s]+");
	private static final Pattern ARTIFACT_MODULE = Pattern.compile("[^:\\s]+:[^:\\s]+:[^:\\s]+(:[^:\\s]+)?");
	private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
	private static final int MINIMUM_JAVA = 8;
	private static final String PIN_TASK = "./gradlew :anvil-protocol:protocol-mcprotocol:pinLibraryReleases";

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

		return new Rows(source, additional).read(document);
	}

	@RequiredArgsConstructor
	private static final class Rows {
		private final String source;
		private final boolean additional;

		private List<ReleaseDefinition> read(JsonNode document) {
			if (document == null || !document.isObject()) fail("must declare at least one [[release]]");
			unknownFields(document, Set.of("release"), "the file");
			JsonNode rows = document.path("release");
			if (!rows.isArray() || rows.isEmpty()) fail("must declare at least one [[release]]");

			List<ReleaseDefinition> releases = new ArrayList<>();
			Set<String> versions = new HashSet<>();
			Set<MinecraftVersion> minecraft = new HashSet<>();
			for (int index = 0; index < rows.size(); index++) {
				ReleaseDefinition definition = release(rows.get(index), "release #" + (index + 1));
				ProtocolRelease release = definition.getRelease();
				if (!versions.add(release.getLibraryVersion())) fail("declares release " + release.getLibraryVersion() + " more than once");
				for (MinecraftVersion version : release.getMinecraftVersions())
					if (!minecraft.add(version)) fail("assigns Minecraft " + version + " to more than one release");

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
					.minecraftVersions(minecraft)
					.verifiedVersions(verified)
					.protocolNumber(number(row, "protocol", context, 1))
					.javaVersion(number(row, "java", context, MINIMUM_JAVA))
					.features(features(row, context))
					.additional(additional)
					.launchRefusal(launchRefusal(version, artifacts))
					.build();
			return ReleaseDefinition.builder().release(release).module(module).artifacts(artifacts).build();
		}

		private @Nullable String launchRefusal(String version, List<ReleaseArtifact> artifacts) {
			for (ReleaseArtifact artifact : artifacts) {
				if (artifact.pinned()) continue;

				String remedy = additional ? "set its sha256 in " + source : "run `" + PIN_TASK + "`";
				return "MCProtocolLib release " + version + " has no pinned checksum for " + artifact.getModule() + "; " + remedy;
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
				MinecraftVersion version;
				try {
					version = MinecraftVersion.parse(text);
				} catch (IllegalArgumentException exception) {
					throw new IllegalArgumentException(source + ": " + context + " " + field + ": " + exception.getMessage(), exception);
				}
				if (versions.contains(version)) fail(context + " lists " + field + " " + version + " more than once");

				versions.add(version);
			}

			return versions;
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
