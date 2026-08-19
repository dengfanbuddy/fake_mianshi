package com.fakemianshi.util;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * WAV(PCM) 音频工具：拼接多段 wav、把 PCM 裸流封装为 wav，供逐句 TTS 合并与流式 TTS 落盘使用。
 *
 * <p>腾讯云 TTS 输出统一为 16k / 16bit / 单声道 PCM（基础合成返回 wav 容器，流式合成为 PCM 裸流），
 * 本工具统一按小端序处理。
 */
public final class WavAudio {

    private WavAudio() {
    }

    public static final int SAMPLE_RATE = 16000;
    public static final int CHANNELS = 1;
    public static final int BITS_PER_SAMPLE = 16;

    /**
     * 将多段 wav（PCM）按顺序拼接为一段 wav。任一段为空/非法则跳过；全部非法时返回空数组。
     */
    public static byte[] concat(List<byte[]> wavs) {
        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        int sampleRate = SAMPLE_RATE;
        int channels = CHANNELS;
        int bits = BITS_PER_SAMPLE;
        for (byte[] wav : wavs) {
            if (wav == null || wav.length == 0) {
                continue;
            }
            PcmInfo info = parse(wav);
            if (info == null) {
                continue;
            }
            sampleRate = info.sampleRate;
            channels = info.channels;
            bits = info.bitsPerSample;
            pcm.writeBytes(info.pcm);
        }
        byte[] pcmBytes = pcm.toByteArray();
        if (pcmBytes.length == 0) {
            return new byte[0];
        }
        return pcmToWav(pcmBytes, sampleRate, channels, bits);
    }

    /** 将 PCM 裸流封装为 wav 文件字节（44 字节头 + PCM）。 */
    public static byte[] pcmToWav(byte[] pcm, int sampleRate, int channels, int bitsPerSample) {
        int byteRate = sampleRate * channels * bitsPerSample / 8;
        int blockAlign = channels * bitsPerSample / 8;
        int dataSize = pcm.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream(44 + dataSize);
        writeAscii(out, "RIFF");
        writeIntLE(out, 36 + dataSize);
        writeAscii(out, "WAVE");
        writeAscii(out, "fmt ");
        writeIntLE(out, 16);
        writeShortLE(out, 1); // PCM
        writeShortLE(out, channels);
        writeIntLE(out, sampleRate);
        writeIntLE(out, byteRate);
        writeShortLE(out, blockAlign);
        writeShortLE(out, bitsPerSample);
        writeAscii(out, "data");
        writeIntLE(out, dataSize);
        out.writeBytes(pcm);
        return out.toByteArray();
    }

    /**
     * 解析 wav 头，返回 PCM 数据与格式参数；非法时返回 null。
     * 通过扫描 chunk 定位 data 块，兼容含额外 chunk（如 LIST）的 wav。
     */
    public static PcmInfo parse(byte[] wav) {
        if (wav == null || wav.length < 44) {
            return null;
        }
        if (wav[0] != 'R' || wav[1] != 'I' || wav[2] != 'F' || wav[3] != 'F') {
            return null;
        }
        if (wav[8] != 'W' || wav[9] != 'A' || wav[10] != 'V' || wav[11] != 'E') {
            return null;
        }
        int channels = readShortLE(wav, 22);
        int sampleRate = readIntLE(wav, 24);
        int bits = readShortLE(wav, 34);

        int offset = 12;
        int dataOffset = -1;
        int dataLen = 0;
        while (offset + 8 <= wav.length) {
            String id = new String(wav, offset, 4, StandardCharsets.US_ASCII);
            int size = readIntLE(wav, offset + 4);
            if ("data".equals(id)) {
                dataOffset = offset + 8;
                dataLen = size;
                break;
            }
            offset += 8 + size + (size % 2); // chunk 按 2 字节对齐
        }
        if (dataOffset < 0 || dataOffset + dataLen > wav.length) {
            return null;
        }
        byte[] pcm = new byte[dataLen];
        System.arraycopy(wav, dataOffset, pcm, 0, dataLen);
        return new PcmInfo(pcm, sampleRate, channels, bits);
    }

    public record PcmInfo(byte[] pcm, int sampleRate, int channels, int bitsPerSample) {
    }

    private static void writeAscii(ByteArrayOutputStream out, String s) {
        for (int i = 0; i < s.length(); i++) {
            out.write(s.charAt(i));
        }
    }

    private static void writeIntLE(ByteArrayOutputStream out, int v) {
        out.write(v & 0xff);
        out.write((v >> 8) & 0xff);
        out.write((v >> 16) & 0xff);
        out.write((v >> 24) & 0xff);
    }

    private static void writeShortLE(ByteArrayOutputStream out, int v) {
        out.write(v & 0xff);
        out.write((v >> 8) & 0xff);
    }

    private static int readIntLE(byte[] b, int off) {
        return (b[off] & 0xff) | ((b[off + 1] & 0xff) << 8) | ((b[off + 2] & 0xff) << 16) | ((b[off + 3] & 0xff) << 24);
    }

    private static int readShortLE(byte[] b, int off) {
        return (b[off] & 0xff) | ((b[off + 1] & 0xff) << 8);
    }
}
