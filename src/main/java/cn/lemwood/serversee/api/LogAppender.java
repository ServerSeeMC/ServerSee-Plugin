package cn.lemwood.serversee.api;

import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;

public class LogAppender extends AbstractAppender {
    private final ApiServer apiServer;

    protected LogAppender(String name, Filter filter, Layout<? extends Serializable> layout, boolean ignoreExceptions, ApiServer apiServer) {
        super(name, filter, layout, ignoreExceptions);
        this.apiServer = apiServer;
    }

    @Override
    public void append(LogEvent event) {
        if (apiServer != null) {
            String message = event.getMessage().getFormattedMessage();
            String level = event.getLevel().name();
            String formatted = String.format("[%s] %s", level, message);
            apiServer.broadcastLog(formatted);
        }
    }

    public static LogAppender createAppender(String name, Filter filter, Layout<? extends Serializable> layout, ApiServer apiServer) {
        return new LogAppender(name, filter, layout, true, apiServer);
    }
}
