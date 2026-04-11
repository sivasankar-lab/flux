package com.example.socialmedia_poc.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * Runs BEFORE any beans are created.
 * Parses DATABASE_URL (Railway / Heroku / Render format: postgres://user:pass@host:port/db)
 * and injects spring.datasource.url / username / password into the environment.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String databaseUrl = environment.getProperty("DATABASE_URL");
        if (databaseUrl == null || databaseUrl.isEmpty()) {
            databaseUrl = System.getenv("DATABASE_URL");
        }

        if (databaseUrl == null || databaseUrl.isEmpty()) {
            return; // No DATABASE_URL — let spring.datasource.* properties handle it
        }

        try {
            // Normalise: postgres:// → postgresql://
            if (databaseUrl.startsWith("postgres://")) {
                databaseUrl = "postgresql://" + databaseUrl.substring("postgres://".length());
            }

            URI uri = new URI(databaseUrl);
            String host = uri.getHost();
            int port = uri.getPort() == -1 ? 5432 : uri.getPort();
            String path = uri.getPath(); // e.g. /railway
            String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + path;

            if (uri.getQuery() != null) {
                jdbcUrl += "?" + uri.getQuery();
            }

            String[] userInfo = uri.getUserInfo().split(":", 2);
            String username = userInfo[0];
            String password = userInfo.length > 1 ? userInfo[1] : "";

            Map<String, Object> props = new HashMap<>();
            props.put("spring.datasource.url", jdbcUrl);
            props.put("spring.datasource.username", username);
            props.put("spring.datasource.password", password);

            // Highest priority — overrides application.properties
            environment.getPropertySources()
                    .addFirst(new MapPropertySource("databaseUrlProperties", props));

            System.out.println("[DatabaseUrlPostProcessor] Parsed DATABASE_URL → jdbc:postgresql://"
                    + host + ":" + port + path);
        } catch (Exception e) {
            System.err.println("[DatabaseUrlPostProcessor] Failed to parse DATABASE_URL: " + e.getMessage());
        }
    }
}
