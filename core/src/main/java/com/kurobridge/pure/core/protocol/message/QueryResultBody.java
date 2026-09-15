// query_result 响应体（data 形状由请求 kind 决定：status → StatusBody 同构；bindings → string[]）
package com.kurobridge.pure.core.protocol.message;

import com.fasterxml.jackson.databind.JsonNode;

/** 查询结果 body：ok 体 data 任意（协议层不强校验）；error 体 error 非空。 */
public record QueryResultBody(boolean ok, JsonNode data, String error) {

    public static QueryResultBody success(JsonNode data) {
        return new QueryResultBody(true, data, null);
    }

    public static QueryResultBody failure(String error) {
        return new QueryResultBody(false, null, error);
    }
}
