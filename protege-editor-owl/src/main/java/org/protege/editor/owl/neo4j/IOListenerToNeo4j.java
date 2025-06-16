package org.protege.editor.owl.neo4j;

import org.protege.editor.owl.model.io.IOListener;
import org.protege.editor.owl.model.io.IOListenerEvent;

public class IOListenerToNeo4j extends IOListener {
    private final Neo4jManager neo4jManager;

    public IOListenerToNeo4j(Neo4jManager neo4jManager) {
        this.neo4jManager = neo4jManager;
    }

    @Override
    public void beforeLoad(IOListenerEvent event) {
        if (neo4jManager != null) {
            neo4jManager.disconnect();
        }
    }

    @Override
    public void afterLoad(IOListenerEvent event) {
        if (neo4jManager != null) {
            neo4jManager.connect();
        }
    }


    @Override
    public void beforeSave(IOListenerEvent event) {
        // Nothing
    }

    @Override
    public void afterSave(IOListenerEvent event) {
        // Nothing
    }
}

