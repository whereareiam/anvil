package me.whereareiam.anvil.environment.provisioning.java.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.environment.provisioning.java.api.JavaPackageSource;
import me.whereareiam.anvil.environment.provisioning.java.distribution.JavaDistributionDescriptor;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Resolves verified JDK packages through the Foojay Disco catalog.
 */
public final class FoojayJavaCatalog {
	private final JavaPackageSource packages;
	private final Map<String, JavaDistributionDescriptor> distributions;
	private final ObjectMapper mapper = new ObjectMapper();

	/**
	 * Creates a Foojay catalog backed by the providers selected by the resolver.
	 *
	 * @param packages Java package source used for catalog requests
	 * @param distributions providers keyed by their Anvil distribution identifiers
	 */
	public FoojayJavaCatalog(JavaPackageSource packages, Map<String, JavaDistributionDescriptor> distributions) {
		this.packages = packages;
		this.distributions = Map.copyOf(distributions);
	}

	public Package select(String distribution, int version, @Nullable String release, String os, String architecture) throws IOException {
		JavaDistributionDescriptor provider = distributions.get(distribution);
		if (provider == null) throw new ProvisioningException("Unsupported Java distribution: " + distribution);
		String distro = provider.catalogId();
		String selectedVersion = release == null ? Integer.toString(version) : release;
		String query = "https://api.foojay.io/disco/v3.0/packages?package_type=jdk&latest=available"
				+ "&version=" + URLEncoder.encode(selectedVersion, StandardCharsets.UTF_8)
				+ "&distro=" + URLEncoder.encode(distro, StandardCharsets.UTF_8)
				+ "&operating_system=" + os + "&architecture=" + architecture
				+ "&archive_type=tar.gz&libc_type=glibc&directly_downloadable=true";

		JsonNode result = mapper.readTree(packages.catalog(URI.create(query))).path("result");
		if (!result.isArray() || result.isEmpty())
			throw new ProvisioningException("Foojay has no Java package for " + distro + " " + selectedVersion);

		String id = result.get(0).path("id").asText();
		JsonNode packageInfo = mapper.readTree(packages.catalog(URI.create("https://api.foojay.io/disco/v3.0/ids/" + id)))
				.path("result").path(0);

		return archive(packageInfo.path("direct_download_uri").asText(), packageInfo.path("checksum").asText());
	}

	private Package archive(String url, String checksum) {
		if (!url.startsWith("https://") || !checksum.matches("[a-fA-F0-9]{64}"))
			throw new ProvisioningException("Foojay returned an invalid JDK package");

		return new Package(URI.create(url), checksum.toLowerCase(), url.endsWith(".zip")
				? ".zip"
				: ".tar.gz");
	}

	public record Package(URI uri, String sha256, String extension) {
	}
}
