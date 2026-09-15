// :paper 插件主类：生命周期薄壳（协议层与业务骨架在 :core，本模块零协议逻辑）
package com.kurobridge.pure;

import org.bukkit.plugin.java.JavaPlugin;

/** KuroBridgePure 插件入口（事件接线、WS 启停见下一阶段）。 */
public final class KuroBridgePurePlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        // 下一阶段：加载配置（ConfigLoader）、启动 PureWsServer、接线 Bukkit 事件与
        // 主线程 BusinessScheduler
    }

    @Override
    public void onDisable() {
        // 下一阶段：向已握手对端 close 1001 "server shutdown"、停 PureWsServer
    }
}
