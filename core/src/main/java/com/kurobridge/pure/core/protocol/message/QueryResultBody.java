// query_result 响应体（data 形状由请求 kind 决定：status → StatusBody 同构；bindings → string[]）
package com.kurobridge.pure.core.protocol.message;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;

/** 查询结果 body：ok 体 data 任意（协议层不强校验）；error 体 error 非空。 */
public final class QueryResultBody {

    private final boolean ok;
    private final JsonNode data;
    private final String error;

    public QueryResultBody(boolean ok, JsonNode data, String error) {
        this.ok = ok;
        this.data = data;
        this.error = error;
    }

    public static QueryResultBody success(JsonNode data) {
        return new QueryResultBody(true, data, null);
    }

    public static QueryResultBody failure(String error) {
        return new QueryResultBody(false, null, error);
    }

    public boolean ok() {
        return ok;
    }

    public JsonNode data() {
        return data;
    }

    public String error() {
        return error;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof QueryResultBody)) {
            return false;
        }
        QueryResultBody other = (QueryResultBody) obj;
        return ok == other.ok && Objects.equals(data, other.data) && Objects.equals(error, other.error);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ok, data, error);
    }

    @Override
    public String toString() {
        return "QueryResultBody[ok=" + ok + ", data=" + data + ", error=" + error + "]";
    }
}
