package me.whereareiam.anvil.protocol.api.model;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.type.SupportLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolReleaseTest {
	private static final ProtocolRelease RELEASE = ProtocolRelease.builder()
			.libraryVersion("1.16.5-2")
			.minecraftVersion(version("1.16.4"))
			.minecraftVersion(version("1.16.5"))
			.verifiedVersion(version("1.16.5"))
			.protocolNumber(754)
			.javaVersion(8)
			.build();

	@Test
	void assessesListedVersionsExactlyAndNeverByFloor() {
		assertEquals(SupportLevel.VERIFIED, RELEASE.support(version("1.16.5")));
		assertEquals(SupportLevel.COMPATIBLE, RELEASE.support(version("1.16.4")));
		assertEquals(SupportLevel.UNSUPPORTED, RELEASE.support(version("1.16.3")));
		assertEquals(SupportLevel.UNSUPPORTED, RELEASE.support(version("1.17")));
	}

	@Test
	void userSuppliedReleasesAreUntestedForEveryListedVersion() {
		ProtocolRelease additional = RELEASE.toBuilder().additional(true).build();

		assertEquals(SupportLevel.UNTESTED, additional.support(version("1.16.5")));
		assertEquals(SupportLevel.UNTESTED, additional.support(version("1.16.4")));
		assertEquals(SupportLevel.UNSUPPORTED, additional.support(version("1.17.1")));
	}

	@Test
	void keysTheReleaseByItsHighestVersion() {
		ProtocolRelease release = RELEASE.toBuilder()
				.clearMinecraftVersions()
				.minecraftVersion(version("26.1.2"))
				.minecraftVersion(version("26.1"))
				.minecraftVersion(version("26.1.1"))
				.clearVerifiedVersions()
				.build();

		assertEquals(version("26.1.2"), release.version());
	}

	@Test
	void rejectsReleasesWithoutVersionsOrWithUnlistedVerifiedVersions() {
		assertThrows(IllegalArgumentException.class, () -> RELEASE.toBuilder().clearMinecraftVersions().clearVerifiedVersions().build());
		assertThrows(IllegalArgumentException.class, () -> RELEASE.toBuilder().verifiedVersion(version("1.17.1")).build());
		assertThrows(IllegalArgumentException.class, () -> RELEASE.toBuilder().libraryVersion(" ").build());
	}

	@Test
	void rejectsRepeatedVersionsAndNonPositiveProtocolOrJavaVersions() {
		var repeated = assertThrows(IllegalArgumentException.class,
				() -> RELEASE.toBuilder().minecraftVersion(version("1.16.5")).build());
		assertTrue(repeated.getMessage().contains("lists a Minecraft version twice"), repeated.getMessage());
		assertThrows(IllegalArgumentException.class, () -> RELEASE.toBuilder().protocolNumber(0).build());
		assertThrows(IllegalArgumentException.class, () -> RELEASE.toBuilder().protocolNumber(-754).build());
		assertThrows(IllegalArgumentException.class, () -> RELEASE.toBuilder().javaVersion(0).build());
	}

	private static MinecraftVersion version(String text) {
		return MinecraftVersion.parse(text);
	}
}
