package me.whereareiam.anvil.protocol.mcprotocol.worker.host;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkerClasspathResolverTest {
	@TempDir
	Path temporary;

	@Test
	void includesContextLoaderDependenciesAndReplacesTheProtocolRuntime() throws Exception {
		Path selectedProtocol = temporary.resolve("protocol-1.21.11-selected.jar").toAbsolutePath();
		Path staleProtocol = jar("renamed-client.jar", "org/geysermc/mcprotocollib/protocol/codec/MinecraftCodec.class");
		Path capability = jar("protocol-99-external-capability.jar", "example/Capability.class");

		try (URLClassLoader loader = new URLClassLoader(new URL[] {
				capability.toUri().toURL(),
				staleProtocol.toUri().toURL()
		}, null)) {
			List<String> classpath = Arrays.asList(new WorkerClasspathResolver()
					.resolve(List.of(selectedProtocol), loader)
					.split(Pattern.quote(System.getProperty("path.separator"))));

			assertEquals(selectedProtocol.toString(), classpath.getFirst());
			assertTrue(classpath.contains(capability.toString()));
			assertFalse(classpath.contains(staleProtocol.toString()));
		}
	}

	private Path jar(String filename, String resource) throws Exception {
		Path path = temporary.resolve(filename);
		try (var jar = new JarOutputStream(Files.newOutputStream(path))) {
			jar.putNextEntry(new JarEntry(resource));
			jar.closeEntry();
		}
		return path;
	}
}
