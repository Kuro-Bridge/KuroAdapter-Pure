// JUL 实现的 KbLogger（生产缺省；Paper 侧下一阶段可换成插件 logger 适配）
package com.kurobridge.pure.core.server;

import java.util.logging.Level;
import java.util.logging.Logger;

/** java.util.logging 后端。 */
public final class JulKbLogger implements KbLogger {

    private final Logger logger;

    public JulKbLogger(String name) {
        this.logger = Logger.getLogger(name);
    }

    @Override
    public void debug(String message) {
        logger.log(Level.FINE, message);
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
