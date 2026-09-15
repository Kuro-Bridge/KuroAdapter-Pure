// hello 请求体（对端注册；token/client 可选）
package com.kurobridge.pure.core.protocol.message;

/** hello body：peerId/platform/version/protocolVersion 必填非空，token（鉴权）与 client（自报身份）可选。 */
public record HelloBody(
        String peerId, String platform, String version, String protocolVersion, String token, String client) {}
