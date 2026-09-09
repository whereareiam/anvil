package me.whereareiam.anvil.environment.execution.local;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.anvil.api.model.java.JavaArchive;
import me.whereareiam.anvil.api.model.java.JavaRequirement;
import me.whereareiam.anvil.api.model.java.JavaSource;
import me.whereareiam.anvil.api.model.java.local.LocalJavaHome;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Map;

/** Local-execution sources for explicit Java requirements. */
@Value
@Builder
public class LocalExecutionSettings {
	@NotNull @Singular("javaHome") Map<String, Path> javaHomes;
	@NotNull @Singular("javaArchive") Map<String, JavaArchive> javaArchives;

	JavaSource source(@NotNull JavaRequirement requirement) {
		String key = key(requirement);
		Path home = javaHomes.get(key);

		if (home != null) return new LocalJavaHome(home);
		return javaArchives.get(key);
	}

	private String key(JavaRequirement requirement) {
		return (requirement.getDistribution() == null ? "default" : requirement.getDistribution())
				+ ":" + (requirement.getFeatureVersion() == null ? "minimum" : requirement.getFeatureVersion());
	}
}
