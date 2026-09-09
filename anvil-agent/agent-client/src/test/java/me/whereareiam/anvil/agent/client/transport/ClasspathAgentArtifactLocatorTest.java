package me.whereareiam.anvil.agent.client.transport;

import me.whereareiam.anvil.agent.api.exception.AgentException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ClasspathAgentArtifactLocatorTest {
	@TempDir
	Path temporary;

	@Test
	void usesTheExactClasspathJar() throws Exception {
		Path jar = temporary.resolve("selected.jar");
		try (var output = new JarOutputStream(Files.newOutputStream(jar))) {
			output.putNextEntry(new JarEntry("example/Agent.class"));
			output.write(new byte[] {0});
			output.closeEntry();
		}
		ClassLoader previous = Thread.currentThread().getContextClassLoader();
		try (var loader = new URLClassLoader(new URL[] {jar.toUri().toURL()}, null)) {
			Thread.currentThread().setContextClassLoader(loader);
			assertEquals(jar, new ClasspathAgentArtifactLocator().locate("example.Agent"));
		} finally {
			Thread.currentThread().setContextClassLoader(previous);
		}
	}

	@Test
	void refusesToGuessAnArtifactFromCompiledClasses() throws Exception {
		Path classes = temporary.resolve("build/classes/java/main");
		Files.createDirectories(classes.resolve("example"));
		Files.createFile(classes.resolve("example/Agent.class"));
		Path libraries = Files.createDirectories(temporary.resolve("build/libs"));
		Files.createFile(libraries.resolve("unrelated-999.jar"));
		ClassLoader previous = Thread.currentThread().getContextClassLoader();
		try (var loader = new URLClassLoader(new URL[] {classes.toUri().toURL()}, null)) {
			Thread.currentThread().setContextClassLoader(loader);
			AgentException failure = assertThrows(AgentException.class,
					() -> new ClasspathAgentArtifactLocator().locate("example.Agent"));
			assertTrue(failure.getMessage().contains("packaged JAR"));
		} finally {
			Thread.currentThread().setContextClassLoader(previous);
		}
	}
}
