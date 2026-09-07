package me.whereareiam.anvil.api.model.java;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.net.URI;

/** Supplies a checksum-verified JDK archive from a user-controlled location. */
@Value
@Builder
public class JavaArchive implements JavaSource {
	@NotNull URI uri;
	@NotNull String sha256;
}
