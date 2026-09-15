// 绑定表变更推送事件体（变更后完整列表，非增量）
package com.kurobridge.pure.core.protocol.message;

import java.util.List;

/** 绑定变更 body：channelBindings 为非空串数组（完整列表）。 */
public record BindingsUpdatedBody(List<String> channelBindings) {}
