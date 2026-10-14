package com.kudcrafts.widgets

import com.kudcrafts.widgets.core.Format
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatTest {
    @Test fun shortMatchesTradingBar() {
        // The same cases as trading-bar's TradingBarTests.swift.
        assertEquals("0", Format.short(0.0))
        assertEquals("950", Format.short(950.0))
        assertEquals("695K", Format.short(695_000.0))
        assertEquals("2.4M", Format.short(2_436_000.0))
        assertEquals("2M", Format.short(2_000_000.0))
        assertEquals("200M", Format.short(200_000_000.0))
        assertEquals("1.2B", Format.short(1_250_000_000.0))
        assertEquals("199M", Format.short(199_900_000.0))
        assertEquals("1.9M", Format.short(1_990_000.0))
    }

    @Test fun shortNegative() {
        assertEquals("-289K", Format.short(-289_000.0))
        assertEquals("-1.1M", Format.short(-1_150_000.0))
        assertEquals("0", Format.short(-0.4))
    }

    @Test fun roasAndCount() {
        assertEquals("5.6", Format.roas(5.6))
        assertEquals("5.9", Format.roas(5.99)) // truncated, like short()
        assertEquals("–", Format.roas(null))
        assertEquals("64", Format.count(64.0))
        assertEquals("1.204", Format.count(1204.0))
    }

    @Test fun delta() {
        assertEquals("▲ 12% vs yesterday", Format.delta(12.4))
        assertEquals("▼ 4.5% vs yesterday", Format.delta(-4.56))
        assertEquals("▼ 4% vs yesterday", Format.delta(-4.0))
        assertEquals("▲ 150% vs yesterday", Format.delta(150.9))
        assertEquals("≈ yesterday", Format.delta(0.3))
        assertNull(Format.delta(null))
        assertNull(Format.delta(Double.POSITIVE_INFINITY))
    }

    @Test fun age() {
        assertEquals("now", Format.age(0))
        assertEquals("38m ago", Format.age(38))
        assertEquals("3h ago", Format.age(200))
        assertEquals("2d ago", Format.age(60 * 50))
    }
}
