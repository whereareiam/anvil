package me.whereareiam.anvil.integration.intellij.view.window.account;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.ui.Messages;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.swing.JComponent;

import me.whereareiam.anvil.integration.intellij.account.AccountLibrary;
import me.whereareiam.anvil.integration.intellij.model.account.AccountCatalog;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.type.AccountSource;
import me.whereareiam.anvil.integration.intellij.view.window.account.component.AccountListPanel;
import me.whereareiam.anvil.integration.intellij.view.window.account.pool.AccountPoolsPanel;
import me.whereareiam.anvil.integration.intellij.view.window.account.pool.PoolEditorDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Project account manager: account and pool operations persist immediately; storage is explicitly applied.
 */
final class ProjectAccountsController {
	private final Project project;
	private final BooleanSupplier disposed;
	private final @Nullable ScenarioSource source;
	private final AccountLibrary library;
	private final ProjectAccountsForm form;
	private final AccountListPanel accounts;
	private final AccountPoolsPanel pools;
	private final AccountStorageController storage;

	private AccountCatalog catalog = AccountCatalog.builder().build();
	private Map<String, List<String>> poolMembers = Map.of();
	private long refreshGeneration;
	private boolean operation;
	private boolean refreshing;

	ProjectAccountsController(
			@NotNull Project project,
			@Nullable ScenarioSource source,
			@NotNull BooleanSupplier disposed
	) {
		this.project = project;
		this.source = source;
		this.disposed = disposed;

		library = project.getService(AccountLibrary.class);
		accounts = new AccountListPanel(
				source != null,
				library.includesGlobal(),
				this::addAccount,
				this::importAccount,
				this::exportAccount,
				this::removeAccount,
				this::refresh,
				enabled -> {
					library.setIncludesGlobal(enabled);
					refresh();
				}
		);

		pools = new AccountPoolsPanel(() -> editPool(null), this::editPool, this::removePool);
		storage = new AccountStorageController(project, library, this::refresh);
		form = new ProjectAccountsForm(accounts, pools, storage.panel());

		refresh();
	}

	@NotNull JComponent component() {
		return form.getComponent();
	}

	private void refresh() {
		if (disposed.getAsBoolean() || project.isDisposed()) return;

		long generation = ++refreshGeneration;
		refreshing = true;
		updateBusy();
		form.showFeedback("Loading project accounts…");
		ApplicationManager.getApplication().executeOnPooledThread(() -> {
			try {
				AccountCatalog loaded = library.catalog();
				ApplicationManager.getApplication()
						.invokeLater(() -> showCatalog(generation, loaded), ModalityState.any());
			} catch (RuntimeException failure) {
				ApplicationManager.getApplication()
						.invokeLater(() -> showRefreshFailure(generation, failure), ModalityState.any());
			}
		});
	}

	private boolean currentRefresh(long generation) {
		return !disposed.getAsBoolean() && !project.isDisposed() && generation == refreshGeneration;
	}

	private void showRefreshFailure(long generation, @NotNull RuntimeException failure) {
		if (!currentRefresh(generation)) return;

		refreshing = false;
		updateBusy();
		form.showFeedback("Could not load project accounts: " + failure.getMessage());
	}

	private void showCatalog(long generation, AccountCatalog loaded) {
		if (!currentRefresh(generation)) return;

		refreshing = false;
		catalog = loaded;
		poolMembers = loaded.getPools();
		accounts.setAccounts(loaded.getAccounts());
		pools.setPools(poolMembers, !loaded.getAccounts().isEmpty());
		updateBusy();

		form.showFeedback(
				loaded.getProblems().isEmpty()
						? loaded.getAccounts().size() + " accounts available to this project."
						: String.join("\n", loaded.getProblems())
		);
	}

	private void updateBusy() {
		accounts.setBusy(operation || refreshing);
		pools.setBusy(operation || refreshing);
		storage.panel().setBusy(operation);
	}

