// hello_ack 响应体（ok 体携带服务端身份与绑定快照；error 体携带拒绝原因）
package com.kurobridge.pure.core.protocol.message;

import java.util.List;

/** 握手结果 body（服务端身份信息并入 body）。 */
public sealed interface HelloAckBody permits HelloAckBody.Ok, HelloAckBody.Error {

    /** ok 体：serverId/version 非空、protocolVersion 三元组、channelBindings 完整快照（空数组合法）。 */
    record Ok(String serverId, String version, String protocolVersion, List<String> channelBindings)
            implements HelloAckBody {}

    /** error 体：reason 非空，配 close 1002/1008。 */
    record Error(String reason) implements HelloAckBody {}
}
