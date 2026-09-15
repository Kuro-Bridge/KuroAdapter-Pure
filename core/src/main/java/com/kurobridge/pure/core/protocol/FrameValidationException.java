// 两段式解析第二段（具体帧型）的拒绝：未知 type、已知 type 但 body 非法、事件帧带 id 等
package com.kurobridge.pure.core.protocol;

/** 具体帧型校验失败（两段式解析第二段；行为 = warn 丢弃不回执不断连）。 */
public final class FrameValidationException extends ProtocolException {

    private static final long serialVersionUID = 1L;

    public FrameValidationException(String message) {
        super(message);
    }
}
