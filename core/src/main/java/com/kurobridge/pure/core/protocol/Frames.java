// 出帧工厂：Server→Peer 全部帧型 + 未知帧回执（body 用 Jackson 显式构建，不依赖反射）
package com.kurobridge.pure.core.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kurobridge.pure.core.protocol.message.BindingsUpdatedBody;
import com.kurobridge.pure.core.protocol.message.CommandResultBody;
import com.kurobridge.pure.core.protocol.message.DeathBody;
import com.kurobridge.pure.core.protocol.message.GameChatBody;
import com.kurobridge.pure.core.protocol.message.JoinBody;
import com.kurobridge.pure.core.protocol.message.LeaveBody;
import com.kurobridge.pure.core.protocol.message.QueryResultBody;
import com.kurobridge.pure.core.protocol.message.StatusBody;
import java.util.List;

/** 出帧构建（构造 Frame；线文本经 FrameCodec.encode 落地）。 */
public final class Frames {

    /** 未知帧容忍的回执错误文案（协议契约固定值）。 */
    public static final String UNKNOWN_FRAME_TYPE = "unknown frame type";

    private Frames() {}

    public static Frame helloAckOk(String id, String serverId, String serverVersion, List<String> channelBindings) {
        ObjectNode body = Json.MAPPER.createObjectNode();
        body.put("ok", true);
        body.put("serverId", serverId);
        body.put("version", serverVersion);
        body.put("protocolVersion", ProtocolVersions.PROTOCOL_VERSION);
        body.set("channelBindings", stringArray(channelBindings));
        return new Frame("hello_ack", id, header("hello_ack", id), body);
    }

    public static Frame helloAckError(String id, String reason) {
        ObjectNode body = Json.MAPPER.createObjectNode();
        body.put("ok", false);
        body.put("reason", reason);
        return new Frame("hello_ack", id, header("hello_ack", id), body);
    }

    public static Frame pong(String id, long timestamp) {
        ObjectNode body = Json.MAPPER.createObjectNode();
        body.put("timestamp", timestamp);
        return new Frame("pong", id, header("pong", id), body);
    }

    public static Frame gameChat(GameChatBody chat) {
        ObjectNode body = Json.MAPPER.createObjectNode();
        body.put("channel", chat.channel());
        body.put("playerName", chat.playerName());
        body.put("content", chat.content());
        return event("chat", body);
    }

    public static Frame join(JoinBody join) {
        ObjectNode body = Json.MAPPER.createObjectNode();
        body.put("channel", join.channel());
        body.put("playerName", join.playerName());
        return event("join", body);
    }

    public static Frame leave(LeaveBody leave) {
        ObjectNode body = Json.MAPPER.createObjectNode();
        body.put("channel", leave.channel());
        body.put("playerName", leave.playerName());
        return event("leave", body);
    }

    public static Frame death(DeathBody death) {
        ObjectNode body = Json.MAPPER.createObjectNode();
        body.put("channel", death.channel());
        body.put("player", death.player());
        body.put("message", death.message());
        return event("death", body);
    }

    public static Frame status(StatusBody status) {
        ObjectNode body = Json.MAPPER.createObjectNode();
        body.set("tps", tpsNode(status.tps()));
        body.put("onlinePlayers", status.onlinePlayers());
        body.put("uptimeSeconds", status.uptimeSeconds());
        return event("status", body);
    }

    public static Frame bindingsUpdated(BindingsUpdatedBody updated) {
        ObjectNode body = Json.MAPPER.createObjectNode();
        body.set("channelBindings", stringArray(updated.channelBindings()));
        return event("bindings_updated", body);
    }

    public static Frame commandResult(String id, CommandResultBody result) {
        ObjectNode body = Json.MAPPER.createObjectNode();
        body.put("ok", result.ok());
        if (result.ok()) {
            if (result.output() != null) {
                body.set("output", stringArray(result.output()));
            }
        } else {
            body.put("error", result.error());
        }
        return new Frame("command_result", id, header("command_result", id), body);
    }

    public static Frame queryResult(String id, QueryResultBody result) {
        ObjectNode body = Json.MAPPER.createObjectNode();
        body.put("ok", result.ok());
        if (result.ok()) {
            body.set("data", result.data());
        } else {
            body.put("error", result.error());
        }
        return new Frame("query_result", id, header("query_result", id), body);
    }

    /** 未知请求帧的回执：同 id 的 `<type>_result` {ok:false, error:"unknown frame type"}。 */
    public static Frame unknownFrameResult(String type, String id) {
        ObjectNode body = Json.MAPPER.createObjectNode();
        body.put("ok", false);
        body.put("error", UNKNOWN_FRAME_TYPE);
        String resultType = type + "_result";
        return new Frame(resultType, id, header(resultType, id), body);
    }

    /** status 的 data 形状（query kind=status 与 status 事件同构）。 */
    public static JsonNode statusNode(StatusBody status) {
        ObjectNode node = Json.MAPPER.createObjectNode();
        node.set("tps", tpsNode(status.tps()));
        node.put("onlinePlayers", status.onlinePlayers());
        node.put("uptimeSeconds", status.uptimeSeconds());
        return node;
    }

    /** 字符串数组节点（query kind=bindings 的 data 形状）。 */
    public static JsonNode stringList(List<String> values) {
        return stringArray(values);
    }

    /** tps 序列化：数学上整数的 double 落为整数（对齐 TS JSON.stringify(20) → "20"）。 */
    private static JsonNode tpsNode(double tps) {
        if (tps == Math.rint(tps) && Math.abs(tps) <= Long.MAX_VALUE) {
            return Json.MAPPER.getNodeFactory().numberNode((long) tps);
        }
        return Json.MAPPER.getNodeFactory().numberNode(tps);
    }

    private static ArrayNode stringArray(List<String> values) {
        ArrayNode array = Json.MAPPER.createArrayNode();
        values.forEach(array::add);
        return array;
    }

    private static Frame event(String type, ObjectNode body) {
        return new Frame(type, null, header(type, null), body);
    }

    private static ObjectNode header(String type, String id) {
        ObjectNode header = Json.MAPPER.createObjectNode();
        header.put("type", type);
        if (id != null) {
            header.put("id", id);
        }
        return header;
    }
}
