// BusinessScheduler 的 Paper 实现：BukkitScheduler 主线程投递（Bukkit API 非线程安全）
package com.kurobridge.pure.paper;

import com.kurobridge.pure.core.server.BusinessScheduler;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 主线程投递实现（替换测试用的直执行缺省；对齐主仓 NodeRequestHandler 的 runTask 包装）。
 * 回调已在主线程上执行，实现里可直用 Bukkit API。
 */
public final class BukkitBusinessScheduler implements BusinessScheduler {

    private final JavaPlugin plugin;

    public BukkitBusinessScheduler(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void dispatch(Runnable task) {
        try {
            Bukkit.getScheduler().runTask(plugin, task);
        } catch (RuntimeException schedulingFailed) {
            // 插件已 disable 等导致调度必抛 IllegalPluginAccessException（主仓同因同果）：
            // warn + 丢弃。disable 窗口内 onDisable 先行 close 1001，等待回执的对端由其
            // 自身 10s 超时兜底（peer-guide §5.1），服务端无悬挂义务（设计书 §3.1）。
            plugin.getLogger().log(Level.WARNING, "调度业务回调到主线程失败（插件已 disable？），丢弃", schedulingFailed);
        }
    }
}
