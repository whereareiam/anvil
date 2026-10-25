package me.whereareiam.anvil.workbench.protocol.model.connection;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * The workbench's answer to a connection's header.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "answer")
@JsonSubTypes({
		@JsonSubTypes.Type(Accepted.class),
		@JsonSubTypes.Type(Refused.class),
		@JsonSubTypes.Type(Paired.class),
		@JsonSubTypes.Type(Uploaded.class)
})
public sealed interface ConnectionAnswer permits Accepted, Refused, Paired, Uploaded {
}
