package me.whereareiam.anvil.workbench.protocol.model.file;

import me.whereareiam.anvil.workbench.protocol.model.WorkbenchRequest;

/**
 * Requests about the files of one process workspace.
 *
 * @param <R> type of the workbench's answer
 */
public sealed interface FileRequest<R> extends WorkbenchRequest<R>
		permits CheckFile, ReadFile, WriteFile, ListFiles, DeleteFile {
}
