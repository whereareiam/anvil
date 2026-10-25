package me.whereareiam.anvil.workbench.protocol.model.java;

import lombok.Value;
import me.whereareiam.anvil.environment.provisioning.java.api.model.JavaInstallation;
import org.jetbrains.annotations.Nullable;

/**
 * An inspected Java installation.
 */
@Value
public class InspectedJava {
	/**
	 * The installation, or null when the source names none.
	 */
	@Nullable JavaInstallation installation;
}
