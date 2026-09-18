// 命令执行器：CONSOLE 名义 dispatchCommand + 收集输出（onCommand 与 WhitelistGateway 共用机制）
package com.kurobridge.pure.paper;

import com.kurobridge.pure.core.protocol.message.CommandResultBody;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandException;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 命令执行机制（主仓 NodeRequestHandler.onExecuteCommand 的主仓→纯 Java 移植）：收集型
 * sender 承接 Bukkit 命令路径的回显；vanilla 命令（Paper 的 VanillaCommandWrapper 只认内部
 * Craft* sender）抛 listener 拒绝异常时回退真实 console sender，输出经 log4j appender 收集。
 *
 * <p>线程契约：仅在 Bukkit 主线程调用（onCommand 经 BukkitBusinessScheduler 派发后已在内）。
 * 执行完命令才组结果（主线程同步完成 = 对端拿到的是真实执行结果，对齐主仓 v0.3.0 语义）。
 * 命令无前导斜杠是**对端约定**（peer-guide §5.1），本层不校验不剥除。
 */
public final class PaperCommandDispatcher {

    private final JavaPlugin plugin;

    public PaperCommandDispatcher(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** 执行一条命令（CONSOLE 名义），返回 ok（含 output 行）或 error 体。 */
    public CommandResultBody execute(String command) {
        CollectingCommandSender sender = new CollectingCommandSender();
        VanillaFeedbackCapture capture = new VanillaFeedbackCapture();
        try {
            capture.attach();
            try {
                Bukkit.dispatchCommand(sender, command);
            } catch (CommandException commandRejected) {
                if (!isVanillaListenerRejection(commandRejected)) {
                    throw commandRejected;
                }
                // Paper 的 VanillaCommandWrapper 拒绝自定义 sender：vanilla 命令回退真实
                // console sender 执行，输出经 log4j 控制台流由 capture 收集
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            }
        } catch (RuntimeException executionFailed) {
            // 命令本身抛异常（插件命令 bug 等）：显式 error 回执，避免对端等到超时
            plugin.getLogger().log(Level.WARNING, "command 执行异常：" + command, executionFailed);
            return CommandResultBody.failure("命令执行异常：" + executionFailed.getMessage());
        } finally {
            capture.detach();
        }
        List<String> output = sender.collectedLines();
        if (output.isEmpty()) {
            output = capture.collectedLines();
        }
        return CommandResultBody.success(output);
    }

    /** 是否为 VanillaCommandWrapper.getListener 拒绝自定义 sender（回退 console 的信号）。 */
    private static boolean isVanillaListenerRejection(CommandException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof IllegalArgumentException
                    && String.valueOf(cause.getMessage()).contains("a vanilla command listener")) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
