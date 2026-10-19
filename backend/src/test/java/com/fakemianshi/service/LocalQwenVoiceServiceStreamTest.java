package com.fakemianshi.service;

import com.fakemianshi.config.VoiceProperties;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 流式 TTS 的分片对齐测试。
 *
 * <p>背景：{@code InputStream.read} 的返回长度是任意的，可能是奇数。16bit PCM 被劈成两半后，
 * 前端 {@code new Int16Array(buffer)} 会直接抛 RangeError（整帧丢失），
 * 或者丢掉半个样本导致之后所有样本错位一字节 —— 那听起来是刺耳的白噪音。
 * 所以服务端必须保证交给 {@code onPcmChunk} 的每个分片都是偶数长度。
 */
class LocalQwenVoiceServiceStreamTest {

    /** 按指定节奏返回数据的 InputStream，用来精确制造奇数长度的 read。 */
    private static final class ScriptedInputStream extends InputStream {
        private final byte[] data;
        private final int[] pattern;
        private int offset = 0;
        private int step = 0;

        ScriptedInputStream(byte[] data, int[] pattern) {
            this.data = data;
            this.pattern = pattern;
        }

        @Override
        public int read() {
            return offset < data.length ? data[offset++] & 0xFF : -1;
        }

        @Override
        public int read(byte[] buf, int off, int len) {
            if (offset >= data.length) {
                return -1;
            }
            int want = Math.min(Math.min(pattern[step++ % pattern.length], len), data.length - offset);
            System.arraycopy(data, offset, buf, off, want);
            offset += want;
            return want;
        }
    }

    @SuppressWarnings("unchecked")
    private static HttpClient streamingClient(InputStream body) {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        try {
            when(response.statusCode()).thenReturn(200);
            when(response.body()).thenReturn(body);
            when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                    .thenReturn(response);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return httpClient;
    }

    private static List<byte[]> stream(HttpClient client) {
        LocalQwenVoiceService service = new LocalQwenVoiceService(new VoiceProperties(), client);
        List<byte[]> chunks = new ArrayList<>();
        service.synthesizeSpeechStream("你好", "{\"tone\":\"serious\"}", chunks::add);
        return chunks;
    }

    private static byte[] pcm(int samples) {
        byte[] bytes = new byte[samples * 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) (i * 31 + 7);
        }
        return bytes;
    }

    @Test
    void shouldEmitOnlyEvenLengthChunksWhenReadsAreOdd() {
        byte[] payload = pcm(5000); // 10000 字节
        // 3、5 字节的读法会把每个样本劈开
        List<byte[]> chunks = stream(streamingClient(new ScriptedInputStream(payload, new int[]{3, 5, 7, 4096})));

        assertTrue(chunks.size() > 1, "应该产生多个分片");
        for (byte[] chunk : chunks) {
            assertEquals(0, chunk.length % 2,
                    "分片长度必须是偶数（16bit 样本不能被劈开），实际 " + chunk.length);
        }
        ByteArrayOutputStream joined = new ByteArrayOutputStream();
        for (byte[] chunk : chunks) {
            joined.writeBytes(chunk);
        }
        assertArrayEquals(payload, joined.toByteArray(), "拼接后必须与原始 PCM 完全一致");
    }

    @Test
    void shouldKeepByteOrderWhenReadAlternatesOddAndEven() {
        byte[] payload = pcm(64);
        List<byte[]> chunks = stream(streamingClient(new ScriptedInputStream(payload, new int[]{1, 2, 3, 4, 5})));

        ByteArrayOutputStream joined = new ByteArrayOutputStream();
        for (byte[] chunk : chunks) {
            assertEquals(0, chunk.length % 2, "分片长度必须是偶数");
            joined.writeBytes(chunk);
        }
        assertArrayEquals(payload, joined.toByteArray());
    }

    @Test
    void shouldDropTrailingHalfSampleWhenStreamItselfIsOddLength() {
        byte[] payload = pcm(10); // 20 字节
        byte[] odd = new byte[payload.length - 1];
        System.arraycopy(payload, 0, odd, 0, odd.length); // 19 字节：最后一帧残缺

        List<byte[]> chunks = stream(streamingClient(new ScriptedInputStream(odd, new int[]{4096})));

        ByteArrayOutputStream joined = new ByteArrayOutputStream();
        for (byte[] chunk : chunks) {
            assertEquals(0, chunk.length % 2);
            joined.writeBytes(chunk);
        }
        byte[] expected = new byte[18];
        System.arraycopy(payload, 0, expected, 0, 18);
        assertArrayEquals(expected, joined.toByteArray(), "残缺的半个样本应被丢弃，其余字节保持不变");
    }
}
