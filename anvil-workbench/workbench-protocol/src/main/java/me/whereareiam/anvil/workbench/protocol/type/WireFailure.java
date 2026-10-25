package me.whereareiam.anvil.workbench.protocol.type;

/**
 * What can go wrong on a workbench, as both ends of a connection name it. Each kind stands for the exception the
 * engine would see had the workbench run in its own JVM, so a remote run fails as a local one does; the kinds, not
 * Java class names, are the protocol's vocabulary.
 */
public enum WireFailure {
	/**
	 * A file of a process's workspace does not exist; carries {@code file}.
	 */
	MISSING_FILE,
	/**
	 * Any other failure to read or write.
	 */
	IO,
	/**
	 * The engine passed an argument the workbench refuses.
	 */
	INVALID_ARGUMENT,
	/**
	 * The engine asked for something the workbench's state does not allow now.
	 */
	INVALID_STATE,
	/**
	 * The workbench cannot do what the engine asked at all, such as attach to a container's agent.
	 */
	UNSUPPORTED,
	/**
	 * A scenario's declaration is invalid.
	 */
	SCENARIO_VALIDATION,
	/**
	 * Preparing what a process needs failed, such as a download or a Java installation.
	 */
	PROVISIONING,
	/**
	 * Starting, talking to or stopping a process failed; carries {@code processName}.
	 */
	PROCESS,
	/**
	 * The workbench cannot meet a scenario's requirements; carries {@code scenarioName}, {@code workbenchName} and
	 * {@code unmet}.
	 */
	REQUIREMENTS,
	/**
	 * Anything the engine has no closer exception for; carries the workbench's message.
	 */
	WORKBENCH
}
