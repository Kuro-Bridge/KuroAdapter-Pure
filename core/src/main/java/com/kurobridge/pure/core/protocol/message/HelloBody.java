// hello 请求体（对端注册；token/client 可选）
package com.kurobridge.pure.core.protocol.message;

import java.util.Objects;

/** hello body：peerId/platform/version/protocolVersion 必填非空，token（鉴权）与 client（自报身份）可选。 */
public final class HelloBody {

    private final String peerId;
    private final String platform;
    private final String version;
    private final String protocolVersion;
    private final String token;
    private final String client;

    public HelloBody(
            String peerId, String platform, String version, String protocolVersion, String token, String client) {
        this.peerId = peerId;
        this.platform = platform;
        this.version = version;
        this.protocolVersion = protocolVersion;
        this.token = token;
        this.client = client;
    }

    public String peerId() {
        return peerId;
    }

    public String platform() {
        return platform;
    }

    public String version() {
        return version;
    }

    public String protocolVersion() {
        return protocolVersion;
    }

    public String token() {
        return token;
    }

    public String client() {
        return client;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof HelloBody)) {
            return false;
        }
        HelloBody other = (HelloBody) obj;
        return Objects.equals(peerId, other.peerId)
                && Objects.equals(platform, other.platform)
                && Objects.equals(version, other.version)
                && Objects.equals(protocolVersion, other.protocolVersion)
                && Objects.equals(token, other.token)
                && Objects.equals(client, other.client);
    }

    @Override
    public int hashCode() {
        return Objects.hash(peerId, platform, version, protocolVersion, token, client);
    }

    @Override
    public String toString() {
        return "HelloBody[peerId=" + peerId + ", platform=" + platform + ", version=" + version + ", protocolVersion="
                + protocolVersion + ", token=" + token + ", client=" + client + "]";
    }
}
