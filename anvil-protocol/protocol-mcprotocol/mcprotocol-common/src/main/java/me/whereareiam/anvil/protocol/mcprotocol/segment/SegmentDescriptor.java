package me.whereareiam.anvil.protocol.mcprotocol.segment;

import lombok.Value;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Selection properties of one segment JAR, written by the build to {@value #PROPERTIES}.
 *
 * <p>A segment adapts one owner, such as {@code mcprotocol-client} or {@code messages-mcprotocol}, to the
 * releases of one library from its start version on. For a release, the selected segment of each owner is the
 * one with the greatest start version that does not exceed the release key.</p>
 */
@Value
public class SegmentDescriptor {
	/**
	 * Resource holding a segment's selection properties.
	 */
	public static final String PROPERTIES = "META-INF/anvil/segment.properties";
	/**
	 * Resource listing every class, field and method a segment links against outside the JDK and the build.
	 */
	public static final String LINKAGE = "META-INF/anvil/segment/linkage.txt";

	/**
	 * Registered library id the segment adapts to.
	 */
	@NotNull String library;
	/**
	 * Library side folder that owns the segment; one segment per owner is selected.
	 */
	@NotNull String owner;
	/**
	 * Key version of the first release the segment serves.
	 */
	@NotNull MinecraftVersion since;

	/**
	 * Reads selection properties.
	 *
	 * @param input content of {@value #PROPERTIES}
	 * @param source location used in failure messages
	 * @return descriptor
	 * @throws IllegalArgumentException when a property is missing or {@code since} is not a release version
	 */
	public static @NotNull SegmentDescriptor read(@NotNull InputStream input, @NotNull String source) {
		Properties properties = new Properties();
		try {
			properties.load(input);
		} catch (IOException | IllegalArgumentException exception) {
			throw new IllegalArgumentException("Could not read segment properties of " + source, exception);
		}

		return new SegmentDescriptor(
				property(properties, "library", source),
				property(properties, "owner", source),
				MinecraftVersion.parse(property(properties, "since", source))
		);
	}

	private static String property(Properties properties, String name, String source) {
		String value = properties.getProperty(name, "").trim();
		if (value.isEmpty()) throw new IllegalArgumentException("Segment " + source + " does not declare '" + name + "'");

		return value;
	}
}
