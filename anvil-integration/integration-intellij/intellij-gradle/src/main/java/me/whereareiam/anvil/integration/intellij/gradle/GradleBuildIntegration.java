package me.whereareiam.anvil.integration.intellij.gradle;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import me.whereareiam.anvil.integration.intellij.gradle.resolver.ScenarioPreparationResolver;
import me.whereareiam.anvil.integration.intellij.gradle.resolver.ScenarioSourceResolver;
import me.whereareiam.anvil.integration.intellij.gradle.sync.GradleSync;
import me.whereareiam.anvil.integration.intellij.gradle.sync.ModuleImportListener;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioPreparation;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.model.source.SourceListing;
import me.whereareiam.anvil.integration.intellij.source.BuildIntegration;
import me.whereareiam.anvil.integration.intellij.type.source.ProjectChange;
import me.whereareiam.anvil.integration.intellij.type.source.SourceListingStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.GradleInstallationManager;
import org.jetbrains.plugins.gradle.settings.DistributionType;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jetbrains.plugins.gradle.util.GradleConstants;

/**
 * Connects Anvil discovery and preparation to the IDE's native Gradle integration.
 */
public final class GradleBuildIntegration implements BuildIntegration {
	static final String ID = "gradle";

	private final ScenarioSourceResolver sources = new ScenarioSourceResolver(ID);
	private final ScenarioPreparationResolver preparation = new ScenarioPreparationResolver();

	@Override
	public @NotNull String getId() {
		return ID;
	}

	@Override
	public boolean canSync(@NotNull Project project) {
		return !GradleSettings.getInstance(project).getLinkedProjectsSettings().isEmpty();
	}

	@Override
	public @NotNull CompletableFuture<Void> sync(@NotNull Project project) {
		var linked = GradleSettings.getInstance(project).getLinkedProjectsSettings();
		if (linked.isEmpty())
			return CompletableFuture.failedFuture(
					new IllegalStateException("Import this project in the IDE before syncing Anvil."));

		List<CompletableFuture<Void>> operations = new ArrayList<>();
		for (var settings : linked)
			operations.add(GradleSync.request(project, settings.getExternalProjectPath()));

		return CompletableFuture.allOf(operations.toArray(CompletableFuture[]::new));
	}

	@Override
	public void subscribe(
			@NotNull Project project,
			@NotNull Consumer<ProjectChange> listener,
			@NotNull Disposable owner
	) {
		var connection = project.getMessageBus().connect(owner);
		var events = new ModuleImportListener(GradleSettings.getInstance(project), listener);
		connection.subscribe(ProjectDataImportListener.TOPIC, events);
	}

	@Override
	public @NotNull SourceListing discover(@NotNull Project project) {
		if (!canSync(project)) return unlinked(project);

		CompletableFuture<Void> importing = observeImports(project);
		return sources.discover(directory(project), loadImports(project), importing);
	}

	@Override
	public @NotNull ScenarioPreparation prepare(
			@NotNull Project project,
			@NotNull ScenarioSource selected
	) throws IOException {
		boolean windows = System.getProperty("os.name").startsWith("Windows");
		var source = sources.resolve(directory(project), loadImports(project), selected.getId());
		Path root = source.getExecutionDirectory();

		return preparation.resolve(root, source.getPreparationTaskPath(), windows, settings(project, root));
	}

	/**
	 * Reads the Gradle JVM, distribution, and offline mode the IDE uses for the linked build at the root.
	 */
	@NotNull ScenarioPreparationResolver.Settings settings(@NotNull Project project, @NotNull Path root) {
		GradleSettings gradle = GradleSettings.getInstance(project);
		GradleProjectSettings linked = gradle.getLinkedProjectsSettings().stream()
				.filter(candidate -> Path.of(candidate.getExternalProjectPath()).equals(root))
				.findFirst()
				.orElse(null);
		if (linked == null) return ScenarioPreparationResolver.Settings.DEFAULT;

		GradleInstallationManager installations = GradleInstallationManager.getInstance();
		String linkedPath = linked.getExternalProjectPath();
		// JVM resolution blocks cancellably; launches call this from their own thread without a progress context.
		String javaHome = ProgressManager.getInstance().runProcess(
				() -> installations.getGradleJvmPath(project, linkedPath),
				new EmptyProgressIndicator()
		);
		Path gradleHome = linked.getDistributionType() == DistributionType.LOCAL
				? installations.getGradleHomePath(project, linkedPath)
				: null;

		return new ScenarioPreparationResolver.Settings(
				javaHome == null ? null : Path.of(javaHome),
				gradleHome,
				gradle.isOfflineWork()
		);
	}

	private @Nullable CompletableFuture<Void> observeImports(@NotNull Project project) {
		List<CompletableFuture<Void>> operations = new ArrayList<>();
		for (var linked : GradleSettings.getInstance(project).getLinkedProjectsSettings()) {
			CompletableFuture<Void> operation = GradleSync.observe(project, linked.getExternalProjectPath());
			if (operation != null) operations.add(operation);
		}
		if (operations.isEmpty()) return null;

		return CompletableFuture.allOf(operations.toArray(CompletableFuture[]::new));
	}

	private @NotNull List<@Nullable ExternalProjectInfo> loadImports(@NotNull Project project) {
		List<ExternalProjectInfo> imports = new ArrayList<>();
		var manager = ProjectDataManager.getInstance();
		for (var linked : GradleSettings.getInstance(project).getLinkedProjectsSettings()) {
			String path = linked.getExternalProjectPath();
			imports.add(manager.getExternalProjectData(project, GradleConstants.SYSTEM_ID, path));
		}

		return imports;
	}

	private @Nullable Path directory(@NotNull Project project) {
		String basePath = project.getBasePath();
		return basePath == null ? null : Path.of(basePath);
	}

	private @NotNull SourceListing unlinked(@NotNull Project project) {
		if (hasBuildFiles(project))
			return SourceListing.builder()
					.status(SourceListingStatus.NOT_IMPORTED)
					.message("Import this project in the IDE to enable Anvil.")
					.build();

		return SourceListing.builder()
				.status(SourceListingStatus.UNSUPPORTED)
				.message("No supported linked project is available.")
				.build();
	}

	private boolean hasBuildFiles(@NotNull Project project) {
		String basePath = project.getBasePath();
		if (basePath == null) return false;

		Path directory = Path.of(basePath);
		return GradleConstants.KNOWN_GRADLE_FILES.stream()
				.anyMatch(name -> Files.isRegularFile(directory.resolve(name)));
	}
}
