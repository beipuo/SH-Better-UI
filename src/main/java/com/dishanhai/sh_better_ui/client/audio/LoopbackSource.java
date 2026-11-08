package com.dishanhai.sh_better_ui.client.audio;

/** 山海：仅供系统输出回环后端使用；打开、读取及释放在同一采集线程。 */
interface LoopbackSource extends AutoCloseable {
    void open() throws Exception;
    Block poll() throws Exception;
    @Override void close();

    record Block(float[] samples, int channels, int sampleRate, boolean discontinuity) {
        Block(float[] mono, int sampleRate, boolean discontinuity) {
            this(mono, 1, sampleRate, discontinuity);
        }
    }
}
