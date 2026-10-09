package me.whereareiam.anvil.integration.intellij.component.console;

import com.intellij.execution.process.AnsiEscapeDecoder;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.util.Key;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Decodes source formatting once and retains a bounded styled tail for one console view. Output readers
 * share serialized decoder state. Retained colors do not depend on escape sequences that have already left
 * the history window.
 */
final class ConsoleBuffer {
	private static final int MAX_ENTRIES = 10_000;
	private static final int MAX_CHARACTERS = 2_000_000;
	private static final int MAX_ENTRY_CHARACTERS = 65_536;
	private static final String ENTRY_TRUNCATED = "\n[Output entry truncated.]\n";

	private final ArrayDeque<Entry> entries = new ArrayDeque<>();
	private final Map<Channel, DecoderState> decoders = new HashMap<>();
	private final Set<Channel> observedChannels = new HashSet<>();
	private final Map<String, Channel> latestChannels = new HashMap<>();
	private int characters;
	private boolean truncated;

	@NotNull
	synchronized List<Chunk> append(
			@Nullable String process,
			@Nullable UUID executionId,
			long sequence,
			@NotNull String text,
			boolean error
	) {
		Entry entry = decodeEntry(process, executionId, sequence, text, error);
		entries.addLast(entry);
		entry.state.retainedEntries++;
		characters += entry.characters;
		while (entries.size() > MAX_ENTRIES || characters > MAX_CHARACTERS) {
			Entry removed = entries.removeFirst();
			characters -= removed.characters;
			removed.state.retainedEntries--;
			prune(removed.state);
			truncated = true;
		}
		return entry.chunks;
	}

	private Entry decodeEntry(
			@Nullable String process,
			@Nullable UUID executionId,
			long sequence,
			@NotNull String text,
			boolean error
	) {
		Channel channel = new Channel(process, executionId);
		DecoderState state = decoder(channel);
		if (sequence > 0) {
			if (state.sequence > 0 && sequence > state.sequence + 1)
				state.decoder = new AnsiEscapeDecoder();
			state.sequence = Math.max(state.sequence, sequence);
		}

		Key<?> stream = error ? ProcessOutputTypes.STDERR : ProcessOutputTypes.STDOUT;
		List<Chunk> chunks = new ArrayList<>();
		int retainedLength = Math.min(text.length(), MAX_ENTRY_CHARACTERS);
		state.decoder.escapeText(
				text.substring(0, retainedLength),
				stream,
				(value, attributes) -> {
					if (value.isEmpty()) return;
					if (chunks.isEmpty() && process != null) {
						chunks.add(new Chunk("[" + process + "] ", stream));
					}
					chunks.add(new Chunk(value, attributes));
				});

		if (retainedLength < text.length()) {
			// Discard the oversized entry's tail from the view, while consuming its actual resets/styles.
			for (int offset = retainedLength; offset < text.length(); offset += MAX_ENTRY_CHARACTERS) {
				state.decoder.escapeText(
						text.substring(offset, Math.min(text.length(), offset + MAX_ENTRY_CHARACTERS)),
						stream,
						(value, attributes) -> {
						}
				);

				state.decoder.escapeText("", stream, (value, attributes) -> {
				});
			}

			if (chunks.isEmpty() && process != null) chunks.add(new Chunk("[" + process + "] ", stream));
			chunks.add(new Chunk(ENTRY_TRUNCATED, ProcessOutputTypes.SYSTEM));
		}

		// The native lexer releases consumed input on the next append. Empty input preserves SGR and
		// incomplete escape state.
		state.decoder.escapeText("", stream, (value, attributes) -> {});
		int retainedCharacters = retainedLength
				+ (process == null ? 0 : process.length() + 3)
				+ (retainedLength < text.length() ? ENTRY_TRUNCATED.length() : 0);

		return new Entry(state, List.copyOf(chunks), retainedCharacters);
	}

	synchronized void replay(@Nullable String process, @NotNull Consumer<Chunk> output) {
		if (truncated) {
			output.accept(new Chunk(
					"Earlier output is no longer retained.\n", ProcessOutputTypes.SYSTEM)
			);
		}

		for (Entry entry : entries) {
			if (process == null
					|| entry.state.channel.process == null
					|| process.equals(entry.state.channel.process))
				entry.chunks.forEach(output);
		}
	}

	synchronized void discardHistory() {
		entries.clear();
		decoders.values().forEach(state -> state.retainedEntries = 0);
		characters = 0;
		truncated = false;
	}

	synchronized void clear() {
		discardHistory();
		decoders.clear();
		latestChannels.clear();
		observedChannels.clear();
	}

	private DecoderState decoder(Channel channel) {
		Channel latest = latestChannels.get(channel.process);
		if (observedChannels.add(channel)) {
			latestChannels.put(channel.process, channel);
			if (latest != null) {
				DecoderState retired = decoders.get(latest);
				if (retired != null) prune(retired);
			}
		}

		return decoders.computeIfAbsent(channel, DecoderState::new);
	}

	private void prune(DecoderState state) {
		if (state.retainedEntries == 0
				&& !state.channel.equals(latestChannels.get(state.channel.process)))
			decoders.remove(state.channel);
	}

	record Chunk(@NotNull String text, @NotNull Key<?> attributes) {
	}

	private record Channel(@Nullable String process, @Nullable UUID executionId) {
	}

	private record Entry(DecoderState state, List<Chunk> chunks, int characters) {
	}

	@RequiredArgsConstructor
	private static final class DecoderState {
		private final Channel channel;
		private AnsiEscapeDecoder decoder = new AnsiEscapeDecoder();
		private long sequence;
		private int retainedEntries;
	}
}
