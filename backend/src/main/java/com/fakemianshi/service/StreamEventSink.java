package com.fakemianshi.service;

import com.fakemianshi.dto.MockInterviewRespondResponse;

/**
 * 流式面试回复事件回调节点：由 WebSocket 编排层实现，把面试官文本增量与合成语音推送给浏览器。
 */
public interface StreamEventSink {

    /** 面试官回复文本增量（打字机展示） */
    void onTextDelta(String delta);

    /** 合成的面试官语音 PCM 分片（16k/16bit/单声道，前端按序排队播放） */
    void onAudio(byte[] pcmChunk);

    /** 全部完成，携带已保存的双方消息与换人建议 */
    void onDone(MockInterviewRespondResponse response);

    /** 出错（前端据此降级到非流式路径） */
    void onError(String message);
}
