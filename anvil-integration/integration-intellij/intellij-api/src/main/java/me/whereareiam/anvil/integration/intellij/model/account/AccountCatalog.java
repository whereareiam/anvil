package me.whereareiam.anvil.integration.intellij.model.account;

import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * Accounts and editable project pools available through one project's configured sources.
 */
@Value
@Builder
public class AccountCatalog {
	@NotNull
	@Builder.Default
	List<AvailableAccount> accounts = List.of();
	@NotNull
	@Builder.Default
	Map<String, List<String>> pools = Map.of();
	@NotNull
	@Builder.Default
	List<String> problems = List.of();
}
