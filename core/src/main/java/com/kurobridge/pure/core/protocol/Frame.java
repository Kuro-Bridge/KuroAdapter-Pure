// 帧模型：线格式 {"header":{"type","id?"},"body"} 的内存表示（事件帧 id 为 null）
package com.kurobridge.pure.core.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.regex.Pattern;

/**
 * 解析后的帧。header 原样保留（事件帧的严格 header 校验需要检视其键集）；
 * encode 出帧时只使用 type/id，header 里的未知字段不透传。
 */
public record Frame(String type, String id, JsonNode header, JsonNode body) {

    /** 帧名约束：全小写 snake_case（对齐 zod TYPE_PATTERN）。 */
    public static final Pattern TYPE_PATTERN = Pattern.compile("^[a-z][a-z0-9_]*$");

    /** canonical UUID（8-4-4-4-12；Java UUID.fromString 接受无横线串，故用正则对齐 z.uuid()）。 */
    public static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    /** 请求/响应帧形态断言：id 必填（UUID 格式已由 wire 段保证）。 */
    public void requireRequestId() {
        if (id == null) {
            throw new FrameValidationException(String.format("帧 %s 是请求/响应帧，header.id 必填", type));
        }
    }

    /** 事件帧形态断言：header 必须只含 type 一个键（对齐 zod strictObject——携带 id 或多余键即拒）。 */
    public void requireEventHeader() {
        if (id != null) {
            throw new FrameValidationException(String.format("帧 %s 是事件帧，不得携带 id", type));
        }
        if (header.size() != 1) {
            throw new FrameValidationException(String.format("帧 %s 是事件帧，header 只允许 type 键", type));
        }
    }
}
