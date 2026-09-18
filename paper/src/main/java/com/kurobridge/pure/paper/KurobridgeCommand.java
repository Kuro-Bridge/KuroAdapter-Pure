// /kurobridge 命令壳：Lifecycle/Brigadier 注册（paper-plugin.yml 不支持 commands 块），逻辑在插件主类
package com.kurobridge.pure.paper;

import com.kurobridge.pure.KuroBridgePurePlugin;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.CommandSender;

/**
 * /kurobridge 命令（现仅 reload 子命令）。
 *
 * <p>注册方式（实装期核实）：Paper 1.21.4 的 paper-plugin.yml 不支持 commands 块
 * （io.papermc.paper.plugin.configuration.PluginMeta 仅有 permissions 字段），故按 Paper
 * 规范经 LifecycleEvents.COMMANDS + {@link BasicCommand} 注册（注册器由插件主类在 onEnable
 * 挂载，COMMANDS 事件在全部插件启用完成后触发、重注册幂等）。权限 kurobridge.reload 由
 * {@link #permission()} 声明——节点本体在 paper-plugin.yml permissions 块声明 default op，
 * 控制台恒可。execute 由 Brigadier 在主线程同步派发（重载全程主线程安全）。
 */
public final class KurobridgeCommand implements BasicCommand {

    private static final String PERMISSION = "kurobridge.reload";
    private static final String USAGE = "用法：/kurobridge reload（重载配置：绑定频道/管理员表热更；token/ws/server.id 待重启生效）";

    private final KuroBridgePurePlugin plugin;

    public KurobridgeCommand(KuroBridgePurePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        if (args.length != 1 || !"reload".equals(args[0])) {
            sender.sendMessage(USAGE);
            return;
        }
        plugin.performKurobridgeReload(sender);
    }

    @Override
    public List<String> suggest(CommandSourceStack source, String[] args) {
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            if ("reload".startsWith(prefix)) {
                return List.of("reload");
            }
        }
        return List.of();
    }

    @Override
    public String permission() {
        return PERMISSION;
    }
}
