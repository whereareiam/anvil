package me.whereareiam.anvil.integration.intellij.gradle;

import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService;

import me.whereareiam.anvil.integration.intellij.gradle.model.ImportedModule;
import org.jetbrains.annotations.NotNull;

/**
 * Registers imported module values with IntelliJ's external-project model infrastructure.
 */
public final class ModuleDataRegistration extends AbstractProjectDataService<ImportedModule, Void> {
	/**
	 * Native model key shared by import contributors and module resolution.
	 */
	public static final Key<ImportedModule> KEY = Key.create(
			ImportedModule.class,
			ProjectKeys.MODULE.getProcessingWeight() + 1
	);

	@Override
	public @NotNull Key<ImportedModule> getTargetDataKey() {
		return KEY;
	}
}
