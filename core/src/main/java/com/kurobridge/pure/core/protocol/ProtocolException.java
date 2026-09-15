// 协议层类型化错误基类（unchecked：编解码/校验失败在收帧分发处统一捕获，不外溢）
package com.kurobridge.pure.core.protocol;

/** kurobridge-ws 帧处理失败的类型化错误。 */
public class ProtocolException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ProtocolException(String message) {
        super(message);
    }

    public ProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
