package cn.lemwood.serversee.api;

import java.util.logging.Handler;
import java.util.logging.LogRecord;

public class JULHandler extends Handler {
    private final ApiServer apiServer;

    public JULHandler(ApiServer apiServer) {
        this.apiServer = apiServer;
    }

    @Override
    public void publish(LogRecord record) {
        if (apiServer != null) {
            String message = record.getMessage();
            String level = record.getLevel().getName();
            String formatted = String.format("[%s] %s", level, message);
            apiServer.broadcastLog(formatted);
        }
    }

    @Override
    public void flush() {}

    @Override
    public void close() throws SecurityException {}
}
