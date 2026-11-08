package com.dishanhai.sh_better_ui.client.api;

import com.dishanhai.sh_better_ui.client.audio.MusicAnalyzer;
import com.dishanhai.sh_better_ui.client.audio.SystemAudioCapture;

/** 山海：系统输出采集与独立 PCM 分析入口；工厂不自动采集。 */
public final class AudioApi {
    AudioApi() {}

    public SystemAudioCapture systemOutput() { return new SystemAudioCapture(); }

    public MusicAnalyzer analyzer() { return new MusicAnalyzer(); }
}
