package io.github.halilozat.trendrepolar

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class QueryTest {
    private val today = LocalDate.of(2026, 10, 30)

    @Test fun onlyDate() = assertEquals("created:>=2026-10-29", buildQuery(1, null, "  ", today))

    @Test fun textAndLanguage() =
        assertEquals("ai agent created:>=2026-09-30 language:\"C++\"", buildQuery(30, "C++", " ai agent ", today))

    @Test fun compactNumbers() {
        assertEquals("999", compact(999))
        assertEquals("1,2 B", compact(1234))
        assertEquals("1,5 Mn", compact(1_500_000))
    }
}
