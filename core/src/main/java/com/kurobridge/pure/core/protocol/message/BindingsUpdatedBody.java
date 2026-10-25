// 绑定表变更推送事件体（变更后完整列表，非增量）
package com.kurobridge.pure.core.protocol.message;

import java.util.List;
import java.util.Objects;

/** 绑定变更 body：channelBindings 为非空串数组（完整列表）。 */
public final class BindingsUpdatedBody {

    private final List<String> channelBindings;

    public BindingsUpdatedBody(List<String> channelBindings) {
        this.channelBindings = channelBindings;
    }

    public List<String> channelBindings() {
        return channelBindings;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof BindingsUpdatedBody)) {
            return false;
        }
        BindingsUpdatedBody other = (BindingsUpdatedBody) obj;
        return Objects.equals(channelBindings, other.channelBindings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(channelBindings);
    }

    @Override
    public String toString() {
        return "BindingsUpdatedBody[channelBindings=" + channelBindings + "]";
    }
}
