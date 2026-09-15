// 测试替身集：手动调度器（确定性推进时钟）/ 假连接 / 收集日志 / 录制 hooks
package com.kurobridge.pure.core.server;

import com.kurobridge.pure.core.protocol.message.CommandBody;
import com.kurobridge.pure.core.protocol.message.CommandResultBody;
import com.kurobridge.pure.core.protocol.message.PlatformChatBody;
import com.kurobridge.pure.core.protocol.message.StatusBody;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

/** 纯逻辑测试替身（等效主仓 clock.ts 的 ManualScheduler：手动推进，零真实等待）。 */
public final class Fakes {

    private Fakes() {}

    /** 手动调度器：advance 推进时钟并触发全部到期任务（回调内再调度亦可）。 */
    public static final class ManualScheduler implements TimeoutScheduler {

        private long now;
        private long nextId;
        private final Map<Long, Task> tasks = new LinkedHashMap<>();

        private record Task(long dueAt, Runnable callback) {}

        private final class Handle implements Cancellable {

            private final long id;

            Handle(long id) {
                this.id = id;
            }

            @Override
            public void cancel() {
                tasks.remove(id);
            }
        }

        @Override
        public Cancellable schedule(long delayMillis, Runnable task) {
            long id = nextId;
            nextId += 1;
            tasks.put(id, new Task(now + Math.max(0, delayMillis), task));
            return new Handle(id);
        }

        public void advance(long millis) {
            now += millis;
            fireDue();
        }

        private void fireDue() {
            boolean fired = true;
            while (fired) {
                fired = false;
                for (Map.Entry<Long, Task> entry : tasks.entrySet()) {
                    if (entry.getValue().dueAt() <= now) {
                        Task task = entry.getValue();
                        tasks.remove(entry.getKey());
                        task.callback().run();
                        fired = true;
                        break; // 回调可能已增删任务，重新遍历保证语义稳定
                    }
                }
            }
        }

        public int pendingCount() {
            return tasks.size();
        }
    }

    /** 假连接：记录全部出帧与 close；send 不抛错。 */
    public static final class FakeConnection implements WsConnection {

        private final List<String> sent = new ArrayList<>();
        private boolean open = true;
        private Integer closedCode;
        private String closedReason;

        @Override
        public void send(String text) {
            sent.add(text);
        }

        @Override
        public void close(int code, String reason) {
            open = false;
            closedCode = code;
            closedReason = reason;
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        public List<String> sent() {
            return List.copyOf(sent);
        }

        public void clearSent() {
            sent.clear();
        }

        public String lastSent() {
            return sent.get(sent.size() - 1);
        }

        public Integer closedCode() {
            return closedCode;
        }

        public String closedReason() {
            return closedReason;
        }

        public boolean wasClosed() {
            return closedCode != null;
        }
    }

    /** 收集日志（断言 warn/debug 行用）。 */
    public static final class CollectingLogger implements KbLogger {

        private final List<String> debugs = new ArrayList<>();
        private final List<String> infos = new ArrayList<>();
        private final List<String> warns = new ArrayList<>();

        @Override
        public void debug(String message) {
            debugs.add(message);
        }

        @Override
        public void info(String message) {
            infos.add(message);
        }

        @Override
        public void warn(String message) {
            warns.add(message);
        }

        @Override
        public void error(String message, Throwable error) {
            warns.add(message + ": " + error);
        }

        public List<String> debugs() {
            return List.copyOf(debugs);
        }

        public List<String> infos() {
            return List.copyOf(infos);
        }

        public List<String> warns() {
            return List.copyOf(warns);
        }
    }

    /** 录制业务回调：chat/command 落账；commandHandler 缺省 null（= 未注册）。 */
    public static final class RecordingHooks implements BusinessHooks {

        private final List<PlatformChatBody> chats = new ArrayList<>();
        private final List<CommandBody> commands = new ArrayList<>();
        private Function<CommandBody, CompletableFuture<CommandResultBody>> commandHandler;
        private Supplier<Optional<StatusBody>> statusSupplier = Optional::empty;

        @Override
        public void onPlatformChat(PlatformChatBody body) {
            chats.add(body);
        }

        @Override
        public CompletableFuture<CommandResultBody> onCommand(CommandBody body) {
            commands.add(body);
            return commandHandler == null ? null : commandHandler.apply(body);
        }

        @Override
        public Optional<StatusBody> latestStatus() {
            return statusSupplier.get();
        }

        public List<PlatformChatBody> chats() {
            return List.copyOf(chats);
        }

        public List<CommandBody> commands() {
            return List.copyOf(commands);
        }

        public void setCommandHandler(Function<CommandBody, CompletableFuture<CommandResultBody>> handler) {
            this.commandHandler = handler;
        }

        public void setStatusSupplier(Supplier<Optional<StatusBody>> supplier) {
            this.statusSupplier = supplier;
        }
    }
}
