package me.whereareiam.anvil.integration.intellij.gradle.sync;

import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.ArrayList;
import java.util.List;

import me.whereareiam.anvil.integration.intellij.gradle.GradleBuildIntegration;
import me.whereareiam.anvil.integration.intellij.type.source.ProjectChange;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

public class ModuleImportListenerPlatformTest extends BasePlatformTestCase {
	public void testOnlyLinkedImportOutcomesReachLiveSubscribers() {
		var owner = Disposer.newDisposable();
		Disposer.register(getTestRootDisposable(), owner);
		List<ProjectChange> changes = new ArrayList<>();
		new GradleBuildIntegration().subscribe(getProject(), changes::add, owner);
		var settings = GradleSettings.getInstance(getProject());
		var previous = List.copyOf(settings.getLinkedProjectsSettings());
		try {
			settings.setLinkedProjectsSettings(List.of(new GradleProjectSettings("/workspace/example")));
			var events = getProject().getMessageBus().syncPublisher(ProjectDataImportListener.TOPIC);
			events.onImportFinished(null);
			events.onImportFinished("/workspace/other");
			events.onImportFailed("/workspace/other", new IllegalStateException("Ignored"));
			assertTrue(changes.isEmpty());
			events.onImportFinished("/workspace/example");
			events.onImportFailed("/workspace/example", new IllegalStateException("Failed sync"));
			assertEquals(List.of(ProjectChange.IMPORTED, ProjectChange.IMPORT_FAILED), changes);
			Disposer.dispose(owner);
			events.onImportFinished("/workspace/example");
			assertEquals(2, changes.size());
		} finally {
			settings.setLinkedProjectsSettings(previous);
		}
	}
}
