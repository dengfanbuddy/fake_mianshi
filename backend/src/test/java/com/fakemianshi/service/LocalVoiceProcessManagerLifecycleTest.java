package com.fakemianshi.service;

import com.fakemianshi.config.VoiceProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link LocalVoiceProcessManager} 的真实进程生命周期测试。
 *
 * <p>用一个"永远不退出"的桩进程代替 Python（Windows 下是 .cmd 里循环 ping），验证四件必须成立的事：
 * <ol>
 *   <li>{@code start()} 真的 spawn 出进程并记录 PID；</li>
 *   <li>{@code stop()} 真的把进程（含子进程）结束掉；</li>
 *   <li>{@code shutdown()}（= 后端关闭时的 {@code @PreDestroy}）结束自己拉起的进程；</li>
 *   <li>pid 文件里记录的上次遗留孤儿进程会被接管，并在关闭时清理（崩溃/硬杀后的兜底）。</li>
 * </ol>
 *
 * <p>没有这组用例，"关后端自动关 sidecar"只能是口头承诺——而用 {@code taskkill /F} 从外部杀 JVM
 * 并不会执行关闭钩子，证明不了这条路径。
 */
class LocalVoiceProcessManagerLifecycleTest {

    /**
     * 用 target/ 下的目录而不是 @TempDir：桩进程的工作目录就是 serviceDir，
     * 而 Windows 会锁住运行中进程的 CWD，导致 JUnit 删除临时目录失败（那是环境噪音，不是被测行为的错）。
     * target/ 是一次性目录，清理失败也无所谓。
     */
    private Path tempDir;

    private VoiceProperties properties;
    private LocalQwenVoiceService localVoiceService;
    private LocalVoiceProcessManager manager;
    private Path fakePython;
    private Path pidFile;
    private int probePort;

    @BeforeEach
    void setUp() throws IOException {
        assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("win"),
                "桩进程用 Windows .cmd 实现，其他平台跳过");

        // 必须是绝对路径：桩进程以 serviceDir 为工作目录，相对路径在它里面解析不到，
        // 桩启动器会静默拉不起子进程，用例就变成"假通过"
        tempDir = Path.of("target", "voice-sidecar-lifecycle", UUID.randomUUID().toString())
                .toAbsolutePath();
        Files.createDirectories(tempDir);

        // 伪造 voice-service 目录：start() 会校验 app/main.py 是否存在
        Path serviceDir = tempDir.resolve("voice-service");
        Files.createDirectories(serviceDir.resolve("app"));
        Files.writeString(serviceDir.resolve("app/main.py"), "# stub for test");

        // 桩"解释器"：忽略传入的 uvicorn 参数，循环 ping 保持存活（并留下一个子进程，用于验证进程树结束）
        fakePython = tempDir.resolve("fake-python.cmd");
        Files.writeString(fakePython, String.join("\r\n",
                "@echo off",
                "set \"IGNORED=%*\"",
                ":loop",
                "ping -n 2 127.0.0.1 >nul",
                "goto loop",
                ""));

        pidFile = tempDir.resolve("sidecar.pid");

        properties = new VoiceProperties();
        properties.getLocal().setAutoStart(false);
        properties.getLocal().setStopOnShutdown(true);
        properties.getLocal().setServiceDir(serviceDir.toString());
        properties.getLocal().setPython(fakePython.toString());
        properties.getLocal().setPidFile(pidFile.toString());
        properties.getLocal().setStopTimeoutSeconds(5);

        localVoiceService = mock(LocalQwenVoiceService.class);
        // 用一个空闲端口而不是固定 9100：新逻辑会真的探这个端口有没有关，
        // 撞上本机正在跑的真实 sidecar 会让用例莫名其妙地"结束失败"
        try (java.net.ServerSocket probe = new java.net.ServerSocket(0)) {
            probePort = probe.getLocalPort();
        }
        when(localVoiceService.baseUrl()).thenReturn("http://127.0.0.1:" + probePort);
        // reachable=false 才会真的去 spawn
        when(localVoiceService.health()).thenReturn(Map.of("reachable", false));

