package com.fakemianshi.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class WavAudioTest {

    @Test
    void pcmToWavAndParseRoundTrip() {
        byte[] pcm = new byte[3200]; // 100ms @16k 16bit mono
        for (int i = 0; i < pcm.length; i++) {
            pcm[i] = (byte) i;
        }
        byte[] wav = WavAudio.pcmToWav(pcm, 16000, 1, 16);
        WavAudio.PcmInfo info = WavAudio.parse(wav);
        assertNotNull(info);
        assertArrayEquals(pcm, info.pcm());
        assertEquals(16000, info.sampleRate());
        assertEquals(1, info.channels());
        assertEquals(16, info.bitsPerSample());
    }

    @Test
    void concatTwoWavs() {
        byte[] pcm1 = new byte[1000];
        byte[] pcm2 = new byte[2000];
        byte[] wav1 = WavAudio.pcmToWav(pcm1, 16000, 1, 16);
        byte[] wav2 = WavAudio.pcmToWav(pcm2, 16000, 1, 16);
        byte[] merged = WavAudio.concat(List.of(wav1, wav2));
        WavAudio.PcmInfo info = WavAudio.parse(merged);
        assertNotNull(info);
        assertEquals(3000, info.pcm().length);
    }

    @Test
    void parseInvalidReturnsNull() {
        assertNull(WavAudio.parse(new byte[10]));
        assertNull(WavAudio.parse(null));
    }
}
