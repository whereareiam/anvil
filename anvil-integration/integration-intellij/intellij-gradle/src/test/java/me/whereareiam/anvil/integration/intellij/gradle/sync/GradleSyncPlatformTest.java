package me.whereareiam.anvil.integration.intellij.gradle.sync;

import com.intellij.build.events.MessageEvent;
import com.intellij.build.events.impl.MessageEventImpl;
import com.intellij.openapi.externalSystem.importing.ImportSpec;
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemBuildEvent;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemProcessingManager;
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemResolveProjectTask;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener;
import com.intellij.openapi.externalSystem.service.remote.RemoteExternalSystemProgressNotificationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

import me.whereareiam.anvil.integration.intellij.exception.ProjectSyncException;
import me.whereareiam.anvil.integration.intellij.gradle.GradleBuildIntegration;
import org.jetbrains.plugins.gradle.util.GradleConstants;

public class GradleSyncPlatformTest extends BasePlatformTestCase {
	public void testAlreadyRunningNativeResolveIsObservedUntilDataImportFinishes() throws Exception {
		var task = activeNativeTask();
		var future =
				GradleSync.request(
						getProject(),
						"/workspace/example",
						(path, specification) -> fail("Anvil must attach to the native sync already running"));
		var events =
				(RemoteExternalSystemProgressNotificationManager)
						ExternalSystemProgressNotificationManager.getInstance();
		events.onSuccess("/workspace/example", task.getId());
		assertFalse("Resolve completion is earlier than IDE data import", future.isDone());
		getProject()
				.getMessageBus()
				.syncPublisher(ProjectDataImportListener.TOPIC)
				.onImportFinished("/workspace/other");
		assertFalse(future.isDone());
		getProject()
				.getMessageBus()
				.syncPublisher(ProjectDataImportListener.TOPIC)
				.onImportFinished("/workspace/example");
		assertTrue(future.isDone());
		assertFalse(future.isCompletedExceptionally());
	}

	public void testCapturedImportSurvivesResolverRemovalBeforeIdeImport() throws Exception {
		var task = activeNativeTask();
		var observed = GradleSync.observe(getProject(), "/workspace/example");
		assertNotNull(observed);
		ExternalSystemProcessingManager.getInstance().release(task.getId());
		assertFalse(observed.isDone());
		getProject()
				.getMessageBus()
				.syncPublisher(ProjectDataImportListener.TOPIC)
				.onImportFinished("/workspace/example");
		assertTrue(observed.isDone());
		assertFalse(observed.isCompletedExceptionally());
	}

	public void testObservationWithoutNativeSyncDoesNotRequestImport() {
		assertNull(GradleSync.observe(getProject(), "/workspace/example"));
	}

	public void testObservedNativeFailureIsReportedWithoutStartingAnotherImport() throws Exception {
		var task = activeNativeTask();
		var future =
				GradleSync.request(
						getProject(),
						"/workspace/example",
						(path, specification) -> fail("Unexpected duplicate import"));
		var events =
				(RemoteExternalSystemProgressNotificationManager)
						ExternalSystemProgressNotificationManager.getInstance();
		events.onStatusChange(
				new ExternalSystemBuildEvent(
						task.getId(),
						new MessageEventImpl(
								task.getId(),
								MessageEvent.Kind.ERROR,
								"Project configuration",
								"Cannot configure Anvil",
								"The declared scenario source set is missing.")));
		events.onFailure(
				"/workspace/example", task.getId(), new IllegalStateException("Native resolve failed"));
		assertTrue(future.isCompletedExceptionally());
		try {
			future.join();
			fail("The observed failure must reach the caller");
		} catch (CompletionException failure) {
			var detailed = (ProjectSyncException) failure.getCause();
			assertEquals("Cannot configure Anvil", detailed.getMessage());
			assertTrue(detailed.getDetails().contains("The declared scenario source set is missing."));
		}
	}

	public void testObservedNativeCancellationTerminatesWithoutImportEvent() throws Exception {
		var task = activeNativeTask();
		var future =
				GradleSync.request(
						getProject(),
						"/workspace/example",
						(path, specification) -> fail("Unexpected duplicate import"));
		var events =
				(RemoteExternalSystemProgressNotificationManager)
						ExternalSystemProgressNotificationManager.getInstance();
		events.onCancel("/workspace/example", task.getId());
		assertTrue(future.isCompletedExceptionally());
	}

	public void testNativeImportRemainsEnabledAndFutureWaitsForImportCallback() {
		AtomicReference<ImportSpec> requested = new AtomicReference<>();
		var future =
				GradleSync.request(
						getProject(),
						"/workspace/example",
						(path, specification) -> {
							assertEquals("/workspace/example", path);
							requested.set(specification);
						});

		assertTrue(
				"Native project data import must remain enabled",
				requested.get().shouldImportProjectData());
		assertEquals(
				ProgressExecutionMode.IN_BACKGROUND_ASYNC, requested.get().getProgressExecutionMode());
		assertEquals(GradleConstants.SYSTEM_ID, requested.get().getExternalSystemId());
		assertFalse(future.isDone());
		requested.get().getCallback().onSuccess(null);
		assertTrue(future.isDone());
		assertFalse(future.isCompletedExceptionally());
	}

