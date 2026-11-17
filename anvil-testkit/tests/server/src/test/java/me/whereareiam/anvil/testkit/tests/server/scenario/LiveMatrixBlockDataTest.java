package me.whereareiam.anvil.testkit.tests.server.scenario;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.protocol.api.model.ProtocolRelease;
import me.whereareiam.anvil.protocol.java.block.JavaBlockData;
import me.whereareiam.anvil.protocol.java.block.exception.BlockDataUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Checks that Anvil ships block data for the protocol of every release of the live matrix, which client physics needs
 * to collide with the world: the physics suite runs every course on each release's Paper server, so a release without
 * block data would leave its suite unable to run. The test starts no server.
 */
class LiveMatrixBlockDataTest {
	@TempDir
	Path directory;

	@Test
	void everyReleaseOfTheLiveMatrixHasBlockData() {
		List<ProtocolRelease> releases = McProtocolReleases.read(directory);
		JavaBlockData blockData = JavaBlockData.shipped();

		Map<MinecraftVersion, String> missing = new TreeMap<>();
		for (MinecraftVersion version : CompatibilityScenarioFactory.releases()) {
			int protocol = releases.stream()
					.filter(release -> release.getMinecraftVersions().contains(version))
					.findFirst()
					.orElseThrow(() -> new AssertionError("No MCProtocolLib release speaks " + version))
					.getProtocolNumber();
			try {
				blockData.require(protocol);
			} catch (BlockDataUnavailableException unavailable) {
				missing.put(version, unavailable.getMessage());
			}
		}

		assertEquals(Map.of(), missing, "Releases of the live matrix without block data");
	}
}
