package me.whereareiam.anvil.player.library.mcprotocol.segment.linkage;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassReader;

/**
 * Opens class files for the linkage checker and collector, turning ASM's refusal of a class file newer than it
 * supports into a failure that names the class and how to fix it.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ClassFiles {
	private static final int MAGIC = 0xCAFEBABE;
	private static final int JAVA_OFFSET = 44;

	/**
	 * Reads the bytes of the class file found at a location.
	 *
	 * @param bytes the class file
	 * @param origin where it was found, for the failure message
	 * @return a reader of the class file
	 * @throws IllegalArgumentException when the bytes are no class file or ASM cannot read the class file's version
	 */
	static @NotNull ClassReader reader(byte @NotNull [] bytes, @NotNull String origin) {
		try {
			return new ClassReader(bytes);
		} catch (IllegalArgumentException exception) {
			if (bytes.length < 8 || readInt(bytes) != MAGIC) throw new IllegalArgumentException(origin + " is not a class file", exception);

			int major = ((bytes[6] & 0xFF) << 8) | (bytes[7] & 0xFF);
			throw new IllegalArgumentException(origin + " has class file version " + major + " (Java " + (major - JAVA_OFFSET)
					+ "), which the ASM library of the segment linkage check cannot read; update 'asm' in gradle/libs.versions.toml",
					exception);
		}
	}

	private static int readInt(byte[] bytes) {
		return ((bytes[0] & 0xFF) << 24) | ((bytes[1] & 0xFF) << 16) | ((bytes[2] & 0xFF) << 8) | (bytes[3] & 0xFF);
	}
}
