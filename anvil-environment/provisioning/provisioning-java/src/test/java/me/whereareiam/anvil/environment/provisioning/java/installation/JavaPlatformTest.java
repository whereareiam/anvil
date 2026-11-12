package me.whereareiam.anvil.environment.provisioning.java.installation;

import me.whereareiam.anvil.api.exception.ProvisioningException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JavaPlatformTest {
	@Test
	void describesEachOperatingSystemInTheTermsOfJavaCatalogs() {
		assertEquals(new JavaPlatform("linux", "x64", "glibc", "tar.gz"), JavaPlatform.of("Linux", "amd64", false));
		assertEquals(new JavaPlatform("linux", "aarch64", "musl", "tar.gz"), JavaPlatform.of("Linux", "aarch64", true));
		assertEquals(new JavaPlatform("mac", "aarch64", null, "tar.gz"), JavaPlatform.of("Mac OS X", "aarch64", false));
		assertEquals(new JavaPlatform("mac", "x64", null, "tar.gz"), JavaPlatform.of("Darwin", "x86_64", false));
		assertEquals(new JavaPlatform("windows", "x64", null, "zip"), JavaPlatform.of("Windows 11", "amd64", false));
	}

	@Test
	void keepsTheInstallationDirectoriesOfGlibcAndNamesMuslApart() {
		assertEquals("linux-x64", JavaPlatform.of("Linux", "amd64", false).key());
		assertEquals("linux-x64-musl", JavaPlatform.of("Linux", "amd64", true).key());
		assertEquals("mac-aarch64", JavaPlatform.of("Mac OS X", "arm64", false).key());
	}

	@Test
	void refusesWhatNoCatalogServes() {
		assertThrows(ProvisioningException.class, () -> JavaPlatform.of("FreeBSD", "amd64", false));
		assertThrows(ProvisioningException.class, () -> JavaPlatform.of("Linux", "riscv64", false));
	}
}
