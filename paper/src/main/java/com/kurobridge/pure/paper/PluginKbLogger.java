// KbLogger → 插件 JUL logger 适配（:core 日志经插件前缀落 Paper 控制台）
package com.kurobridge.pure.paper;

import com.kurobridge.pure.core.server.KbLogger;
import java.util.logging.Level;
import java.util.logging.Logger;

/** 插件 logger 后端（JavaPlugin.getLogger() 即 JUL Logger，Paper 自动附加 [插件名] 前缀）。 */
public final class PluginKbLogger implements KbLogger {

    private final Logger logger;

    public PluginKbLogger(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void debug(String message) {
        logger.fine(message);
    }

    @Override
    public void info(String message) {
        logger.info(message);
    }

    @Override
    public void warn(String message) {
        logger.warning(message);
    }

    @Override
    public void error(String message, Throwable error) {
        logger.log(Level.SEVERE, message, error);
    }
}
