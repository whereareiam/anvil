package me.whereareiam.anvil.platform.planning.version;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.platform.api.exception.PlatformException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads the {@code <platform>-versions.toml} resource a platform provider exposes and validates it
 * into {@link PlatformVersions}.
 *
 * <pre>{@code
 * known = ["1.16.5", "1.17.1"]
 *
 * [agent]
 * minimumJava = 11
 *
 * [verified]
 * "1.16.5" = [11, 17]
 *
 * [[java]]
 * since = "1.16.5"
 * minimum = 11
 * maximum = 16
 * preferred = 11
 * maximumBypassProperty = "Paper.IgnoreJavaVersion"
 * }</pre>
 *
 * <p>Every key is optional except the {@code [[java]]} rows and their {@code minimum} and
 * {@code preferred}; only the first row may omit {@code since}. Unknown keys are refused, so a
 * misspelt key cannot silently drop a rule.</p>
 */
public final class PlatformVersionsReader {
	private static final Set<String> DOCUMENT_KEYS = Set.of("known", "agent", "verified", "java");
	private static final Set<String> AGENT_KEYS = Set.of("minimumJava");
	private static final Set<String> ROW_KEYS = Set.of("since", "minimum", "maximum", "preferred", "maximumBypassProperty");

	private final TomlMapper mapper = new TomlMapper();

	/**
	 * Reads and validates one provider's version data.
	 *
	 * @param platform platform identifier naming the provider in failures
	 * @param resource the provider's version data resource; null when the provider ships none
	 * @return validated version data
	 * @throws PlatformException when the resource is missing, unreadable, or invalid
	 */
	public @NotNull PlatformVersions read(@NotNull String platform, @Nullable URL resource) {
		if (resource == null) throw new PlatformException("Platform '" + platform + "' supplies no version data");

		try (InputStream input = resource.openStream()) {
			return versions(mapper.readTree(input));
		} catch (IOException | IllegalArgumentException failure) {
			throw new PlatformException("Platform '" + platform + "' has invalid version data " + resource + ": "
					+ failure.getMessage(), failure);
		}
	}

	private PlatformVersions versions(JsonNode document) {
		refuseUnknownKeys(document, DOCUMENT_KEYS, "the document");
		PlatformVersions.PlatformVersionsBuilder versions = PlatformVersions.builder();
		for (JsonNode version : array(document, "known"))
			versions.knownVersion(MinecraftVersion.parse(version.asText()));

		JsonNode agent = table(document, "agent");
		refuseUnknownKeys(agent, AGENT_KEYS, "[agent]");
		if (agent.has("minimumJava")) versions.agentMinimumJava(integer(agent.path("minimumJava"), "agent.minimumJava"));

		JsonNode verified = table(document, "verified");
		for (var entry : verified.properties()) {
			Set<Integer> java = new LinkedHashSet<>();
			for (JsonNode feature : array(verified, entry.getKey()))
				java.add(integer(feature, "verified." + entry.getKey()));
			versions.verifiedJavaVersion(MinecraftVersion.parse(entry.getKey()), java);
		}

		for (JsonNode row : array(document, "java")) {
			refuseUnknownKeys(row, ROW_KEYS, "a [[java]] row");
			versions.javaCompatibility(JavaCompatibility.builder()
					.since(row.has("since") ? MinecraftVersion.parse(text(row, "since")) : null)
					.minimum(integer(row.path("minimum"), "java.minimum"))
					.maximum(row.has("maximum") ? integer(row.path("maximum"), "java.maximum") : null)
					.preferred(integer(row.path("preferred"), "java.preferred"))
					.maximumBypassProperty(row.has("maximumBypassProperty") ? text(row, "maximumBypassProperty") : null)
					.build());
		}

		return versions.build();
	}

	private static void refuseUnknownKeys(JsonNode node, Set<String> keys, String location) {
		if (!node.isObject()) return;

		for (var property : node.properties())
			if (!keys.contains(property.getKey()))
				throw new IllegalArgumentException("unknown key '" + property.getKey() + "' in " + location
						+ "; expected one of " + keys.stream().sorted().toList());
	}

	private static JsonNode table(JsonNode parent, String field) {
		JsonNode node = parent.path(field);
		if (!node.isMissingNode() && !node.isObject()) throw new IllegalArgumentException("'" + field + "' must be a table");

		return node;
	}

	private static Iterable<JsonNode> array(JsonNode parent, String field) {
		JsonNode node = parent.path(field);
		if (node.isMissingNode()) return List.of();
		if (!node.isArray()) throw new IllegalArgumentException("'" + field + "' must be an array");

		return node;
	}

	private static String text(JsonNode parent, String field) {
		JsonNode node = parent.path(field);
		if (!node.isTextual()) throw new IllegalArgumentException("'" + field + "' must be a string");

		return node.asText();
	}

	private static int integer(JsonNode node, String field) {
		if (!node.isIntegralNumber()) throw new IllegalArgumentException("'" + field + "' must be an integer");

		return node.intValue();
	}
}