        manager = new LocalVoiceProcessManager(properties, localVoiceService, mock(VoiceConfigService.class));
    }

    @AfterEach
    void tearDown() {
        // 兜底：用例失败时也别留下桩进程（它们会一直占着 CPU 打 ping）
        if (manager != null && properties != null) {
            properties.getLocal().setStopOnShutdown(true);
            manager.shutdown();
        }
        if (tempDir == null || !Files.exists(tempDir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(tempDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // target/ 下的一次性目录，删不掉不影响结论
                }
            });
        } catch (IOException ignored) {
            // 同上
        }
    }

    @Test
    void startSpawnsProcessAndStopKillsIt() throws Exception {
        Map<String, Object> started = manager.start();

        assertTrue((Boolean) started.get("started"), "应当成功拉起进程");
        Object pidValue = started.get("pid");
        assertNotNull(pidValue, "应返回 PID");
        long pid = ((Number) pidValue).longValue();
        assertTrue(isAlive(pid), "拉起后进程应存活");
        assertTrue((Boolean) manager.status().get("managed"), "应标记为由后端托管");
        assertTrue(Files.isRegularFile(pidFile), "应写出 pid 文件（崩溃后据此接管）");
        assertEquals(pid, Long.parseLong(Files.readString(pidFile).strip()));

        Map<String, Object> stopped = manager.stop();

        assertTrue((Boolean) stopped.get("stopped"), "应当结束进程");
        assertProcessGone(pid);
        assertFalse((Boolean) manager.status().get("running"), "结束后不应再有运行中的进程");
        assertFalse(Files.exists(pidFile), "结束后应清理 pid 文件");
    }

    @Test
    void shutdownKillsOwnedProcess() throws Exception {
        Map<String, Object> started = manager.start();
        long pid = ((Number) started.get("pid")).longValue();
        assertTrue(isAlive(pid));

        // 模拟后端关闭：@PreDestroy → shutdown()
        manager.shutdown();

        assertProcessGone(pid);
        assertFalse((Boolean) manager.status().get("running"));
        assertFalse(Files.exists(pidFile), "关闭后应清理 pid 文件");
    }

    @Test
    void shutdownLeavesProcessAloneWhenDisabled() throws Exception {
        properties.getLocal().setStopOnShutdown(false);
        Map<String, Object> started = manager.start();
        long pid = ((Number) started.get("pid")).longValue();

        manager.shutdown();

        assertTrue(isAlive(pid), "stop-on-shutdown=false 时不应结束进程");
        // 清理，避免给后续用例留下僵尸进程
        manager.stop();
        assertProcessGone(pid);
    }

    @Test
    void adoptsLeftoverProcessRecordedInPidFile() throws Exception {
        // 模拟"上次后端被硬杀，关闭钩子没执行，Python 变成孤儿"：
        // 一个不是 manager 拉起、但 pid 文件里记着的进程
        Process orphan = new ProcessBuilder(fakePython.toString()).start();
        long orphanPid = orphan.pid();
        try {
            assertTrue(isAlive(orphanPid));
            Files.writeString(pidFile, Long.toString(orphanPid));

            // autoStart=false，所以只会做接管
            manager.autoStartIfNeeded();

            Map<String, Object> status = manager.status();
            assertTrue((Boolean) status.get("managed"), "遗留进程应被接管");
            assertTrue((Boolean) status.get("adopted"), "应标记为接管而来");
            assertEquals(orphanPid, ((Number) status.get("pid")).longValue());

            manager.shutdown();

            assertProcessGone(orphanPid);
            assertFalse(Files.exists(pidFile));
        } finally {
            orphan.destroyForcibly();
            Files.deleteIfExists(pidFile);
        }
    }

    @Test
    void stalePidFileOfDeadProcessIsIgnored() throws Exception {
        // 记录一个早已结束的 PID：不能因此误判/误杀
        Files.writeString(pidFile, "999999");

        manager.autoStartIfNeeded();

        assertFalse((Boolean) manager.status().get("managed"));
        assertFalse(Files.exists(pidFile), "失效的 pid 文件应被清理");
    }

    /**
     * 回归：停止时必须连**子孙进程**一起结束。
     *
     * <p>真实事故：`.venv/Scripts/python.exe` 只是个启动器，它再拉起
     * `Python312/python.exe` 跑 uvicorn，真正占显存的是后者。后端关闭时只结束了启动器，
     * 占 4GB 显存的子进程变成孤儿——用户只看到「后端停了，显存没降」。
     *
     * <p>断言的判据不看进程名（子进程是 cmd/ping，与生产不同形），而看命令行的**唯一路径标记**：
     * 临时目录名带 UUID，整棵树里任何还活着的进程，命令行里都会带着这个 UUID。
     */
    @Test
    void stopKillsGrandchildrenToo() throws Exception {
        assumeTrue(wmicAvailable(), "需要 wmic 才能按命令行识别残留进程");

        // 桩启动器：拉起一个常驻子进程（模拟"真干活的那个 python"），自己再常驻
        Path worker = tempDir.resolve("worker.cmd");
        Files.writeString(worker, String.join("\r\n",
                "@echo off",
                ":loop",
                "ping -n 2 127.0.0.1 >nul",
                "goto loop",
                ""));
        Path treeLauncher = tempDir.resolve("fake-python-tree.cmd");
        Files.writeString(treeLauncher, String.join("\r\n",
                "@echo off",
                "set \"IGNORED=%*\"",
                "start \"\" /b \"%ComSpec%\" /c \"" + worker + "\"",
                ":loop",
                "ping -n 2 127.0.0.1 >nul",
                "goto loop",
                ""));
        properties.getLocal().setPython(treeLauncher.toString());

        Map<String, Object> started = manager.start();
        long rootPid = ((Number) started.get("pid")).longValue();
        String token = tempDir.getFileName().toString();

        // 等子进程真的起来（否则断言会假通过）
        assertTrue(waitForProcessMentioning(token, 2), "桩进程树应当包含子进程");

        manager.stop();

        assertProcessGone(rootPid);
        assertFalse(anyProcessMentioning(token),
                "整棵进程树都必须结束：留下子进程就是留下占显存的孤儿");
    }

    /**
     * 回归：接管来的孤儿（pid 文件里记着、但不是本进程拉起的）也要连子孙一起结束。
     *
     * <p>这是事故的真实形态——上次后端被硬杀，本次启动靠 pid 文件接管，
     * 而 {@code ProcessHandle.descendants()} 对"不是自己创建的进程"在 Windows 上给不出子孙，
     * 于是只杀掉启动器、显存照旧被占。
     */
    @Test
    void shutdownKillsDescendantsOfAdoptedOrphan() throws Exception {
        assumeTrue(wmicAvailable(), "需要 wmic 才能按命令行识别残留进程");

        Path worker = tempDir.resolve("worker.cmd");
        Files.writeString(worker, String.join("\r\n",
                "@echo off",
                ":loop",
                "ping -n 2 127.0.0.1 >nul",
                "goto loop",
                ""));
        Path treeLauncher = tempDir.resolve("fake-python-tree.cmd");
        Files.writeString(treeLauncher, String.join("\r\n",
                "@echo off",
                "start \"\" /b \"%ComSpec%\" /c \"" + worker + "\"",
                ":loop",
                "ping -n 2 127.0.0.1 >nul",
                "goto loop",
                ""));

        Process orphan = new ProcessBuilder(treeLauncher.toString()).start();
        long orphanPid = orphan.pid();
        try {
            Files.writeString(pidFile, Long.toString(orphanPid));
            String token = tempDir.getFileName().toString();
            assertTrue(waitForProcessMentioning(token, 2), "孤儿进程树应当包含子进程");

            manager.autoStartIfNeeded();
            assertTrue((Boolean) manager.status().get("adopted"), "应接管遗留进程");

            manager.shutdown();

            assertProcessGone(orphanPid);
            assertFalse(anyProcessMentioning(token), "接管的孤儿也必须连子孙一起结束");
        } finally {
            orphan.destroyForcibly();
            Files.deleteIfExists(pidFile);
        }
    }

    /**
     * 端口还开着时必须如实报告"没释放"，不能因为"我拉起的 PID 都死了"就谎报成功。
     *
     * <p>这正是线上事故的误导来源：启动器 python 退出了，占显存的解释器还在监听，
     * 旧实现只看 PID、直接返回成功，用户于是只看到"显存没降又不知道为啥"。
     */
    @Test
    void stopReportsNotFreedWhenPortIsStillListening() throws Exception {
        properties.getLocal().setStopTimeoutSeconds(1);
        Map<String, Object> started = manager.start();
        long pid = ((Number) started.get("pid")).longValue();

        // 用一个占着探活端口的 socket 模拟"真正干活的进程没退"
        try (java.net.ServerSocket squatter = new java.net.ServerSocket(probePort)) {
            Map<String, Object> stopped = manager.stop();

            assertTrue((Boolean) stopped.get("stopped"), "该结束的进程仍要结束");
            assertEquals(Boolean.FALSE, stopped.get("freed"), "端口没关就不能报成功");
            assertEquals("stop-incomplete", stopped.get("reason"));
            assertTrue(String.valueOf(stopped.get("message")).contains("仍在监听"),
                    () -> "消息要告诉用户端口还在: " + stopped.get("message"));
            assertProcessGone(pid);
            assertTrue(Files.exists(pidFile), "没释放成功时保留 pid 文件，下次启动据此重试接管");
        }
    }

    /**
     * 回归：pid 文件丢了也要认得出"这是本项目的 sidecar"，并在关闭时清掉。
     *
     * <p>否则 {pid 文件被删 + 上次硬杀} 的组合会让孤儿永久占显存，用户没有任何入口能回收。
     */
    @Test
    void adoptsOwnSidecarWhenPidFileIsMissing() throws Exception {
        Path worker = tempDir.resolve("worker.cmd");
        Files.writeString(worker, String.join("\r\n",
                "@echo off",
                ":loop",
                "ping -n 2 127.0.0.1 >nul",
                "goto loop",
                ""));
        Process orphan = new ProcessBuilder(worker.toString()).start();
        long orphanPid = orphan.pid();
        try {
            // 没有 pid 文件；只能靠 /health 自报的 pid + cwd 认亲
            when(localVoiceService.health()).thenReturn(Map.of(
                    "reachable", true,
                    "pid", (int) orphanPid,
                    "cwd", tempDir.resolve("voice-service").toString()));

            manager.autoStartIfNeeded();

            Map<String, Object> status = manager.status();
            assertTrue((Boolean) status.get("adopted"), "应接管这个孤儿");
            assertEquals(orphanPid, ((Number) status.get("pid")).longValue());

            manager.shutdown();

            assertProcessGone(orphanPid);
        } finally {
            orphan.destroyForcibly();
        }
    }

    /** cwd 不是本项目的实例（别人在别的目录跑的服务）不能被接管 */
    @Test
    void doesNotAdoptForeignSidecarWhenPidFileIsMissing() {
        long foreignPid = ProcessHandle.current().pid();
        when(localVoiceService.health()).thenReturn(Map.of(
                "reachable", true,
                "pid", (int) foreignPid,
                "cwd", "D:\\somewhere\\else\\voice-service"));

        manager.autoStartIfNeeded();

        assertFalse((Boolean) manager.status().get("managed"), "别的目录的服务不能碰");
    }

    // ---------------- 按命令行识别残留进程 ----------------
    private static boolean wmicAvailable() {
        try {
            Process p = new ProcessBuilder("wmic", "process", "get", "ProcessId", "/format:csv")
                    .redirectErrorStream(true).start();
            // 必须先把输出读干净：进程列表有几百行，管道缓冲写满后 wmic 会阻塞，waitFor 就会假失败
            p.getInputStream().readAllBytes();
            return p.waitFor(20, java.util.concurrent.TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean anyProcessMentioning(String token) {
        try {
            Process p = new ProcessBuilder("wmic", "process", "get", "CommandLine").redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes());
            p.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
            return output.contains(token);
        } catch (Exception e) {
            throw new AssertionError("查询进程列表失败: " + e.getMessage(), e);
        }
    }

    private static boolean waitForProcessMentioning(String token, int minChildren) throws InterruptedException {
        for (int i = 0; i < 25; i++) {
            if (anyProcessMentioning(token + File.separator + "worker.cmd")) {
                return true;
            }
            Thread.sleep(200);
        }
        return false;
    }

    private static boolean isAlive(long pid) {
        return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
    }

    /** 结束是异步的（destroy → 等待 → 强杀），轮询最多 10 秒 */
    private static void assertProcessGone(long pid) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            if (!isAlive(pid)) {
                return;
            }
            Thread.sleep(200);
        }
        Optional<ProcessHandle> handle = ProcessHandle.of(pid);
        assertFalse(handle.isPresent() && handle.get().isAlive(),
                "PID " + pid + " 在 10 秒内未被结束");
    }
}
