package me.whereareiam.anvil.platform.paper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.platform.api.exception.PlatformException;
import me.whereareiam.anvil.platform.api.model.PlatformContext;
import me.whereareiam.anvil.platform.api.model.ResolvedDistribution;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves local artifacts or checksum-pinned Paper builds from PaperMC Fill.
 */
final class PaperDistributionResolver {
	private final ObjectMapper mapper = new ObjectMapper();

	ResolvedDistribution resolve(MinecraftServer process, PlatformContext context) throws IOException {
		var distribution = process.getDistribution();
		if (distribution.isLocal()) {
			Path jar = distribution.getLocalJar().toAbsolutePath().normalize();
			if (!Files.isRegularFile(jar))
				throw new PlatformException("Paper JAR does not exist: " + jar);
			return ResolvedDistribution.builder().jar(jar).description("local Paper").build();
		}

		String version = required(distribution.getVersion(), "Paper version");
		String build = required(distribution.getBuild(), "Paper build");
		URI metadata = URI.create("https://fill.papermc.io/v3/projects/paper/versions/" + version + "/builds");
		JsonNode selected = selectBuild(mapper.readTree(context.getArtifactResolver().read(metadata)), build);
		JsonNode download = selected.path("downloads").path("server:default");
		String name = required(download.path("name").asText(), "artifact filename");
		if (!Path.of(name).getFileName().toString().equals(name))
			throw new PlatformException("Invalid Paper artifact filename: " + name);
		String checksum = required(download.path("checksums").path("sha256").asText(), "artifact SHA-256");
		URI url = URI.create(required(download.path("url").asText(), "artifact URL"));
		Path destination = context.getCacheDirectory().resolve("distributions/paper")
				.resolve(version).resolve(selected.path("id").asText()).resolve(name);
		Path jar = context.getArtifactResolver().obtain(url, destination, checksum);
		return ResolvedDistribution.builder().jar(jar)
				.description("Paper " + version + " build " + selected.path("id").asText()).build();
	}

	private JsonNode selectBuild(JsonNode builds, String requested) {
		if (builds == null || !builds.isArray())
			throw new PlatformException("Invalid PaperMC Fill build response for Paper");
		for (JsonNode build : builds)
			if (requested.equals("latest") || requested.equals(build.path("id").asText()))
				return build;
		throw new PlatformException("Paper build " + requested + " was not found");
	}

	private String required(String value, String label) {
		if (value == null || value.isBlank())
			throw new PlatformException(label + " is required for a remote distribution");
		return value;
	}
}
