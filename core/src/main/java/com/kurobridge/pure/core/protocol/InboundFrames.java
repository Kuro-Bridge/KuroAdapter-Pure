// 收帧集（Peer→Server）的具体帧型校验（两段式第二段）——已知 type 的分界清单 + 逐帧解析
package com.kurobridge.pure.core.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import com.kurobridge.pure.core.protocol.message.CommandBody;
import com.kurobridge.pure.core.protocol.message.CommandSource;
import com.kurobridge.pure.core.protocol.message.HelloBody;
import com.kurobridge.pure.core.protocol.message.PlatformChatBody;
import java.util.Set;

/**
 * 已知收帧 type = hello / ping / chat / command / query（对齐 KuroProtocol WS_INBOUND_TYPES）。
 * 每个解析方法做完整第二段校验（请求帧 id 必填 / 事件帧严格 header + body 字段约束），
 * 失败抛 FrameValidationException。
 */
public final class InboundFrames {

    /** 服务端已知收帧 type 清单（两段式解析的「已知/未知」分界）。 */
    public static final Set<String> KNOWN_TYPES = Set.of("hello", "ping", "chat", "command", "query");

    private InboundFrames() {}

    /** 按已知 type 分发解析（金样本门禁用）；未知 type 抛 FrameValidationException。 */
    public static void validate(Frame frame) {
        switch (frame.type()) {
            case "hello" -> hello(frame);
            case "ping" -> ping(frame);
            case "chat" -> platformChat(frame);
            case "command" -> command(frame);
            case "query" -> query(frame);
            default -> throw new FrameValidationException(String.format("未知收帧 type：%s", frame.type()));
        }
    }

    public static HelloBody hello(Frame frame) {
        requireType(frame, "hello");
        frame.requireRequestId();
        JsonNode body = JsonValidations.requireObject(frame.body());
        return new HelloBody(
                JsonValidations.requireNonEmptyString(body, "peerId"),
                JsonValidations.requireNonEmptyString(body, "platform"),
                JsonValidations.requireNonEmptyString(body, "version"),
                JsonValidations.requireSemver(body, "protocolVersion"),
                JsonValidations.optionalString(body, "token"),
                JsonValidations.optionalString(body, "client"));
    }

    public static long ping(Frame frame) {
        requireType(frame, "ping");
        frame.requireRequestId();
        return JsonValidations.requireNonNegativeLong(JsonValidations.requireObject(frame.body()), "timestamp");
    }

    public static PlatformChatBody platformChat(Frame frame) {
        requireType(frame, "chat");
        frame.requireEventHeader();
        JsonNode body = JsonValidations.requireObject(frame.body());
        return new PlatformChatBody(
                JsonValidations.requireNonEmptyString(body, "channel"),
                JsonValidations.requireNonEmptyString(body, "sender"),
                JsonValidations.requireNonEmptyString(body, "content"));
    }

    public static CommandBody command(Frame frame) {
        requireType(frame, "command");
        frame.requireRequestId();
        JsonNode body = JsonValidations.requireObject(frame.body());
        String command = JsonValidations.requireNonEmptyString(body, "command");
        if (command.startsWith("/")) {
            throw new FrameValidationException("command 不得含前导斜杠（不含 \"/\"，如 \"whitelist list\"）");
        }
        JsonNode source = JsonValidations.requireObjectField(body, "source");
        return new CommandBody(
                command,
                new CommandSource(
                        JsonValidations.requireNonEmptyString(source, "channel"),
                        JsonValidations.requireNonEmptyString(source, "userId")));
    }

    public static String query(Frame frame) {
        requireType(frame, "query");
        frame.requireRequestId();
        JsonNode body = JsonValidations.requireObject(frame.body());
        String kind = JsonValidations.requireNonEmptyString(body, "kind");
        if (!"status".equals(kind) && !"bindings".equals(kind)) {
            throw new FrameValidationException(String.format("query.kind 必须是 status 或 bindings：%s", kind));
        }
        return kind;
    }

    private static void requireType(Frame frame, String expected) {
        if (!expected.equals(frame.type())) {
            throw new FrameValidationException(String.format("期望帧 %s，实际 %s", expected, frame.type()));
        }
    }
}
