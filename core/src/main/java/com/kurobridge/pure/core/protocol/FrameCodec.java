// 帧编解码：wire 骨架校验（两段式第一段）+ 出帧单行 JSON 编码
package com.kurobridge.pure.core.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 线格式编解码。
 *
 * 第一段（骨架）只约束：根为 object、header 为 object、header.type 匹配 snake_case、
 * header.id 存在时必须 canonical UUID。body 任意（缺失视为 null，由第二段拒绝）。
 * 未知字段不报错（对齐 zod 非严格 object 的剥离语义：只取已知键）。
 */
public final class FrameCodec {

    private FrameCodec() {}

    /** 解析并校验线格式骨架；失败抛 WireFormatException。 */
    public static Frame parseWire(String text) {
        JsonNode root;
        try {
            root = Json.MAPPER.readTree(text);
        } catch (JsonProcessingException invalid) {
            throw new WireFormatException("帧不是合法 JSON", invalid);
        }
        if (root == null || !root.isObject()) {
            throw new WireFormatException("帧根必须是 JSON object");
        }
        JsonNode header = root.get("header");
        if (header == null || !header.isObject()) {
            throw new WireFormatException("header 缺失或不是 object");
        }
        JsonNode typeNode = header.get("type");
        if (typeNode == null || !typeNode.isTextual()) {
            throw new WireFormatException("header.type 必须是字符串");
        }
        String type = typeNode.asText();
        if (!Frame.TYPE_PATTERN.matcher(type).matches()) {
            throw new WireFormatException(String.format("header.type 必须是小写 snake_case：%s", type));
        }
        String id = null;
        if (header.hasNonNull("id")) {
            JsonNode idNode = header.get("id");
            if (!idNode.isTextual()
                    || !Frame.UUID_PATTERN.matcher(idNode.asText()).matches()) {
                throw new WireFormatException("header.id 存在时必须是 canonical UUID");
            }
            id = idNode.asText();
        }
        JsonNode body = root.get("body");
        if (body == null) {
            body = Json.MAPPER.nullNode();
        }
        return new Frame(type, id, header, body);
    }

    /** 出帧编码：单行 JSON 文本（header 只落 type/id，body 原样）。 */
    public static String encode(Frame frame) {
        ObjectNode root = Json.MAPPER.createObjectNode();
        ObjectNode header = root.putObject("header");
        header.put("type", frame.type());
        if (frame.id() != null) {
            header.put("id", frame.id());
        }
        root.set("body", frame.body());
        return root.toString();
    }
}
