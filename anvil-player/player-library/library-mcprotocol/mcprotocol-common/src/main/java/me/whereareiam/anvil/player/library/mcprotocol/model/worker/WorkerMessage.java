package me.whereareiam.anvil.player.library.mcprotocol.model.worker;

/**
 * A correlated response, readiness announcement, or player event from the child process.
 */
public sealed interface WorkerMessage permits WorkerResponse, WorkerReady, WorkerEvent {
}
