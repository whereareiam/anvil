package me.whereareiam.anvil.integration.intellij.view.window.account.pool;

import com.intellij.openapi.ui.ValidationInfo;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import me.whereareiam.anvil.integration.intellij.account.AccountLibrary;
import me.whereareiam.anvil.integration.intellij.model.account.AvailableAccount;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class PoolEditorController {
	private final @NotNull AccountLibrary library;
	private final @Nullable String original;
	private final @NotNull Map<String, List<String>> pools;
	private final @NotNull PoolEditorForm form;

	PoolEditorController(
			@NotNull AccountLibrary library,
			@Nullable String original,
			@NotNull Map<String, List<String>> pools,
			@NotNull List<AvailableAccount> accounts
	) {
		this.library = library;
		this.original = original;
		this.pools = pools;

		List<String> selected = original == null
				? List.of()
				: pools.getOrDefault(original, List.of());

		form = new PoolEditorForm(original, selected, accounts);
	}

	@NotNull PoolEditorForm form() {
		return form;
	}

	@Nullable ValidationInfo validate() {
		return form.validateName(original, pools.keySet());
	}

	void save() throws IOException {
		library.savePool(original, form.poolName(), form.selectedMembers());
	}
}
