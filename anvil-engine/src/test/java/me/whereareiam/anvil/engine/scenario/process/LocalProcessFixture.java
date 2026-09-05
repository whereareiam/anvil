package me.whereareiam.anvil.engine.scenario.process;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Scanner;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/**
 * Tiny local JVM used to test lifecycle behavior without Minecraft or downloads.
 */
public final class LocalProcessFixture {
	public static Path jar(Path directory) throws Exception {
		Path executable = directory.resolve("process.jar");
		Manifest manifest = new Manifest();
		manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
		manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, Main.class.getName());
		String resource = Main.class.getName().replace('.', '/') + ".class";
		try (var jar = new JarOutputStream(Files.newOutputStream(executable), manifest);
		     var input = LocalProcessFixture.class.getClassLoader().getResourceAsStream(resource)) {
			jar.putNextEntry(new JarEntry(resource));
			input.transferTo(jar);
			jar.closeEntry();
		}

		return executable;
	}

	public static final class Main {
		public static void main(String[] args) throws Exception {
			System.out.println("READY");
			try (Scanner input = new Scanner(System.in)) {
				while (input.hasNextLine()) {
					if (!input.nextLine().equals("stop")) continue;
					if (args.length == 2)
						Files.writeString(Path.of(args[0]), args[1] + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
					return;
				}
			}
		}
	}
}
