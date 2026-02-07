package com.bi1kbu.articleid.articleidmanagement.service;

import com.bi1kbu.articleid.articleidmanagement.domain.DefaultRules;
import com.bi1kbu.articleid.articleidmanagement.domain.PluginState;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.springframework.stereotype.Component;

@Component
public class StateStorage {
    private final ObjectMapper objectMapper;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Path filePath;

    public StateStorage() {
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
        this.filePath = resolveFilePath();
    }

    public PluginState read() {
        lock.readLock().lock();
        try {
            if (!Files.exists(filePath)) {
                var state = new PluginState();
                state.setRuleConfig(DefaultRules.create());
                return state;
            }
            var state = objectMapper.readValue(filePath.toFile(), PluginState.class);
            if (state.getRuleConfig() == null) {
                state.setRuleConfig(DefaultRules.create());
            }
            if (state.getLedger() == null) {
                state.setLedger(new java.util.ArrayList<>());
            }
            if (state.getLogs() == null) {
                state.setLogs(new java.util.ArrayList<>());
            }
            return state;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read plugin state", e);
        } finally {
            lock.readLock().unlock();
        }
    }

    public PluginState write(PluginState state) {
        lock.writeLock().lock();
        try {
            Files.createDirectories(filePath.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), state);
            return state;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write plugin state", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private Path resolveFilePath() {
        String customPath = System.getenv("ARTICLE_ID_DATA_FILE");
        if (customPath != null && !customPath.isBlank()) {
            return Paths.get(customPath);
        }
        String home = System.getProperty("user.home");
        return Paths.get(home, ".halo", "article-id-management", "state.json");
    }
}
