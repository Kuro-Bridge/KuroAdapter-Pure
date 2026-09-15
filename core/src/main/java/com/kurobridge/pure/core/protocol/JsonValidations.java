// body 字段级校验助手（对齐 zod 各 schema 的字段约束；未知键一律忽略 = 剥离语义）
package com.kurobridge.pure.core.protocol;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class JsonValidations {

    private static final Pattern SEMVER_PATTERN = Pattern.compile("^\\d+\\.\\d+\\.\\d+$");

    private JsonValidations() {}

    static JsonNode requireObject(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new FrameValidationException("body 必须是 JSON object");
        }
        return body;
    }

    static String requireNonEmptyString(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || !node.isTextual() || node.asText().isEmpty()) {
            throw new FrameValidationException(String.format("%s 必须是非空字符串", field));
        }
        return node.asText();
    }

    /** 必须存在且为字符串（允许空串：death.message——deathMessage 为 null 时空串兜底）。 */
    static String requireString(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || !node.isTextual()) {
            throw new FrameValidationException(String.format("%s 必须是字符串", field));
        }
        return node.asText();
    }

    /** 可选字符串：缺失/null 返回 null；存在但非字符串 = 非法（z.string().optional()）。 */
    static String optionalString(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw new FrameValidationException(String.format("%s 必须是字符串", field));
        }
        return node.asText();
    }

    static String requireSemver(JsonNode body, String field) {
        String value = requireNonEmptyString(body, field);
        if (!SEMVER_PATTERN.matcher(value).matches()) {
            throw new FrameValidationException(String.format("%s 必须形如 主.次.补丁：%s", field, value));
        }
        return value;
    }

    /** 整数 >=0（zod z.number().int().nonnegative()；数学上整数的 1.0 合法、1.5 非法）。 */
    static long requireNonNegativeLong(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || !node.isNumber() || !isIntegral(node) || node.doubleValue() < 0) {
            throw new FrameValidationException(String.format("%s 必须是 >=0 的整数", field));
        }
        return node.longValue();
    }

    /**
     * 数值的数学整性判定（Jackson canConvertToLong 对浮点恒真，不可用）：
     * 整数节点直接合格；浮点节点须为有限且无小数部分（对齐 JS Number.isInteger）。
     */
    private static boolean isIntegral(JsonNode node) {
        if (node.isIntegralNumber()) {
            return true;
        }
        double value = node.doubleValue();
        return !Double.isInfinite(value) && value == Math.rint(value);
    }

    /** 数值 >=0（tps 等允许小数）。 */
    static double requireNonNegativeDouble(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || !node.isNumber() || node.doubleValue() < 0) {
            throw new FrameValidationException(String.format("%s 必须是 >=0 的数值", field));
        }
        return node.doubleValue();
    }

    static boolean requireBooleanFlag(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || !node.isBoolean()) {
            throw new FrameValidationException(String.format("%s 必须是布尔值", field));
        }
        return node.asBoolean();
    }

    /** 非空字符串数组（channelBindings 等，元素 min(1)）。 */
    static List<String> requireNonEmptyStringArray(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || !node.isArray()) {
            throw new FrameValidationException(String.format("%s 必须是字符串数组", field));
        }
        List<String> values = new ArrayList<>(node.size());
        for (JsonNode element : node) {
            if (!element.isTextual() || element.asText().isEmpty()) {
                throw new FrameValidationException(String.format("%s 的元素必须是非空字符串", field));
            }
            values.add(element.asText());
        }
        return values;
    }

    /** 字符串数组（元素允许空串，command_result.output 用）。缺失返回 null。 */
    static List<String> optionalStringArray(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isArray()) {
            throw new FrameValidationException(String.format("%s 必须是字符串数组", field));
        }
        List<String> values = new ArrayList<>(node.size());
        for (JsonNode element : node) {
            if (!element.isTextual()) {
                throw new FrameValidationException(String.format("%s 的元素必须是字符串", field));
            }
            values.add(element.asText());
        }
        return values;
    }

    /** body 子对象（command.source 等）。 */
    static JsonNode requireObjectField(JsonNode body, String field) {
        JsonNode node = body.get(field);
        if (node == null || !node.isObject()) {
            throw new FrameValidationException(String.format("%s 必须是 JSON object", field));
        }
        return node;
    }
}
