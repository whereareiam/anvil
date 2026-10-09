package me.whereareiam.anvil.integration.intellij.source;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.util.ui.UIUtil;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import me.whereareiam.anvil.integration.intellij.EnginePlatformTestCase;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioPreparation;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.model.source.SourceListing;
import me.whereareiam.anvil.integration.intellij.type.source.ProjectChange;
import me.whereareiam.anvil.integration.intellij.type.source.SourceListingStatus;
import org.jetbrains.annotations.NotNull;

public class BuildSyncPlatformTest extends EnginePlatformTestCase {
	public void testSyncIsCoalescedAndMakesNewModelsDiscoverable() {
		var provider = new SyncProvider("test");
		ExtensionTestUtil.maskExtensions(
				ProjectBuildIntegrations.BUILD_INTEGRATIONS, List.of(provider), getTestRootDisposable());
		var service = getProject().getService(BuildIntegrations.class);
		assertTrue(service.canSync());
		assertTrue(service.discover().getSources().isEmpty());
		var first = service.sync();
		assertSame(first, service.sync());
		assertEquals(1, provider.requests.get());
		assertFalse(first.isDone());
		provider.pending.complete(null);

		assertTrue(first.isDone());
		assertEquals("test:module", service.resolve("").getId());
	}

	public void testAllLinkedProvidersFinishBeforeOverallCompletionAndFailureIsPreserved() {
		var first = new SyncProvider("first");
		var second = new SyncProvider("second");
		ExtensionTestUtil.maskExtensions(
				ProjectBuildIntegrations.BUILD_INTEGRATIONS, List.of(first, second), getTestRootDisposable());
		var future = getProject().getService(BuildIntegrations.class).sync();
		first.pending.complete(null);
		assertFalse(future.isDone());
		second.pending.completeExceptionally(new IllegalStateException("Sync failed"));
		assertTrue(future.isCompletedExceptionally());
	}

	public void testSubscriptionsStopAtOwnerDisposal() {
		var provider = new SyncProvider("test");
		ExtensionTestUtil.maskExtensions(
				ProjectBuildIntegrations.BUILD_INTEGRATIONS, List.of(provider), getTestRootDisposable());
		var owner = Disposer.newDisposable();
		Disposer.register(getTestRootDisposable(), owner);
		AtomicInteger updates = new AtomicInteger();
		getProject()
				.getService(BuildIntegrations.class)
				.subscribe(
						change -> {
							assertTrue(ApplicationManager.getApplication().isDispatchThread());
							updates.incrementAndGet();
						},
						owner);
		var listener = provider.listener;
		listener.accept(ProjectChange.IMPORTED);
		UIUtil.dispatchAllInvocationEvents();
		assertEquals(1, updates.get());
		Disposer.dispose(owner);
		listener.accept(ProjectChange.IMPORTED);
		UIUtil.dispatchAllInvocationEvents();
		assertEquals(1, updates.get());
	}

	private static final class SyncProvider implements BuildIntegration {
		private final String id;
		private final CompletableFuture<Void> pending = new CompletableFuture<>();
		private final AtomicInteger requests = new AtomicInteger();
		private Consumer<ProjectChange> listener;

		private SyncProvider(String id) {
			this.id = id;
		}

		@Override
		public @NotNull String getId() {
			return id;
		}

		@Override
		public @NotNull SourceListing discover(@NotNull Project project) {
			List<ScenarioSource> projects =
					pending.isDone() && !pending.isCompletedExceptionally()
							? List.of(
									ScenarioSource.builder()
											.id(id + ":module")
											.displayName("Module")
											.integrationId(id)
											.directory(Path.of("/project"))
											.build())
							: List.of();
			return SourceListing.builder()
					.sources(projects)
					.status(
							projects.isEmpty()
									? SourceListingStatus.NOT_IMPORTED
									: SourceListingStatus.READY)
					.message(projects.isEmpty() ? "Sync this project" : "Ready")
					.build();
		}

		@Override
		public boolean canSync(@NotNull Project project) {
			return true;
		}

		@Override
		public @NotNull CompletableFuture<Void> sync(@NotNull Project project) {
			requests.incrementAndGet();
			return pending;
		}

		@Override
		public void subscribe(
				@NotNull Project project,
				@NotNull Consumer<ProjectChange> listener,
				@NotNull Disposable owner) {
			this.listener = listener;
		}

		@Override
		public @NotNull ScenarioPreparation prepare(
				@NotNull Project project, @NotNull ScenarioSource selected) {
			throw new AssertionError("Sync must not prepare an environment");
		}
	}
}
