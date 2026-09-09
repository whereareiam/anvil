package me.whereareiam.anvil.testkit.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestExtensionLoaderTest {
	private static final String PROTOCOL_SERVICE = "META-INF/services/me.whereareiam.anvil.protocol.api.provider.ProtocolProvider";
	private static final String AGENT_SERVICE = "META-INF/services/me.whereareiam.anvil.agent.server.api.operation.AgentOperationProvider";

	@TempDir
	Path directory;

	@Test
	void loadsExternalClassesWhileSharingHostClassesAndRestoresTheContextLoader() throws Exception {
		ClassLoader previous = Thread.currentThread().getContextClassLoader();
		assertThrows(ClassNotFoundException.class, () -> previous.loadClass("external.fixture.protocol.FixtureConnection"));

		try (var extension = new TestExtensionLoader(FixtureArtifacts.extension(), false)) {
			assertSame(TestExtensionLoader.class, extension.load(TestExtensionLoader.class.getName()));
			Class<?> connection = extension.load("external.fixture.protocol.FixtureConnection");
			assertSame(Thread.currentThread().getContextClassLoader(), connection.getClassLoader());
			assertEquals(FixtureArtifacts.extension(), extension.artifact());
		}

		assertSame(previous, Thread.currentThread().getContextClassLoader());
	}

	@Test
	void restoresTheContextLoaderWhenExtensionUseFails() {
		ClassLoader previous = Thread.currentThread().getContextClassLoader();
		IOException failure = new IOException("fixture use failed");
		var thrown = assertThrows(IOException.class, () -> {
			try (var ignored = new TestExtensionLoader(FixtureArtifacts.extension(), false)) {
				throw failure;
			}
		});

		assertSame(failure, thrown);
		assertSame(previous, Thread.currentThread().getContextClassLoader());
	}

	@Test
	void filtersOnlyInstalledProtocolDescriptors() throws Exception {
		ClassLoader previous = Thread.currentThread().getContextClassLoader();
		URL installed = Files.writeString(directory.resolve("installed-provider"), "installed.Provider\n").toUri().toURL();
		ClassLoader parent = new ClassLoader(previous) {
			@Override
			public Enumeration<URL> getResources(String name) throws IOException {
				if (name.equals(PROTOCOL_SERVICE) || name.equals(AGENT_SERVICE))
					return Collections.enumeration(List.of(installed));

				return super.getResources(name);
			}
		};
		Thread.currentThread().setContextClassLoader(parent);
		try {
			try (var ignored = new TestExtensionLoader(FixtureArtifacts.extension(), false)) {
				assertEquals(List.of("external.fixture.protocol.FixtureProtocolProvider"), descriptors(PROTOCOL_SERVICE));
				assertEquals(List.of("installed.Provider", "external.fixture.agent.FixtureAgentOperations"), descriptors(AGENT_SERVICE));
			}
			assertSame(parent, Thread.currentThread().getContextClassLoader());

			try (var ignored = new TestExtensionLoader(FixtureArtifacts.extension(), true)) {
				assertEquals(List.of("installed.Provider", "external.fixture.protocol.FixtureProtocolProvider"), descriptors(PROTOCOL_SERVICE));
			}
		} finally {
			Thread.currentThread().setContextClassLoader(previous);
		}
	}

	@Test
	void selectsPreparedProviderVariantsWithoutRebuildingTheJar() throws Exception {
		try (var ignored = new TestExtensionLoader(FixtureArtifacts.brokenExtension(), false)) {
			assertEquals(List.of("external.fixture.protocol.BrokenProtocolProvider"), descriptors(PROTOCOL_SERVICE));
		}
		try (var ignored = new TestExtensionLoader(FixtureArtifacts.observationExtension(), false)) {
			assertEquals(List.of("external.fixture.protocol.ObservationProtocolProvider"), descriptors(PROTOCOL_SERVICE));
		}
	}

	private List<String> descriptors(String name) throws IOException {
		var values = new ArrayList<String>();
		var resources = Thread.currentThread().getContextClassLoader().getResources(name);
		while (resources.hasMoreElements())
			try (var input = resources.nextElement().openStream()) {
				values.add(new String(input.readAllBytes(), StandardCharsets.UTF_8).strip());
			}

		return values;
	}
}
