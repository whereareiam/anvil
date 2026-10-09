package me.whereareiam.anvil.tooling.gradle.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * Internal JSON model written by the Gradle preparation task.
 */
@Value
@Builder
public class ToolingLaunchModel {
	public static final int SCHEMA_VERSION = 1;

	int schemaVersion;
	String toolingJavaExecutable;
	@Singular("classpathEntry")
	List<String> classpath;
	@Singular("definition")
	List<String> definitions;
	@Singular("property")
	Map<String, String> properties;
	@Singular
	Map<String, String> artifacts;
}
