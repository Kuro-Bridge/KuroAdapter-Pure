// 单条 WS 连接抽象（宿主实现；core 只依赖此接口——测试用 fake，生产由 PureWsServer 适配）
package com.kurobridge.pure.core.server;

/** 传输连接接口：send 抛错视为连接已死，由 onClose 收尾。 */
public interface WsConnection {

    void send(String text);

    void close(int code, String reason);

    boolean isOpen();
}
