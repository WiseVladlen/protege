package org.protege.editor.owl.neo4j;

@FunctionalInterface
public interface ElementEventHandler {
    void call(ElementEvent value);
}
