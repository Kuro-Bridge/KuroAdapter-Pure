// protocol 包共享的 Jackson mapper（树模型专用；未知字段剥离语义由「只取已知键」实现）
package com.kurobridge.pure.core.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;

final class Json {

    static final ObjectMapper MAPPER = new ObjectMapper();

    private Json() {}
}
