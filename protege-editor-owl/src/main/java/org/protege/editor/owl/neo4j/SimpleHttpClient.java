package org.protege.editor.owl.neo4j;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class SimpleHttpClient implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(SimpleHttpClient.class);

    private final String url = "http://localhost:8080";
    private final String uuid = UUID.randomUUID().toString();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ScheduledExecutorService messageExecutor;

    private final CloseableHttpClient client;
    private final ElementEventHandler eventHandler;

    public SimpleHttpClient(ElementEventHandler eventHandler) {
        client = HttpClients.createDefault();

        this.eventHandler = eventHandler;
        this.messageExecutor = Executors.newSingleThreadScheduledExecutor();

        listenMessages();
    }

    public void sendMessage(String message) {
        HttpPost post = new HttpPost(url + "/api/messages/" + uuid);
        post.setHeader("Content-Type", "text/plain; charset=UTF-8");
        post.setEntity(new StringEntity(message, "UTF-8"));

        try (CloseableHttpResponse response = client.execute(post)) {
            logger.info("Status code: {}", response.getStatusLine().getStatusCode());
        } catch (Exception e) {
            logger.error("Error sending message", e);
        }
    }

    private void listenMessages() {
        HttpGet get = new HttpGet(url + "/api/messages/" + uuid);
        get.setHeader("Content-Type", "text/plain; charset=UTF-8");

        messageExecutor.scheduleAtFixedRate(() -> {
            try {
                HttpResponse httpResponse = client.execute(get);
                HttpEntity entity = httpResponse.getEntity();

                String response;
                if (entity != null) {
                    try (InputStream inputStream = entity.getContent(); BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                        response = reader.lines().collect(Collectors.joining("\n"));
                    }
                } else {
                    response = null;
                }

                if (response == null) {
                    logger.info("No new messages found");
                } else {
                    ElementEvent event = objectMapper.readValue(response, ElementEvent.class);
                    SwingUtilities.invokeLater(() -> eventHandler.call(event));

                    logger.info("Successfully getting message: {}", response);
                }
            } catch (Exception e) {
                logger.error("Error getting message", e);
            }
        }, 0, 3, TimeUnit.SECONDS);
    }

    @Override
    public void close() throws Exception {
        messageExecutor.shutdown();
    }
}
