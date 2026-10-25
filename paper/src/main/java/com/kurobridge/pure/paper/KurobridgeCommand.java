// /kurobridge 命令壳：plugin.yml 声明命令（经典注册），reload 逻辑在插件主类
package com.kurobridge.pure.paper;

import com.kurobridge.pure.KuroBridgePurePlugin;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

/**
 * /kurobridge 命令（现仅 reload 子命令）。
 *
 * <p>注册方式（对齐主仓 ADR-038 universal 化）：经典 plugin.yml 的 commands 块声明
 * （全版本可用，1.12.2 ~ 26.3），主类 onEnable 经 getCommand(...).setExecutor/setTabCompleter
 * 挂载本类。权限 kurobridge.reload 在 plugin.yml 的 commands 块声明（default op，控制台恒可），
 * 旧版 CraftBukkit 对 commands 块 permission 的执行口径不一，execute 里显式复查兜底。
 * onCommand 由 Bukkit 主线程同步派发（重载全程主线程安全）。
 */
public final class KurobridgeCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "kurobridge.reload";
    private static final String USAGE = "用法：/kurobridge reload（重载配置：绑定频道/管理员表热更；token/ws/server.id 待重启生效）";

    private final KuroBridgePurePlugin plugin;

    public KurobridgeCommand(KuroBridgePurePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage("缺少 kurobridge.reload 权限");
            return true;
        }
        if (args.length != 1 || !"reload".equals(args[0])) {
            sender.sendMessage(USAGE);
            return true;
        }
        plugin.performKurobridgeReload(sender);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            if ("reload".startsWith(prefix)) {
                return Collections.singletonList("reload");
            }
        }
        return Collections.emptyList();
    }
}
