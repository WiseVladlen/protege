package org.protege.editor.owl.neo4j;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class ElementEvent {
    public String type;
    public Map<String, String> props;
    public boolean isAdd;

    @JsonCreator
    public ElementEvent(
            @JsonProperty("type") String type,
            @JsonProperty("props") Map<String, String> props,
            @JsonProperty("isAdd") boolean isAdd
    ) {
        this.type = type;
        this.props = props;
        this.isAdd = isAdd;
    }
}