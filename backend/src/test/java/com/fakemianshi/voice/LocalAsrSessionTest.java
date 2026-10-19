package com.fakemianshi.voice;

import com.fakemianshi.config.VoiceProperties;
import com.fakemianshi.service.LocalQwenVoiceService;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 本地识别会话的纯逻辑测试：mock 掉 {@link LocalQwenVoiceService}，不碰网络也不碰模型。
 *
 * <p>重点验证三件容易出错的事：
 * <ol>
 *   <li>分片音频必须按顺序累积成完整缓冲再送识别（拼接错就会识别成半句话）；</li>
 *   <li>松手只产出<b>一次</b>最终结果（重复触发会让面试官回复两遍）；</li>
 *   <li>关闭/松手之后到达的中间结果必须丢弃（否则前端会看到文本"回退"）。</li>
 * </ol>
 */
class LocalAsrSessionTest {

    /** 16k/16bit/单声道 → 每秒 32000 字节 */
    private static final int BYTES_PER_SECOND = 32000;

    private static VoiceProperties.Asr config(int partialIntervalMs) {
        VoiceProperties.Asr asr = new VoiceProperties.Asr();
        asr.setPartialIntervalMs(partialIntervalMs);
        asr.setPartialMinDeltaMs(0);
        asr.setPartialMaxAudioSeconds(30);
        return asr;
    }

