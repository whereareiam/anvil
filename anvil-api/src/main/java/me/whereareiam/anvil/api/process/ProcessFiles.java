package me.whereareiam.anvil.api.process;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.List;

/**
 * The files of one process's workspace, wherever the workbench running the process keeps them. Platform providers
 * configure a process through them, and tests read and change what a running process wrote.
 *
 * <p>Paths are relative to the workspace and separated by {@code /}, such as {@code config/paper-global.yml}; a path
 * that is absolute, climbs out with {@code ..} or passes through a symbolic link is refused. Text is UTF-8.</p>
 *
 * <pre>{@code
 * ProcessFiles files = anvil.processes().server("lobby").files();
 * String properties = files.read("server.properties");
 * files.write("plugins/Example/config.yml", "enabled: false\n");
 * }</pre>
 */
public interface ProcessFiles {
	/**
	 * Describes where the files live for a person reading diagnostics, such as the workspace's absolute path on
	 * the machine that runs the process. It is not meant to be opened by code.
	 *
	 * @return location description
	 */
	@NotNull String location();

	/**
	 * Returns whether a file or directory exists.
	 *
	 * @param path workspace-relative path
	 * @return true when something exists at the path
	 * @throws IOException when the workspace cannot be read
	 */
	boolean exists(@NotNull String path) throws IOException;

	/**
	 * Reads a whole text file.
	 *
	 * @param path workspace-relative path of a file
	 * @return file content
	 * @throws NoSuchFileException when the file does not exist
	 * @throws IOException when the file cannot be read
	 */
	@NotNull String read(@NotNull String path) throws IOException;

	/**
	 * Writes a whole text file, creating its directories and replacing an existing file.
	 *
	 * @param path workspace-relative path of a file
	 * @param content new file content
	 * @throws IOException when the file cannot be written
	 */
	void write(@NotNull String path, @NotNull String content) throws IOException;

	/**
	 * Lists the names of a directory's entries in name order.
	 *
	 * @param directory workspace-relative path of a directory, or an empty string for the workspace itself
	 * @return entry names, without their directory
	 * @throws NoSuchFileException when the directory does not exist
	 * @throws IOException when the directory cannot be read
	 */
	@NotNull List<String> list(@NotNull String directory) throws IOException;

	/**
	 * Deletes a file, or a directory with everything in it.
	 *
	 * @param path workspace-relative path
	 * @return true when something was deleted, false when nothing existed at the path
	 * @throws IOException when it cannot be deleted
	 */
	boolean delete(@NotNull String path) throws IOException;
}
