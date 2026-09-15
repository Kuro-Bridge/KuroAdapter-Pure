// 配置读取/解析失败的类型化错误（不静默吞；行为对齐主仓 ConfigError）
package com.kurobridge.pure.core.business;

/** 配置非法（形状/内容），携带人类可读的期望形状说明。 */
public final class ConfigException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ConfigException(String message) {
        super(message);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