    private static byte[] pcm(int seconds) {
        byte[] data = new byte[seconds * BYTES_PER_SECOND];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 251);
        }
        return data;
    }

    /** 收集回调的监听器 */
    private static final class Recorder implements AsrSession.Listener {
        final CountDownLatch finals = new CountDownLatch(1);
        final CountDownLatch partials = new CountDownLatch(1);
        final List<String> partialTexts = new CopyOnWriteArrayList<>();
        final AtomicReference<String> finalText = new AtomicReference<>();
        final AtomicReference<String> error = new AtomicReference<>();
        final AtomicReference<byte[]> finalAudio = new AtomicReference<>();

        @Override
        public void onPartialResult(String text) {
            partialTexts.add(text);
            partials.countDown();
        }

        @Override
        public void onFinalResult(String text) {
            finalText.set(text);
            finals.countDown();
        }

        @Override
        public void onError(String message) {
            error.set(message);
            finals.countDown();
        }
    }

    @Test
    void stop_shouldReturnEmpty_whenNoAudioWasSent() throws Exception {
        LocalQwenVoiceService service = mock(LocalQwenVoiceService.class);
        Recorder recorder = new Recorder();

        LocalAsrSession session = LocalAsrSession.open(service, config(0), recorder);
        session.stop();

        assertTrue(recorder.finals.await(2, TimeUnit.SECONDS), "松手后必须有回调");
        assertEquals("", recorder.finalText.get());
        // 没有音频就不该打扰模型
        org.mockito.Mockito.verify(service, org.mockito.Mockito.never())
                .recognizePcm16(any(), anyInt());
    }

    @Test
    void stop_shouldTranscribeConcatenatedBuffer() throws Exception {
        LocalQwenVoiceService service = mock(LocalQwenVoiceService.class);
        when(service.recognizePcm16(any(), anyInt())).thenReturn("  我在上一家公司负责订单系统  ");
        Recorder recorder = new Recorder();

        LocalAsrSession session = LocalAsrSession.open(service, config(0), recorder);
        session.sendAudio(new byte[0]); // 空块必须被忽略
        byte[] first = pcm(1);
        session.sendAudio(first);
        session.sendAudio(first);
        session.stop();

        assertTrue(recorder.finals.await(2, TimeUnit.SECONDS));
        // 最终文本要去掉首尾空白
        assertEquals("我在上一家公司负责订单系统", recorder.finalText.get());

        org.mockito.ArgumentCaptor<byte[]> captor = org.mockito.ArgumentCaptor.forClass(byte[].class);
        org.mockito.Mockito.verify(service).recognizePcm16(captor.capture(), anyInt());
        byte[] sent = captor.getValue();
        assertEquals(2 * first.length, sent.length, "两段音频必须按顺序拼成完整缓冲");

        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        expected.writeBytes(first);
        expected.writeBytes(first);
        assertArrayEquals(expected.toByteArray(), sent);
    }

    @Test
    void stop_shouldEmitOnlyOneFinalResult_perRound() throws Exception {
        LocalQwenVoiceService service = mock(LocalQwenVoiceService.class);
        when(service.recognizePcm16(any(), anyInt())).thenReturn("你好");
        Recorder recorder = new Recorder();

        LocalAsrSession session = LocalAsrSession.open(service, config(0), recorder);
        session.sendAudio(pcm(1));
        session.stop();
        session.stop(); // 重复松手（前端可能重复发 end）
        session.sendAudio(pcm(1)); // 松手后的音频不该再生效

        assertTrue(recorder.finals.await(2, TimeUnit.SECONDS));
        Thread.sleep(150);
        org.mockito.Mockito.verify(service, org.mockito.Mockito.times(1))
                .recognizePcm16(any(), anyInt());
    }

    @Test
    void partial_shouldBeEmittedWhileSpeaking() throws Exception {
        LocalQwenVoiceService service = mock(LocalQwenVoiceService.class);
        when(service.recognizePcm16(any(), anyInt())).thenReturn("缓存穿透可以用");
        Recorder recorder = new Recorder();

        LocalAsrSession session = LocalAsrSession.open(service, config(80), recorder);
        session.sendAudio(pcm(1)); // 1 秒音频 > 半秒门槛

        assertTrue(recorder.partials.await(3, TimeUnit.SECONDS), "说话过程中应该有中间结果");
        assertEquals("缓存穿透可以用", recorder.partialTexts.get(0));
        assertNull(recorder.finalText.get(), "还没松手，不应有最终结果");
        session.close();
    }

    @Test
    void partial_shouldBeSkipped_whenIntervalDisabled() throws Exception {
        LocalQwenVoiceService service = mock(LocalQwenVoiceService.class);
        when(service.recognizePcm16(any(), anyInt())).thenReturn("不应出现");
        Recorder recorder = new Recorder();

        LocalAsrSession session = LocalAsrSession.open(service, config(0), recorder);
        session.sendAudio(pcm(2));
        Thread.sleep(400);

        assertTrue(recorder.partialTexts.isEmpty(), "partialIntervalMs=0 时不该有中间结果");
        session.close();
    }

    @Test
    void partial_shouldNotFireForTooShortAudio() throws Exception {
        LocalQwenVoiceService service = mock(LocalQwenVoiceService.class);
        when(service.recognizePcm16(any(), anyInt())).thenReturn("太短");
        Recorder recorder = new Recorder();

        LocalAsrSession session = LocalAsrSession.open(service, config(50), recorder);
        session.sendAudio(new byte[BYTES_PER_SECOND / 4]); // 0.25s
        Thread.sleep(400);

        assertTrue(recorder.partialTexts.isEmpty(), "不足半秒不该触发中间识别");
        session.close();
    }

    @Test
    void partial_shouldBeDiscarded_afterStop() throws Exception {
        // 松手时正在飞的中间结果，回来后必须被 generation 丢弃，避免文本回退
        LocalQwenVoiceService service = mock(LocalQwenVoiceService.class);
        CountDownLatch inPartial = new CountDownLatch(1);
        when(service.recognizePcm16(any(), anyInt())).thenAnswer(invocation -> {
            inPartial.countDown();
            Thread.sleep(300);
            return "迟到的中间结果";
        });
        Recorder recorder = new Recorder();

        LocalAsrSession session = LocalAsrSession.open(service, config(60), recorder);
        session.sendAudio(pcm(1));
        assertTrue(inPartial.await(2, TimeUnit.SECONDS), "中间识别应已开始");
        session.stop();

        assertTrue(recorder.finals.await(3, TimeUnit.SECONDS));
        Thread.sleep(400); // 等那个迟到的中间结果回来
        assertTrue(recorder.partialTexts.stream().noneMatch("迟到的中间结果"::equals),
                "松手后到达的中间结果必须丢弃，实际收到: " + recorder.partialTexts);
    }

    @Test
    void close_shouldCancelPendingWork_withoutFinalResult() throws Exception {
        LocalQwenVoiceService service = mock(LocalQwenVoiceService.class);
        when(service.recognizePcm16(any(), anyInt())).thenReturn("不该出现");
        Recorder recorder = new Recorder();

        LocalAsrSession session = LocalAsrSession.open(service, config(50), recorder);
        session.sendAudio(pcm(1));
        session.close();
        Thread.sleep(300);

        assertNull(recorder.finalText.get());
        assertTrue(recorder.partialTexts.stream().noneMatch("不该出现"::equals));
    }

    @Test
    void stop_shouldReportError_whenRecognitionFails() throws Exception {
        LocalQwenVoiceService service = mock(LocalQwenVoiceService.class);
        when(service.recognizePcm16(any(), anyInt()))
                .thenThrow(new com.fakemianshi.config.BusinessException("voice-service 未启动"));
        Recorder recorder = new Recorder();

        LocalAsrSession session = LocalAsrSession.open(service, config(0), recorder);
        session.sendAudio(pcm(1));
        session.stop();

        assertTrue(recorder.finals.await(2, TimeUnit.SECONDS));
        assertNotNull(recorder.error.get());
        assertTrue(recorder.error.get().contains("本地语音识别失败"), recorder.error.get());
        assertTrue(recorder.error.get().contains("voice-service 未启动"), recorder.error.get());
    }
}
