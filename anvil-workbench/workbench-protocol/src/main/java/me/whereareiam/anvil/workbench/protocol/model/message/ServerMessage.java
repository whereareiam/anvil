package me.whereareiam.anvil.workbench.protocol.model.message;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * What a workbench sends on an engine's control connection: answers to requests, and events.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "message")
@JsonSubTypes({
		@JsonSubTypes.Type(Response.class),
		@JsonSubTypes.Type(ReverseConnection.class)
})
public sealed interface ServerMessage permits Response, ReverseConnection {
}
