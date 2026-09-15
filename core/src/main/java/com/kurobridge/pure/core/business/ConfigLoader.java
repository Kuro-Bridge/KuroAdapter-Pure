// 配置加载（Jackson）：多余字段剥离、缺字段补缺省、非法值抛类型化 ConfigException
package com.kurobridge.pure.core.business;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置解析（纯函数）。行为对齐主仓 bridge/core/src/business/config.ts 的 parseConfig
 * （剔除 runtime/embedded 段）：channels/admins 各自去重保序；非法形状/内容抛 ConfigException。
 */
public final class ConfigLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String EXPECTED_SHAPE =
            "配置不合法（期望 { channels: string[], token?: string, admins?: {channel, users}[], ws?: { host?: string, port?: 1-65535 } }）";

    private ConfigLoader() {}

    /** 从文件加载；文件读不出/解析失败抛 ConfigException。 */
    public static PureConfig load(Path file) {
        String json;
        try {
            json = Files.readString(file);
        } catch (IOException unreadable) {
            throw new ConfigException(String.format("配置文件读取失败：%s", file), unreadable);
        }
        return parse(json);
    }

    /** 解析 JSON 文本；非法 JSON 抛 ConfigException。 */
    public static PureConfig parse(String json) {
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (JsonProcessingException invalid) {
            throw new ConfigException(EXPECTED_SHAPE, invalid);
        }
        return parse(root);
    }

    /** 解析配置树（根必须为 object，null/数组/标量均拒绝）。 */
    public static PureConfig parse(JsonNode root) {
        if (root == null || !root.isObject()) {
            throw new ConfigException(EXPECTED_SHAPE);
        }
        List<String> channels = parseChannels(root.get("channels"));
        String token = parseToken(root.get("token"));
        List<PureConfig.AdminMapping> admins = parseAdmins(root.get("admins"));
        PureConfig.WsListen ws = parseWs(root.get("ws"));
        return new PureConfig(channels, token, admins, ws);
    }

    private static List<String> parseChannels(JsonNode node) {
        if (node == null || !node.isArray()) {
            throw new ConfigException("channels 必须是字符串数组（必填，可空）");
        }
        List<String> channels = new ArrayList<>(node.size());
        for (JsonNode element : node) {
            if (!element.isTextual() || element.asText().isEmpty()) {
                throw new ConfigException("channels 的元素必须是非空字符串");
            }
            if (!channels.contains(element.asText())) {
                channels.add(element.asText()); // 去重保序
            }
        }
        return channels;
    }

    private static String parseToken(JsonNode node) {
        if (node == null || node.isNull()) {
            return ""; // 缺省 = 不鉴权（向后兼容）
        }
        if (!node.isTextual()) {
            throw new ConfigException("token 必须是字符串");
        }
        return node.asText();
    }

    private static List<PureConfig.AdminMapping> parseAdmins(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of(); // 缺省 = 无人可经 command 执行命令
        }
        if (!node.isArray()) {
            throw new ConfigException("admins 必须是 {channel, users} 数组");
        }
        Map<String, PureConfig.AdminMapping> byChannel = new LinkedHashMap<>(); // channel 去重保序（保留首个）
        for (JsonNode element : node) {
            if (!element.isObject()) {
                throw new ConfigException("admins 的条目必须是 {channel, users}");
            }
            JsonNode channelNode = element.get("channel");
            if (channelNode == null
                    || !channelNode.isTextual()
                    || channelNode.asText().isEmpty()) {
                throw new ConfigException("admins[].channel 必须是非空字符串");
            }
            JsonNode usersNode = element.get("users");
            if (usersNode == null || !usersNode.isArray()) {
                throw new ConfigException("admins[].users 必须是字符串数组");
            }
            List<String> users = new ArrayList<>(usersNode.size());
            for (JsonNode user : usersNode) {
                if (!user.isTextual() || user.asText().isEmpty()) {
                    throw new ConfigException("admins[].users 的元素必须是非空字符串");
                }
                if (!users.contains(user.asText())) {
                    users.add(user.asText()); // users 去重保序
                }
            }
            byChannel.putIfAbsent(channelNode.asText(), new PureConfig.AdminMapping(channelNode.asText(), users));
        }
        return List.copyOf(byChannel.values());
    }

    private static PureConfig.WsListen parseWs(JsonNode node) {
        if (node == null || node.isNull()) {
            return null; // 整段缺省 = 动态端口 + 全部接口
        }
        if (!node.isObject()) {
            throw new ConfigException("ws 必须是 { host?: string, port?: 1-65535 }");
        }
        String host = null;
        JsonNode hostNode = node.get("host");
        if (hostNode != null && !hostNode.isNull()) {
            if (!hostNode.isTextual() || hostNode.asText().isEmpty()) {
                throw new ConfigException("ws.host 必须是非空字符串");
            }
            host = hostNode.asText();
        }
        Integer port = null;
        JsonNode portNode = node.get("port");
        if (portNode != null && !portNode.isNull()) {
            // Jackson canConvertToLong 对浮点恒真，数学整性须自判（25580.5 非法、25580.0 合法）
            boolean integral = portNode.isIntegralNumber()
                    || portNode.isFloatingPointNumber()
                            && !Double.isInfinite(portNode.doubleValue())
                            && portNode.doubleValue() == Math.rint(portNode.doubleValue());
            if (!portNode.isNumber() || !integral) {
                throw new ConfigException("ws.port 必须是 1-65535 的整数");
            }
            long value = portNode.longValue();
            if (value < 1 || value > 65535) {
                throw new ConfigException("ws.port 必须是 1-65535 的整数");
            }
            port = (int) value;
        }
        return new PureConfig.WsListen(host, port); // 只配 host 不配 port = 动态端口 + 指定地址（合法）
    }
}
