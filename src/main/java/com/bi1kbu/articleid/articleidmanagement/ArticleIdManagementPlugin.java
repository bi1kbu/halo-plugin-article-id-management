package com.bi1kbu.articleid.articleidmanagement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;

/**
 * <p>Plugin main class to manage the lifecycle of the plugin.</p>
 * <p>This class must be public and have a public constructor.</p>
 * <p>Only one main class extending {@link BasePlugin} is allowed per plugin.</p>
 *
 * @author bi1kbu
 * @since 1.0.0
 */
@Component
public class ArticleIdManagementPlugin extends BasePlugin {
    private static final Logger log = LoggerFactory.getLogger(ArticleIdManagementPlugin.class);

    public ArticleIdManagementPlugin(PluginContext pluginContext) {
        super(pluginContext);
    }

    @Override
    public void start() {
        log.info("Article ID Management plugin started");
    }

    @Override
    public void stop() {
        log.info("Article ID Management plugin stopped");
    }
}
