package me.whereareiam.anvil.environment.provisioning.java.catalog;

import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.api.model.java.JavaArchive;
import me.whereareiam.anvil.environment.provisioning.java.api.JavaPackageSource;
import me.whereareiam.anvil.environment.provisioning.java.distribution.TemurinDistribution;
import me.whereareiam.anvil.environment.provisioning.java.installation.JavaPlatform;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoojayJavaCatalogTest {
	private static final JavaPlatform LINUX = JavaPlatform.of("Linux", "amd64", false);
	private static final String NOTHING_FOUND = "{\"result\":[],\"message\":\"No package(s) found\"}";
	private static final String FOUND = "{\"result\":[{\"id\":\"package-21\"}]}";
	private static final String CHECKSUM = "a".repeat(64);
	private static final String PACKAGE = "{\"result\":[{\"direct_download_uri\":\"https://example.test/jdk-21.tar.gz\","
			+ "\"checksum\":\"" + CHECKSUM + "\"}]}";

	@Test
	void acceptsOnlyASearchThatFoundAPackageAndADownloadablePackage() throws Exception {
		JavaPackageSource packages = source((uri, usable) -> {
			if (uri.getPath().endsWith("/packages")) {
				assertFalse(usable.test(NOTHING_FOUND), "An empty search must not be kept");
				assertFalse(usable.test("not json"));
				assertTrue(usable.test(FOUND));
				return FOUND;
			}

			assertEquals("/disco/v3.0/ids/package-21", uri.getPath());
			assertFalse(usable.test("{\"result\":[]}"));
			assertFalse(usable.test("{\"result\":[{\"direct_download_uri\":\"http://example.test/jdk.tar.gz\",\"checksum\":\"" + CHECKSUM + "\"}]}"));
			assertTrue(usable.test(PACKAGE));
			return PACKAGE;
		});

		FoojayJavaCatalog.Package selected = catalog(packages).select("temurin", 21, null, LINUX);

		assertEquals(URI.create("https://example.test/jdk-21.tar.gz"), selected.uri());
		assertEquals(CHECKSUM, selected.sha256());
	}

	/**
	 * Foojay files packages under the platform's own terms: macOS and Windows under no C library, Windows as zip
	 * archives, so asking every platform for a glibc tar.gz finds nothing on them.
	 */
	@Test
	void searchesForThePackagesOfThePlatform() throws Exception {
		Map<String, String> searches = new HashMap<>();
		for (JavaPlatform platform : List.of(LINUX, JavaPlatform.of("Linux", "aarch64", true),
				JavaPlatform.of("Mac OS X", "aarch64", false), JavaPlatform.of("Windows 11", "amd64", false))) {
			JavaPackageSource packages = source((uri, usable) -> {
				if (uri.getPath().endsWith("/packages")) {
					searches.put(platform.key(), uri.getQuery());
					return FOUND;
				}
				return PACKAGE;
			});
			catalog(packages).select("temurin", 21, null, platform);
		}

		assertTrue(searches.get("linux-x64").contains("operating_system=linux&architecture=x64&archive_type=tar.gz&libc_type=glibc"));
		assertTrue(searches.get("linux-aarch64-musl").contains("archive_type=tar.gz&libc_type=musl"));
		assertTrue(searches.get("mac-aarch64").contains("operating_system=mac&architecture=aarch64&archive_type=tar.gz&directly"));
		assertTrue(searches.get("windows-x64").contains("operating_system=windows&architecture=x64&archive_type=zip&directly"));
	}

	@Test
	void namesTheRequestedPackageWhenNoUsableSearchIsAvailable() {
		ProvisioningException unusable = new ProvisioningException("Resolution metadata has no usable answer");
		JavaPackageSource packages = source((uri, usable) -> {
			throw unusable;
		});

		ProvisioningException failure = assertThrows(ProvisioningException.class,
				() -> catalog(packages).select("temurin", 21, null, LINUX));

		assertEquals("Found no Java package for temurin 21 on Foojay", failure.getMessage());
		assertSame(unusable, failure.getCause());
	}

	private static FoojayJavaCatalog catalog(JavaPackageSource packages) {
		return new FoojayJavaCatalog(packages, Map.of("temurin", new TemurinDistribution()));
	}

	private static JavaPackageSource source(Catalog catalog) {
		return new JavaPackageSource() {
			@Override
			public @NotNull String catalog(@NotNull URI uri, @NotNull Predicate<String> usable) {
				return catalog.read(uri, usable);
			}

			@Override
			public @NotNull Path archive(@NotNull JavaArchive archive, @NotNull Path destination) {
				throw new AssertionError("Selecting a package must not download it");
			}
		};
	}

	private interface Catalog {
		String read(URI uri, Predicate<String> usable);
	}
}
