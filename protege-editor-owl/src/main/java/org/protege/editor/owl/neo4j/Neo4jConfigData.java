package org.protege.editor.owl.neo4j;

public class Neo4jConfigData {
    private final String uri;
    private final String username;
    private final String password;
    private final String databaseName;

    public Neo4jConfigData(String uri, String username, String password, String databaseName) {
        this.uri = uri;
        this.username = username;
        this.password = password;
        this.databaseName = databaseName;
    }

    public String getUri() {
        return uri;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getDatabaseName() {
        return databaseName;
    }
}
