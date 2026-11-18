package me.whereareiam.anvil.edition.java.data.item.model;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * Where a protocol's item keys come from: the PrismarineJS minecraft-data release, the commit it is pinned to and the
 * item file that names every item of the release's registry.
 */
@Value
@Builder
public class ItemDataSource {
	/**
	 * The GitHub repository, such as {@code PrismarineJS/minecraft-data}.
	 */
	@NotNull String repository;
	/**
	 * The minecraft-data release, such as {@code 3.117.0}.
	 */
	@NotNull String version;
	/**
	 * The commit the release is pinned to.
	 */
	@NotNull String commit;
	/**
	 * The item file, such as {@code data/pc/1.21.11/items.json}.
	 */
	@NotNull String items;
}
