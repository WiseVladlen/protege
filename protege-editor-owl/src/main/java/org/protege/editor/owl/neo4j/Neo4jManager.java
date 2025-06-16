package org.protege.editor.owl.neo4j;

import org.neo4j.driver.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Neo4jManager implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(Neo4jManager.class);

    private Driver driver;
    private Session session;

    private final Neo4jConfigData config;

    public Neo4jManager(Neo4jConfigData config) {
        this.config = config;
    }

    public void connect() {
        logger.info("Try to connect");

        if (driver != null || session != null) {
            throw new IllegalStateException("Already connected. Call disconnect() first.");
        }

        driver = GraphDatabase.driver(config.getUri(), AuthTokens.basic(config.getUsername(), config.getPassword()));
        session = driver.session(SessionConfig.defaultConfig());

        logger.info("Connection is established");
    }

    public void disconnect() {
        logger.info("Disconnect");

        if (session != null) {
            session.close();
            session = null;
        }
        if (driver != null) {
            driver.close();
            driver = null;
        }
    }

    public Session getSession() {
        if (session == null) {
            throw new IllegalStateException("Not connected. Call connect() first.");
        }
        return session;
    }

    @Override
    public void close() {
        disconnect();
    }
}