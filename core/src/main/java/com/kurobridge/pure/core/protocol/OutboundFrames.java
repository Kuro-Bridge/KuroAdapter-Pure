// 出帧集（Server→Peer）的具体帧型校验（两段式第二段）——金样本门禁 / 回帧自检用
package com.kurobridge.pure.core.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.kurobridge.pure.core.protocol.message.BindingsUpdatedBody;
import com.kurobridge.pure.core.protocol.message.CommandResultBody;
import com.kurobridge.pure.core.protocol.message.DeathBody;
import com.kurobridge.pure.core.protocol.message.GameChatBody;
import com.kurobridge.pure.core.protocol.message.HelloAckBody;
import com.kurobridge.pure.core.protocol.message.JoinBody;
import com.kurobridge.pure.core.protocol.message.LeaveBody;
import com.kurobridge.pure.core.protocol.message.QueryResultBody;
import com.kurobridge.pure.core.protocol.message.StatusBody;
import java.util.Set;

/**
 * 出帧集校验（协议端视角的收帧集）。协议出帧在服务端侧主要由 Frames 工厂构建（类型安全），
 * 本类用于：金样本夹具按 dir=server->peer 消费、以及未来回环/录制回放的自检。
 */
public final class OutboundFrames {

    /** 出帧已知 type 清单（hello_ack/pong/chat/join/leave/death/status/bindings_updated/command_result/query_result）。 */
    public static final Set<String> KNOWN_TYPES = Set.of(
            "hello_ack",
            "pong",
            "chat",
            "join",
            "leave",
            "death",
            "status",
            "bindings_updated",
            "command_result",
            "query_result");

    private OutboundFrames() {}

    /** 按已知 type 分发解析（金样本门禁用）；未知 type 抛 FrameValidationException。 */
    public static void validate(Frame frame) {
        switch (frame.type()) {
            case "hello_ack" -> helloAck(frame);
            case "pong" -> pong(frame);
            case "chat" -> gameChat(frame);
            case "join" -> join(frame);
            case "leave" -> leave(frame);
            case "death" -> death(frame);
            case "status" -> status(frame);
            case "bindings_updated" -> bindingsUpdated(frame);
            case "command_result" -> commandResult(frame);
            case "query_result" -> queryResult(frame);
            default -> throw new FrameValidationException(String.format("未知出帧 type：%s", frame.type()));
        }
    }

    public static HelloAckBody helloAck(Frame frame) {
        requireType(frame, "hello_ack");
        frame.requireRequestId();
        JsonNode body = JsonValidations.requireObject(frame.body());
        boolean ok = JsonValidations.requireBooleanFlag(body, "ok");
        if (!ok) {
            return new HelloAckBody.Error(JsonValidations.requireNonEmptyString(body, "reason"));
        }
        return new HelloAckBody.Ok(
                JsonValidations.requireNonEmptyString(body, "serverId"),
                JsonValidations.requireNonEmptyString(body, "version"),
                JsonValidations.requireSemver(body, "protocolVersion"),
                JsonValidations.requireNonEmptyStringArray(body, "channelBindings"));
    }

    public static long pong(Frame frame) {
        requireType(frame, "pong");
        frame.requireRequestId();
        return JsonValidations.requireNonNegativeLong(JsonValidations.requireObject(frame.body()), "timestamp");
    }

    public static GameChatBody gameChat(Frame frame) {
        requireType(frame, "chat");
        frame.requireEventHeader();
        JsonNode body = JsonValidations.requireObject(frame.body());
        return new GameChatBody(
                JsonValidations.requireNonEmptyString(body, "channel"),
                JsonValidations.requireNonEmptyString(body, "playerName"),
                JsonValidations.requireNonEmptyString(body, "content"));
    }

    public static JoinBody join(Frame frame) {
        requireType(frame, "join");
        frame.requireEventHeader();
        JsonNode body = JsonValidations.requireObject(frame.body());
        return new JoinBody(
                JsonValidations.requireNonEmptyString(body, "channel"),
                JsonValidations.requireNonEmptyString(body, "playerName"));
    }

    public static LeaveBody leave(Frame frame) {
        requireType(frame, "leave");
        frame.requireEventHeader();
        JsonNode body = JsonValidations.requireObject(frame.body());
        return new LeaveBody(
                JsonValidations.requireNonEmptyString(body, "channel"),
                JsonValidations.requireNonEmptyString(body, "playerName"));
    }

    public static DeathBody death(Frame frame) {
        requireType(frame, "death");
        frame.requireEventHeader();
        JsonNode body = JsonValidations.requireObject(frame.body());
        return new DeathBody(
                JsonValidations.requireNonEmptyString(body, "channel"),
                JsonValidations.requireNonEmptyString(body, "player"),
                JsonValidations.requireString(body, "message"));
    }

    public static StatusBody status(Frame frame) {
        requireType(frame, "status");
        frame.requireEventHeader();
        JsonNode body = JsonValidations.requireObject(frame.body());
        return new StatusBody(
                JsonValidations.requireNonNegativeDouble(body, "tps"),
                JsonValidations.requireNonNegativeLong(body, "onlinePlayers"),
                JsonValidations.requireNonNegativeLong(body, "uptimeSeconds"));
    }

    public static BindingsUpdatedBody bindingsUpdated(Frame frame) {
        requireType(frame, "bindings_updated");
        frame.requireEventHeader();
        JsonNode body = JsonValidations.requireObject(frame.body());
        return new BindingsUpdatedBody(JsonValidations.requireNonEmptyStringArray(body, "channelBindings"));
    }

    public static CommandResultBody commandResult(Frame frame) {
        requireType(frame, "command_result");
        frame.requireRequestId();
        JsonNode body = JsonValidations.requireObject(frame.body());
        boolean ok = JsonValidations.requireBooleanFlag(body, "ok");
        if (!ok) {
            return CommandResultBody.failure(JsonValidations.requireNonEmptyString(body, "error"));
        }
        return CommandResultBody.success(JsonValidations.optionalStringArray(body, "output"));
    }

    public static QueryResultBody queryResult(Frame frame) {
        requireType(frame, "query_result");
        frame.requireRequestId();
        JsonNode body = JsonValidations.requireObject(frame.body());
        boolean ok = JsonValidations.requireBooleanFlag(body, "ok");
        if (!ok) {
            return QueryResultBody.failure(JsonValidations.requireNonEmptyString(body, "error"));
        }
        return QueryResultBody.success(body.get("data"));
    }

    private static void requireType(Frame frame, String expected) {
        if (!expected.equals(frame.type())) {
            throw new FrameValidationException(String.format("期望帧 %s，实际 %s", expected, frame.type()));
        }
    }
}