	private void addAccount() {
		if (source == null) return;

		String id = Messages.showInputDialog(project, "Choose a local ID for the account:", "Add Project Account",
				null, "", (InputValidatorEx) input -> {
					if (!library.isValidAccountId(input))
						return "Use letters, numbers, dots, underscores, or hyphens.";
					if (catalog.getAccounts().stream().anyMatch(entry -> entry.getAccount().getAccountId().equals(input)))
						return "This account ID is already available to the project.";

					return null;
				});

		if (id == null) return;
		new AuthenticationDialog(project, source, id, this::refresh).show();
	}

	private void importAccount() {
		var selected = FileChooser.chooseFiles(
				FileChooserDescriptorFactory.createSingleFileDescriptor("json"),
				project, null
		);

		if (selected.length == 0) return;

		Path source = Path.of(selected[0].getPath());
		runOperation(() -> library.inspect(source), metadata -> {
			String id = metadata.getAccountId();
			boolean global = catalog.getAccounts().stream().anyMatch(
					entry -> entry.getSource() == AccountSource.GLOBAL
							&& entry.getAccount().getAccountId().equals(id));

			if (global) {
				form.showFeedback(
						"Account '" + id + "' is already available globally. Disable global accounts"
								+ " before importing a project copy.");
				return;
			}

			Path destination = library.directory();
			if (Files.exists(destination.resolve(id + ".json"))
					&& Messages.showYesNoDialog(project,
					"Replace project account '" + id + "' with the selected file?",
					"Replace Account",
					Messages.getQuestionIcon()) != Messages.YES)
				return;

			runOperation(
					() -> library.importAccount(source),
					ignored -> refresh(),
					"Importing account"
			);
		}, "Reading account file…");
	}

	private void exportAccount() {
		var selected = accounts.selected();
		if (selected == null) return;

		var descriptor = new FileSaverDescriptor("Export Account", "Choose where to save this account file.", "json");
		var destination = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
				.save(selected.getAccount().getAccountId() + ".json");
		if (destination == null) return;

		runOperation(() -> {
			library.exportAccount(selected, destination.getFile().toPath());
			return null;
		}, ignored -> form.showFeedback("Account exported."), "Exporting account…");
	}

	private void removeAccount() {
		var selected = accounts.selected();
		if (selected == null || selected.getSource() != AccountSource.PROJECT) return;

		String id = selected.getAccount().getAccountId();
		String used = String.join(
				", ",
				poolMembers.entrySet().stream()
						.filter(entry -> entry.getValue().contains(id))
						.map(Map.Entry::getKey)
						.toList()
		);

		String message = "Remove project account '" + id + "'? Its saved sign-in will be deleted.";
		if (!used.isEmpty()) message += "\nPool memberships are retained in: " + used;
		if (Messages.showYesNoDialog(
				project, message, "Remove Project Account", Messages.getWarningIcon())
				!= Messages.YES)
			return;

		runOperation(() -> {
			library.removeAccount(selected);
			return null;
		}, ignored -> refresh(), "Removing account…");
	}

	private void editPool(@Nullable String name) {
		var editor = new PoolEditorDialog(project, library, name, poolMembers, catalog.getAccounts());
		if (editor.showAndGet()) refresh();
	}

	private void removePool(String name) {
		if (Messages.showYesNoDialog(project, "Remove pool '" + name + "'? Account files will remain available.",
				"Remove Pool", Messages.getQuestionIcon()) != Messages.YES)
			return;

		runOperation(() -> {
			library.removePool(name);
			return null;
		}, ignored -> refresh(), "Removing pool…");
	}

	private <T> void runOperation(Callable<T> action, Consumer<T> completed, String message) {
		operation = true;
		updateBusy();
		form.showFeedback(message);
		ApplicationManager.getApplication().executeOnPooledThread(() -> {
			try {
				T result = action.call();
				ApplicationManager.getApplication().invokeLater(() -> {
					if (disposed.getAsBoolean() || project.isDisposed()) return;

					operation = false;
					updateBusy();
					form.showFeedback("");
					completed.accept(result);
				}, ModalityState.any());
			} catch (Exception failure) {
				ApplicationManager.getApplication().invokeLater(() -> {
					if (disposed.getAsBoolean() || project.isDisposed()) return;

					operation = false;
					updateBusy();
					form.showFeedback("Could not complete the operation: " + failure.getMessage());
				}, ModalityState.any());
			}
		});
	}
}
