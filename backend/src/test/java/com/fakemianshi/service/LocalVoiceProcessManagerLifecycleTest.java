package com.fakemianshi.service;

import com.fakemianshi.config.VoiceProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

    @BeforeEach
    void setUp() throws IOException {
        assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("win"),
                "桩进程用 Windows .cmd 实现，其他平台跳过");

        tempDir = Path.of("target", "voice-sidecar-lifecycle", UUID.randomUUID().toString());
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
        when(localVoiceService.baseUrl()).thenReturn("http://127.0.0.1:9100");
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
