// hello_ack 响应体（ok 体携带服务端身份与绑定快照；error 体携带拒绝原因）
package com.kurobridge.pure.core.protocol.message;

import java.util.List;
import java.util.Objects;

/** 握手结果 body（服务端身份信息并入 body）。 */
public interface HelloAckBody {

    /** ok 体：serverId/version 非空、protocolVersion 三元组、channelBindings 完整快照（空数组合法）。 */
    final class Ok implements HelloAckBody {

        private final String serverId;
        private final String version;
        private final String protocolVersion;
        private final List<String> channelBindings;

        public Ok(String serverId, String version, String protocolVersion, List<String> channelBindings) {
            this.serverId = serverId;
            this.version = version;
            this.protocolVersion = protocolVersion;
            this.channelBindings = channelBindings;
        }

        public String serverId() {
            return serverId;
        }

        public String version() {
            return version;
        }

        public String protocolVersion() {
            return protocolVersion;
        }

        public List<String> channelBindings() {
            return channelBindings;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Ok)) {
                return false;
            }
            Ok other = (Ok) obj;
            return Objects.equals(serverId, other.serverId)
                    && Objects.equals(version, other.version)
                    && Objects.equals(protocolVersion, other.protocolVersion)
                    && Objects.equals(channelBindings, other.channelBindings);
        }

        @Override
        public int hashCode() {
            return Objects.hash(serverId, version, protocolVersion, channelBindings);
        }

        @Override
        public String toString() {
            return "Ok[serverId=" + serverId + ", version=" + version + ", protocolVersion=" + protocolVersion
                    + ", channelBindings=" + channelBindings + "]";
        }
    }

    /** error 体：reason 非空，配 close 1002/1008。 */
    final class Error implements HelloAckBody {

        private final String reason;

        public Error(String reason) {
            this.reason = reason;
        }

        public String reason() {
            return reason;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Error)) {
                return false;
            }
            Error other = (Error) obj;
            return Objects.equals(reason, other.reason);
        }

        @Override
        public int hashCode() {
            return Objects.hash(reason);
        }

        @Override
        public String toString() {
            return "Error[reason=" + reason + "]";
        }
    }
}
