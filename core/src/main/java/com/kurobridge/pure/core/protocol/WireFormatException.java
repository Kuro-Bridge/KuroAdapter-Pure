// 两段式解析第一段（线格式骨架）的拒绝：type 非 snake_case、id 非 UUID、缺 header 等
package com.kurobridge.pure.core.protocol;

/** 线格式骨架校验失败（两段式解析第一段；行为 = warn 丢弃不断连）。 */
public final class WireFormatException extends ProtocolException {

    private static final long serialVersionUID = 1L;

    public WireFormatException(String message) {
        super(message);
    }

    public WireFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