	public void testFailedNativeImportCompletesExceptionally() {
		AtomicReference<ImportSpec> requested = new AtomicReference<>();
		var future =
				GradleSync.request(
						getProject(),
						"/workspace/example",
						(path, specification) -> requested.set(specification));
		requested.get().getCallback().onFailure("Project sync failed", null);

		assertTrue(future.isCompletedExceptionally());
		try {
			future.join();
			fail("Import failure must reach the caller");
		} catch (CompletionException failure) {
			assertEquals("Project sync failed", failure.getCause().getMessage());
		}
	}

	public void testNativeCancellationTerminatesPendingFutureWithoutImportCallback()
			throws Exception {
		var future =
				GradleSync.request(getProject(), "/workspace/example", (path, specification) -> {});
		var events =
				(RemoteExternalSystemProgressNotificationManager)
						ExternalSystemProgressNotificationManager.getInstance();
		var task =
				ExternalSystemTaskId.create(
						GradleConstants.SYSTEM_ID, ExternalSystemTaskType.RESOLVE_PROJECT, getProject());
		events.onCancel("/workspace/other", task);
		assertFalse(future.isDone());
		events.onCancel("/workspace/example", task);
		assertTrue(future.isCompletedExceptionally());
	}

	public void testImportDataFailureTerminatesPendingFutureWithoutRefreshCallback() {
		var future =
				GradleSync.request(getProject(), "/workspace/example", (path, specification) -> {});
		getProject()
				.getMessageBus()
				.syncPublisher(ProjectDataImportListener.TOPIC)
				.onImportFailed(null, new IllegalStateException("Unrelated import failed"));
		assertFalse(future.isDone());
		getProject()
				.getMessageBus()
				.syncPublisher(ProjectDataImportListener.TOPIC)
				.onImportFailed("/workspace/example", new IllegalStateException("Model import failed"));
		assertTrue(future.isCompletedExceptionally());
	}

	public void testProviderCannotSyncAnUnlinkedProject() {
		var provider = new GradleBuildIntegration();
		assertFalse(provider.canSync(getProject()));
		assertTrue(provider.sync(getProject()).isCompletedExceptionally());
	}

	public void testNativeBuildErrorDetailsReachCallerInsteadOfEmptyMarkerException()
			throws Exception {
		AtomicReference<ImportSpec> requested = new AtomicReference<>();
		var future =
				GradleSync.request(
						getProject(),
						"/workspace/example",
						(path, specification) -> requested.set(specification));
		var events =
				(RemoteExternalSystemProgressNotificationManager)
						ExternalSystemProgressNotificationManager.getInstance();
		var task =
				ExternalSystemTaskId.create(
						GradleConstants.SYSTEM_ID, ExternalSystemTaskType.RESOLVE_PROJECT, getProject());
		events.onStart("/workspace/example", task);
		events.onStatusChange(
				new ExternalSystemBuildEvent(
						task,
						new MessageEventImpl(
								task,
								MessageEvent.Kind.WARNING,
								"Resource configuration",
								"Some resources use custom filters",
								"Unsupported CopySpec filter")));
		events.onStatusChange(
				new ExternalSystemBuildEvent(
						task,
						new MessageEventImpl(
								task,
								MessageEvent.Kind.ERROR,
								"Anvil model",
								"Could not import the Anvil project declaration",
								"The declared source set does not exist.")));
		assertFalse(
				"Warnings and errors are diagnostic events; final sync outcome is still pending",
				future.isDone());
		requested
				.get()
				.getCallback()
				.onFailure("com.intellij.SomePartialResolutionException: ", "Native import details");

		try {
			future.join();
			fail("A failed partial import must not be accepted");
		} catch (CompletionException failure) {
			var detailed = (ProjectSyncException) failure.getCause();
			assertEquals("Could not import the Anvil project declaration", detailed.getMessage());
			assertTrue(detailed.getDetails().contains("The declared source set does not exist."));
			assertTrue(detailed.getDetails().contains("Native import details"));
			assertTrue(detailed.getDetails().contains("Unsupported CopySpec filter"));
		}
	}

	public void testResourceWarningDoesNotConvertSuccessfulSyncIntoFailure() throws Exception {
		AtomicReference<ImportSpec> requested = new AtomicReference<>();
		var future =
				GradleSync.request(
						getProject(),
						"/workspace/example",
						(path, specification) -> requested.set(specification));
		var events =
				(RemoteExternalSystemProgressNotificationManager)
						ExternalSystemProgressNotificationManager.getInstance();
		var task =
				ExternalSystemTaskId.create(
						GradleConstants.SYSTEM_ID, ExternalSystemTaskType.RESOLVE_PROJECT, getProject());
		events.onStart("/workspace/example", task);
		events.onStatusChange(
				new ExternalSystemBuildEvent(
						task,
						new MessageEventImpl(
								task,
								MessageEvent.Kind.WARNING,
								"Resource configuration",
								"Custom resource filter",
								"The IDE cannot represent this filter.")));
		assertFalse(future.isDone());
		requested.get().getCallback().onSuccess(null);
		assertFalse(future.isCompletedExceptionally());
	}

	private ExternalSystemResolveProjectTask activeNativeTask() {
		var specification = new ImportSpecBuilder(getProject(), GradleConstants.SYSTEM_ID).build();
		var task =
				new ExternalSystemResolveProjectTask(getProject(), "/workspace/example", specification);
		var manager = ExternalSystemProcessingManager.getInstance();
		manager.add(task);
		Disposer.register(getTestRootDisposable(), () -> manager.release(task.getId()));
		return task;
	}
}
