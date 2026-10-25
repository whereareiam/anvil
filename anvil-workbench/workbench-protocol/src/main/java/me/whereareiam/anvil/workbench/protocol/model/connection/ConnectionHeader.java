package me.whereareiam.anvil.workbench.protocol.model.connection;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * The first line of every connection to a workbench, naming what the connection is for. Except while pairing, it
 * carries the engine's token.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
		@JsonSubTypes.Type(Pair.class),
		@JsonSubTypes.Type(Control.class),
		@JsonSubTypes.Type(Stdio.class),
		@JsonSubTypes.Type(Stderr.class),
		@JsonSubTypes.Type(Upload.class),
		@JsonSubTypes.Type(Tunnel.class),
		@JsonSubTypes.Type(Reverse.class)
})
public sealed interface ConnectionHeader permits Pair, EngineConnection {
}
