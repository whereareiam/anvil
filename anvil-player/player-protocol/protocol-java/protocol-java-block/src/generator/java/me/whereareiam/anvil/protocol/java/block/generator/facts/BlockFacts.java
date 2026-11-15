package me.whereareiam.anvil.protocol.java.block.generator.facts;

import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Singular;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The hand-maintained facts of {@code block-facts.toml} that minecraft-data does not carry, merged per block.
 *
 * <p>Each {@code [[block]]} entry ({@link BlockFactEntry}) names blocks and the vanilla code its values come from.
 * Several entries may name one block, such as a tag and the block's own constants: their behaviours add up, and every
 * other value may be set once per block. An entry never names a default (kind {@code OTHER}, collision {@code FIXED},
 * fluid {@code NONE}), which a block it does not set has anyway.</p>
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class BlockFacts {
	private static final Pattern NAME = Pattern.compile("minecraft:[a-z0-9_]+");

	private final @NotNull Map<String, BlockFact> facts;

	/**
	 * Reads and merges the facts of a {@code block-facts.toml}.
	 *
	 * @param file the file
	 * @return the merged facts
	 * @throws IOException when the file cannot be read or bound
	 * @throws IllegalStateException when an entry breaks the rules above
	 */
	public static @NotNull BlockFacts read(@NotNull Path file) throws IOException {
		Document document = new TomlMapper().readValue(file.toFile(), Document.class);
		Map<String, BlockFactMerge> merges = new HashMap<>();
		for (int index = 0; index < document.getBlock().size(); index++) {
			BlockFactEntry entry = document.getBlock().get(index);
			String position = file.getFileName() + " block #" + (index + 1);
			check(entry, position);
			for (String name : entry.getNames())
				merges.computeIfAbsent(name, BlockFactMerge::new).add(entry);
		}

		Map<String, BlockFact> facts = new HashMap<>();
		merges.forEach((name, merge) -> facts.put(name, merge.fact()));
		return new BlockFacts(Map.copyOf(facts));
	}

	/**
	 * Returns the facts of a block, or vanilla's defaults when no entry names it.
	 *
	 * @param name the namespaced block name, such as {@code minecraft:ice}
	 * @return the block's facts
	 */
	public @NotNull BlockFact of(@NotNull String name) {
		return facts.getOrDefault(name, BlockFact.DEFAULT);
	}

	/**
	 * The blocks some entry names.
	 *
	 * @return the namespaced names
	 */
	public @NotNull Set<String> names() {
		return facts.keySet();
	}

	private static void check(BlockFactEntry entry, String position) {
		if (entry.getSource().isBlank())
			throw new IllegalStateException(position + " must name the vanilla source of its values");
		if (entry.getNames().isEmpty()) throw new IllegalStateException(position + " must name at least one block");

		entry.getNames().stream().filter(name -> !NAME.matcher(name).matches()).findFirst().ifPresent(name -> {
			throw new IllegalStateException(position + " names '" + name + "', which is not a minecraft: block name");
		});
		entry.getPropertyStarts().forEach((property, start) -> {
			if (start < 1) throw new IllegalStateException(position + " starts " + property + " at " + start
					+ "; property-start names only properties that start above 0");
		});
	}

	/**
	 * The file's root: its {@code [[block]]} entries.
	 */
	@Value
	@Builder
	@Jacksonized
	static class Document {
		@Singular("entry")
		@NotNull List<BlockFactEntry> block;
	}
}
