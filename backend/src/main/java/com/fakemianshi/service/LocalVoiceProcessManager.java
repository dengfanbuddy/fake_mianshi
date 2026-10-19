package com.fakemianshi.service;

import com.fakemianshi.config.BusinessException;
import com.fakemianshi.config.VoiceProperties;
import com.fakemianshi.entity.VoiceConfig;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 本地 Qwen3-TTS sidecar（voice-service）的进程管家。
 *
 * <p>解决"sidecar 是独立 Python 进程、用户忘记启动就用不了"的问题：
 * <ul>
 *   <li>后端启动完成后，若生效供应商是本地 Qwen 且 9100 无人监听，自动拉起 sidecar；</li>
 *   <li>后端关闭时结束<b>由自己拉起的</b> sidecar；</li>
 *   <li>页面可以通过接口手动启动/停止，并查看最近日志用于排错。</li>
 * </ul>
 *
 * <p><b>所有权是核心不变量</b>：启动时如果 sidecar 已在运行但来源不明（用户手工跑的、或上次硬杀遗留的
 * 孤儿进程），后端只按"外部实例"对待——不接管、不结束，避免把别人的服务误杀。
 * 唯一会接管的例外是：{@code pid 文件}里记着"这是本后端上次拉起的进程"，见 {@link #adoptLeftover()}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalVoiceProcessManager {

    /** 保留给页面展示的最近日志行数（模型加载日志很长，只留尾巴） */
    private static final int MAX_LOG_LINES = 200;

    /** 页面展示的行数 */
    private static final int LOG_TAIL = 40;

    private final VoiceProperties voiceProperties;
    private final LocalQwenVoiceService localQwenVoiceService;
    private final VoiceConfigService voiceConfigService;

    private final Object lock = new Object();

    /** 由本进程直接拉起的子进程 */
    private Process process;
    /** 接管来的进程句柄（上次硬杀遗留的孤儿），没有则为 null */
    private ProcessHandle adoptedHandle;
    /** 是否有归属于本后端（含接管）的进程；决定能否结束它 */
    private boolean owned;
    private Instant startedAt;
    private String lastError;
    private String lastCommand;

    private final Deque<String> recentLogs = new ArrayDeque<>();
    private Thread logPump;

    // ---------------- 生命周期钩子 ----------------

    /**
     * 后端启动完成后：供应商是本地 Qwen 就确保 sidecar 可用。
     *
     * <p>不会阻塞启动流程，也不等待模型加载完成（加载约 15–20s，页面通过 /local/health 轮询进度）；
     * 失败只记录日志，不影响后端对外服务。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void autoStartIfNeeded() {
        adoptLeftover();
        if (!voiceProperties.getLocal().isAutoStart()) {
            log.info("本地语音 sidecar 自动启动已关闭（app.voice.local.auto-start=false）");
            return;
        }
        boolean localProvider;
        try {
            VoiceConfig cfg = voiceConfigService.resolveActive();
            localProvider = cfg != null && cfg.isLocalProvider();
        } catch (RuntimeException e) {
            log.warn("读取语音配置失败，跳过 sidecar 自动启动: {}", e.getMessage());
            return;
        }
        if (!localProvider) {
            log.info("生效语音供应商不是本地 Qwen，跳过 sidecar 自动启动");
            return;
        }
        try {
            Map<String, Object> result = start();
            log.info("本地语音 sidecar 自动启动: {}", result.get("message"));
        } catch (RuntimeException e) {
            setLastError(e.getMessage());
            log.warn("本地语音 sidecar 自动启动失败（TTS 将回退腾讯云或报错）: {}", e.getMessage());
        }
    }

    /** 后端关闭：只结束归属于本后端的 sidecar */
    @PreDestroy
    public void shutdown() {
        if (!voiceProperties.getLocal().isStopOnShutdown()) {
            return;
        }
        synchronized (lock) {
            if (!ownedProcessAlive()) {
                return;
            }
            log.info("后端关闭，结束本地语音 sidecar (PID {})", ownedPid());
            killOwned();
        }
    }

    // ---------------- 对外操作 ----------------

    /**
     * 启动 sidecar（幂等）。
     *
     * <p>已在运行（无论归属）时只回报状态，不重复拉起。
     */
    public Map<String, Object> start() {
        synchronized (lock) {
            Map<String, Object> result = new LinkedHashMap<>();
            if (reachable()) {
                result.put("started", false);
                result.put("reason", "already-running");
                result.put("managed", owned);
                result.put("message", owned
                        ? "本地语音服务正在运行（由后端托管）"
                        : "本地语音服务已在运行（由外部启动，后端关闭时不会结束它）");
                return result;
            }
            if (ownedProcessAlive()) {
                result.put("started", false);
                result.put("reason", "starting");
                result.put("managed", true);
                result.put("message", "本地语音服务已在启动中，模型加载约需 20 秒");
                return result;
            }

            File serviceDir = resolveServiceDir();
            if (serviceDir == null) {
                throw new BusinessException("未找到 voice-service 目录：请确认它和 backend 目录同级，"
                        + "或在配置里指定 app.voice.local.service-dir");
            }
            String python = resolvePython(serviceDir);
            if (python == null) {
                throw new BusinessException("未找到 Python 解释器：请先执行 voice-service/uv sync 生成 .venv，"
                        + "或配置 app.voice.local.python");
            }

            String host = "127.0.0.1";
            int port = 9100;
            try {
                URI base = URI.create(localQwenVoiceService.baseUrl());
                if (base.getHost() != null) {
                    host = base.getHost();
                }
                if (base.getPort() > 0) {
                    port = base.getPort();
                }
            } catch (IllegalArgumentException e) {
                log.warn("本地语音地址解析失败，回退 127.0.0.1:9100: {}", e.getMessage());
            }

            List<String> command = new ArrayList<>(List.of(
                    python, "-m", "uvicorn", "app.main:app",
                    "--host", host, "--port", String.valueOf(port)));
            ProcessBuilder builder = new ProcessBuilder(command)
                    .directory(serviceDir)
                    .redirectErrorStream(true);
            Map<String, String> env = builder.environment();
            env.put("VOICE_HOST", host);
            env.put("VOICE_PORT", String.valueOf(port));
            env.putIfAbsent("HF_ENDPOINT", "https://hf-mirror.com");
            // 强制子进程用 UTF-8 输出：Windows 中文环境默认 GBK，日志里中文会变成乱码
            env.put("PYTHONIOENCODING", "utf-8");
            env.put("PYTHONUTF8", "1");
            env.put("PYTHONUNBUFFERED", "1");

            try {
                process = builder.start();
            } catch (IOException e) {
                String message = "拉起本地语音服务失败: " + e.getMessage();
                setLastError(message);
                throw new BusinessException(message, e);
            }
            owned = true;
            adoptedHandle = null;
            startedAt = Instant.now();
            lastCommand = String.join(" ", command);
            setLastError(null);
            recentLogs.clear();
            startLogPump(process);
            writePidFile(process.pid());

            log.info("已拉起本地语音 sidecar (PID {}): {}", process.pid(), lastCommand);
            result.put("started", true);
            result.put("reason", "spawned");
            result.put("managed", true);
            result.put("pid", process.pid());
            result.put("message", "已启动本地语音服务（PID " + process.pid() + "），模型加载约需 20 秒");
            return result;
        }
    }

    /**
     * 结束归属于本后端的 sidecar。
     *
     * <p>外部实例一律不碰——只回报"不是由后端启动的"。
     */
    public Map<String, Object> stop() {
        synchronized (lock) {
            Map<String, Object> result = new LinkedHashMap<>();
            if (!ownedProcessAlive() || !owned) {
                result.put("stopped", false);
                result.put("reason", "not-managed");
                result.put("managed", false);
                result.put("message", reachable()
                        ? "本地语音服务在运行，但不是由后端启动的，未结束该进程"
                        : "本地语音服务当前未在运行");
                return result;
            }
            Long pid = ownedPid();
            killOwned();
            log.info("已结束本地语音 sidecar (PID {})", pid);
            result.put("stopped", true);
            result.put("reason", "stopped");
            result.put("managed", false);
            result.put("pid", pid);
            result.put("message", "已结束本地语音服务（PID " + pid + "）");
            return result;
        }
    }

    /** 托管状态（页面轮询用，不做网络探测以免阻塞） */
    public Map<String, Object> status() {
        synchronized (lock) {
            boolean alive = ownedProcessAlive();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("managed", alive && owned);
            result.put("running", alive);
            result.put("pid", ownedPid());
            result.put("adopted", alive && process == null && adoptedHandle != null);
            result.put("startedAt", startedAt == null ? null : startedAt.toString());
            result.put("uptimeSeconds", alive && startedAt != null
                    ? Duration.between(startedAt, Instant.now()).getSeconds() : null);
            result.put("autoStart", voiceProperties.getLocal().isAutoStart());
            result.put("stopOnShutdown", voiceProperties.getLocal().isStopOnShutdown());
            result.put("serviceDir", absolutePath(resolveServiceDir()));
            result.put("python", resolvePython(resolveServiceDir()));
            result.put("command", lastCommand);
            result.put("baseUrl", localQwenVoiceService.baseUrl());
            result.put("lastError", lastError);
            result.put("logs", logTail());
            return result;
        }
    }

    // ---------------- 内部实现 ----------------

    private boolean ownedProcessAlive() {
        if (process != null && process.isAlive()) {
            return true;
        }
        return adoptedHandle != null && adoptedHandle.isAlive();
    }

    private Long ownedPid() {
        if (process != null && process.isAlive()) {
            return process.pid();
        }
        if (adoptedHandle != null && adoptedHandle.isAlive()) {
            return adoptedHandle.pid();
        }
        return null;
    }

    /**
     * 接管上次遗留的孤儿 sidecar。
     *
     * <p>关闭钩子在硬杀（IDE 停止按钮、崩溃、断电）时不会执行，此时上次拉起的 Python 仍在占显存。
     * pid 文件只由本后端写入，因此"文件里的 PID 仍活着"可以安全判定为<b>自己的遗留</b>并接管，
     * 让它在下次正常关闭时被清理。
     */
    private void adoptLeftover() {
        Long stalePid = readPidFile();
        if (stalePid == null) {
            return;
        }
        Optional<ProcessHandle> handle = ProcessHandle.of(stalePid);
        if (handle.isEmpty() || !handle.get().isAlive()) {
            clearPidFile();
            return;
        }
        synchronized (lock) {
            if (ownedProcessAlive()) {
                return;
            }
            adoptedHandle = handle.get();
            owned = true;
            startedAt = null;
            log.info("接管上次遗留的本地语音 sidecar (PID {})，将在后端关闭时结束它", stalePid);
        }
    }

    /** sidecar 是否可连通（每次现探，避免缓存挡住"刚刚起来"的实例） */
    private boolean reachable() {
        try {
            Map<String, Object> health = localQwenVoiceService.health();
            return Boolean.TRUE.equals(health.get("reachable"));
        } catch (RuntimeException e) {
            log.debug("sidecar 健康探测异常: {}", e.getMessage());
            return false;
        }
    }

    /** 结束归属本后端的进程（子进程或接管来的句柄），并清理状态与 pid 文件 */
    private void killOwned() {
        if (process != null && process.isAlive()) {
            destroyHandleTree(process.toHandle());
        } else if (adoptedHandle != null && adoptedHandle.isAlive()) {
            destroyHandleTree(adoptedHandle);
        }
        process = null;
        adoptedHandle = null;
        owned = false;
        startedAt = null;
        clearPidFile();
    }

    /**
     * 结束进程树。
     *
     * <p>先优雅 destroy 并等待 {@code stop-timeout-seconds}，超时后强杀。
     * Windows 上 uvicorn 常由一个启动器 python 拉起真正的 worker（父子两个 PID），
     * 只杀父会留下占显存的子进程，故必须连子孙一起处理。
     */
    private void destroyHandleTree(ProcessHandle root) {
        List<ProcessHandle> descendants = root.descendants().toList();
        descendants.forEach(ProcessHandle::destroy);
        root.destroy();
        try {
            if (root.onExit().get(Math.max(1, voiceProperties.getLocal().getStopTimeoutSeconds()),
                    TimeUnit.SECONDS) != null) {
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.debug("等待 sidecar 退出超时或异常: {}", e.getMessage());
        }
        if (root.isAlive() || descendants.stream().anyMatch(ProcessHandle::isAlive)) {
            log.warn("sidecar 未在超时内退出，强制结束");
            descendants.forEach(ProcessHandle::destroyForcibly);
            root.destroyForcibly();
        }
    }

    /**
     * 采集子进程输出。
     *
     * <p>必须持续读走 stdout，否则管道缓冲写满后 uvicorn 会被阻塞——这是最常见的踩坑点。
     *
     * <p>按字节读取再逐行解码，而不是直接套 UTF-8 的 Reader：sidecar 的输出编码取决于它的运行环境，
     * 用户在自己的 cp936 控制台里手工跑 {@code run.ps1} 时输出就是 GBK。硬按 UTF-8 解会把中文变成乱码。
     */
    private void startLogPump(Process target) {
        logPump = new Thread(() -> {
            try (InputStream in = new BufferedInputStream(target.getInputStream());
                 ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream()) {
                int b;
                while ((b = in.read()) != -1) {
                    // tqdm 的进度条用 \r 覆盖同一行，\r 和 \n 都当行终止符
                    if (b == '\n' || b == '\r') {
                        emitLogLine(lineBuffer);
                    } else {
                        lineBuffer.write(b);
                    }
                }
                emitLogLine(lineBuffer);
            } catch (IOException e) {
                log.debug("sidecar 输出读取结束: {}", e.getMessage());
            }
        }, "voice-sidecar-log");
        logPump.setDaemon(true);
        logPump.start();
    }

    private void emitLogLine(ByteArrayOutputStream lineBuffer) {
        if (lineBuffer.size() == 0) {
            return;
        }
        byte[] raw = lineBuffer.toByteArray();
        lineBuffer.reset();
        String text = decodeLogLine(raw).strip();
        if (text.isEmpty() || isProgressNoise(text)) {
            return;
        }
        synchronized (lock) {
            if (recentLogs.size() >= MAX_LOG_LINES) {
                recentLogs.pollFirst();
            }
            recentLogs.addLast(text);
        }
        log.debug("[sidecar] {}", text);
    }

    /**
     * 解码一行日志：先严格按 UTF-8，失败再按 GBK，都不行才用替换字符兜底。
     *
     * <p>顺序不能反——GBK 解码几乎不会报错，先试 GBK 会把 UTF-8 中文解成乱码。
     */
    private static String decodeLogLine(byte[] raw) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(raw))
                    .toString();
        } catch (CharacterCodingException utf8Failure) {
            try {
                return Charset.forName("GBK").newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(raw))
                        .toString();
            } catch (CharacterCodingException gbkFailure) {
                return new String(raw, StandardCharsets.UTF_8);
            }
        }
    }

    private List<String> logTail() {
        List<String> lines = new ArrayList<>(recentLogs);
        int from = Math.max(0, lines.size() - LOG_TAIL);
        return lines.subList(from, lines.size());
    }

    /**
     * 过滤加载进度刷屏行。
     *
     * <p>transformers 的 "Loading weights: 42%|████▌ | 167/402 [00:00<00:00, 212it/s]" 会一次刷几十行，
     * 把真正有用的告警（SoX 缺失、CUDA graph 捕获失败等）挤出可视区域。
     */
    private static boolean isProgressNoise(String line) {
        return line.startsWith("Loading weights:") || line.startsWith("Warming up");
    }

    private void setLastError(String message) {
        this.lastError = message;
    }

    // ---------------- pid 文件 ----------------

    private Path pidFilePath() {
        return Path.of(voiceProperties.getLocal().getPidFile()).toAbsolutePath().normalize();
    }

    private void writePidFile(long pid) {
        Path path = pidFilePath();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, Long.toString(pid), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("写入 sidecar pid 文件失败（不影响运行，但崩溃后无法自动接管）: {}", e.getMessage());
        }
    }

    private Long readPidFile() {
        Path path = pidFilePath();
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            return Long.parseLong(Files.readString(path, StandardCharsets.UTF_8).strip());
        } catch (IOException | NumberFormatException e) {
            log.debug("读取 sidecar pid 文件失败，忽略: {}", e.getMessage());
            return null;
        }
    }

    private void clearPidFile() {
        try {
            Files.deleteIfExists(pidFilePath());
        } catch (IOException e) {
            log.debug("删除 sidecar pid 文件失败: {}", e.getMessage());
        }
    }

    // ---------------- 路径解析 ----------------

    /** 解析 voice-service 目录：配置优先，否则按常见相对位置探测 */
    private File resolveServiceDir() {
        String configured = voiceProperties.getLocal().getServiceDir();
        List<File> candidates = new ArrayList<>();
        if (configured != null && !configured.isBlank()) {
            candidates.add(new File(configured));
        } else {
            String userDir = System.getProperty("user.dir");
            candidates.add(new File(userDir, "../voice-service"));
            candidates.add(new File(userDir, "voice-service"));
            candidates.add(new File(userDir, "../../voice-service"));
        }
        for (File dir : candidates) {
            if (new File(dir, "app/main.py").isFile()) {
                // 归一化：../voice-service 这类相对路径留给页面展示不友好
                return dir.getAbsoluteFile().toPath().normalize().toFile();
            }
        }
        return null;
    }

    /** 解析 Python：配置优先，其次是 voice-service/.venv 下的解释器 */
    private String resolvePython(File serviceDir) {
        String configured = voiceProperties.getLocal().getPython();
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        if (serviceDir == null) {
            return null;
        }
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        File venvPython = windows
                ? new File(serviceDir, ".venv/Scripts/python.exe")
                : new File(serviceDir, ".venv/bin/python");
        return venvPython.isFile() ? venvPython.getAbsolutePath() : null;
    }

    private static String absolutePath(File file) {
        return file == null ? null : file.getAbsolutePath();
    }
}
