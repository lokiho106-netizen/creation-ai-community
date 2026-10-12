package com.creationai.community.automation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ToutiaoAdapterTest {
    @Test
    public void parsesChineseCompactCounts() {
        assertEquals(12_000L, ToutiaoAdapter.parseCompactCount("1.2万播放"));
        assertEquals(230_000_000L, ToutiaoAdapter.parseCompactCount("2.3亿"));
        assertEquals(986L, ToutiaoAdapter.parseCompactCount("986赞"));
        assertEquals(-1L, ToutiaoAdapter.parseCompactCount("播放"));
    }

    @Test
    public void parsesRelativeAge() {
        assertEquals(0, ToutiaoAdapter.parseAgeHours("刚刚"));
        assertEquals(0, ToutiaoAdapter.parseAgeHours("35分钟前"));
        assertEquals(7, ToutiaoAdapter.parseAgeHours("7小时前"));
        assertEquals(48, ToutiaoAdapter.parseAgeHours("2天前"));
        assertEquals(-1, ToutiaoAdapter.parseAgeHours("2026-08-20"));
    }

    @Test
    public void matchesAnyRequiredKeywordIgnoringCase() {
        assertTrue(ToutiaoAdapter.containsAnyRequired("中美AI芯片竞争", "ai, 大模型"));
        assertFalse(ToutiaoAdapter.containsAnyRequired("新能源汽车观察", "AI,芯片"));
        assertTrue(ToutiaoAdapter.containsAnyRequired("任意标题", ""));
    }
}
