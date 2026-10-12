package com.creationai.community.edit;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class LocalVideoReeditorTest {
    @Test
    public void convertsFramesUsingRealFrameRate() {
        assertEquals(67L, LocalVideoReeditor.framesToDurationMs(2, 30d));
        assertEquals(80L, LocalVideoReeditor.framesToDurationMs(2, 25d));
        assertEquals(0L, LocalVideoReeditor.framesToDurationMs(0, 30d));
    }

    @Test
    public void fallsBackToThirtyFramesPerSecond() {
        assertEquals(100L, LocalVideoReeditor.framesToDurationMs(3, 0d));
        assertEquals(100L, LocalVideoReeditor.framesToDurationMs(3, 500d));
    }
}
