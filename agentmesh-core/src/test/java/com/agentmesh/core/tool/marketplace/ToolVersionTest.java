package com.agentmesh.core.tool.marketplace;

import com.agentmesh.core.tool.marketplace.model.ToolVersion;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ToolVersionTest {

    @Test
    void shouldParseValidVersion() {
        ToolVersion v = ToolVersion.parse("1.2.3");
        assertEquals(1, v.getMajor());
        assertEquals(2, v.getMinor());
        assertEquals(3, v.getPatch());
    }

    @Test
    void shouldParseZeroVersion() {
        ToolVersion v = ToolVersion.parse("0.0.0");
        assertEquals(0, v.getMajor());
        assertEquals(0, v.getMinor());
        assertEquals(0, v.getPatch());
    }

    @Test
    void shouldParseLargeVersion() {
        ToolVersion v = ToolVersion.parse("999.999.999");
        assertEquals(999, v.getMajor());
        assertEquals(999, v.getMinor());
        assertEquals(999, v.getPatch());
    }

    @Test
    void shouldRejectNullVersion() {
        assertThrows(IllegalArgumentException.class, () -> ToolVersion.parse(null));
    }

    @Test
    void shouldRejectInvalidFormat() {
        assertThrows(IllegalArgumentException.class, () -> ToolVersion.parse("1.2"));
        assertThrows(IllegalArgumentException.class, () -> ToolVersion.parse("1.2.3.4"));
        assertThrows(IllegalArgumentException.class, () -> ToolVersion.parse("abc"));
        assertThrows(IllegalArgumentException.class, () -> ToolVersion.parse("1.2.3-beta"));
    }

    @Test
    void shouldRejectNegativeVersion() {
        assertThrows(IllegalArgumentException.class, () -> ToolVersion.parse("-1.0.0"));
    }

    @Test
    void shouldCompareEqualVersions() {
        ToolVersion v1 = ToolVersion.parse("1.0.0");
        ToolVersion v2 = ToolVersion.parse("1.0.0");
        assertEquals(0, v1.compareTo(v2));
    }

    @Test
    void shouldCompareMajorVersion() {
        assertTrue(ToolVersion.parse("2.0.0").compareTo(ToolVersion.parse("1.0.0")) > 0);
        assertTrue(ToolVersion.parse("1.0.0").compareTo(ToolVersion.parse("2.0.0")) < 0);
    }

    @Test
    void shouldCompareMinorVersion() {
        assertTrue(ToolVersion.parse("1.2.0").compareTo(ToolVersion.parse("1.1.0")) > 0);
        assertTrue(ToolVersion.parse("1.1.0").compareTo(ToolVersion.parse("1.2.0")) < 0);
    }

    @Test
    void shouldComparePatchVersion() {
        assertTrue(ToolVersion.parse("1.0.2").compareTo(ToolVersion.parse("1.0.1")) > 0);
        assertTrue(ToolVersion.parse("1.0.1").compareTo(ToolVersion.parse("1.0.2")) < 0);
    }

    @Test
    void shouldCompareToNullReturnPositive() {
        assertTrue(ToolVersion.parse("1.0.0").compareTo(null) > 0);
    }

    @Test
    void shouldToStringCorrectly() {
        assertEquals("1.2.3", ToolVersion.parse("1.2.3").toString());
    }

    @Test
    void shouldBeEqualByValue() {
        ToolVersion v1 = ToolVersion.parse("1.0.0");
        ToolVersion v2 = ToolVersion.parse("1.0.0");
        assertEquals(v1, v2);
        assertEquals(v1.hashCode(), v2.hashCode());
    }
}