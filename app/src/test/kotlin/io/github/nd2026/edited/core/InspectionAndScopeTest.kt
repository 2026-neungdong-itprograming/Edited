package io.github.nd2026.edited.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InspectionModelTest {
    private fun model(area: TextArea, vararg lines: Int) = InspectionModel().also {
        it.attachTo(area)
        it.set(lines.map { line -> Inspection(line, Severity.WARNING, "m$line") })
    }

    @Test
    fun insertedLinesAboveShiftMarkersDown() {
        val area = TextArea("a\nb\nc")
        val m = model(area, 2)
        area.insert(0, "x\ny\n")
        assertEquals(listOf(4), m.markers.map { it.line })
    }

    @Test
    fun editsOnTheSameLineDoNotMoveMarkers() {
        val area = TextArea("a\nb\nc")
        val m = model(area, 1)
        area.insert(2, "zzz")
        assertEquals(listOf(1), m.markers.map { it.line })
    }

    @Test
    fun mergedAwayLinesDropTheirMarkersAndLaterOnesShiftUp() {
        val area = TextArea("a\nb\nc\nd")
        val m = model(area, 1, 3)
        area.delete(1, 4) // removes "\nb\n": the marker on "b" goes away, "d" moves from line 3 to line 1
        assertEquals(listOf(1), m.markers.map { it.line })
    }

    @Test
    fun undoRestoresTheShiftedMarkers() {
        val area = TextArea("a\nb\nc")
        val m = model(area, 2)
        area.insert(0, "x\n")
        area.undo()
        assertEquals(listOf(2), m.markers.map { it.line })
    }

    @Test
    fun worstSeverityWinsPerLine() {
        val m = InspectionModel()
        m.set(listOf(Inspection(1, Severity.INFO, "i"), Inspection(1, Severity.ERROR, "e"), Inspection(1, Severity.WARNING, "w")))
        assertEquals(Severity.ERROR, m.worstOnLine(1)?.severity)
        assertNull(m.worstOnLine(2))
    }
}

class ScopeResolverTest {
    private val novel = """
        # 달빛 아래의 계약
        ## 제1화. 낯선 손님
        비가 그쳤다.
        ***
        창가에 앉았다.
        ## 제2화. 깨진 약속
        새벽 종이 울렸다.
        * * *
        한도윤은 없었다.
        ***
        경비대가 왔다.
    """.trimIndent()

    private fun titles(line: Int) = ScopeResolver.resolve(TextArea(novel), line).map { it.title }

    @Test
    fun chainIsOutermostFirst() {
        assertEquals(listOf("달빛 아래의 계약", "## 제1화. 낯선 손님".removePrefix("## ")), titles(2))
    }

    @Test
    fun sceneIsCountedFromTheChapterStart() {
        assertEquals(listOf("달빛 아래의 계약", "제1화. 낯선 손님", "장면 2"), titles(4))
        assertEquals(listOf("달빛 아래의 계약", "제2화. 깨진 약속", "장면 3"), titles(10))
    }

    @Test
    fun siblingChapterReplacesThePreviousOne() {
        assertEquals(listOf("달빛 아래의 계약", "제2화. 깨진 약속"), titles(6))
    }

    @Test
    fun plainKoreanChapterHeadingsWorkWithoutMarkdown() {
        val area = TextArea("제1화. 시작\n본문\n제2화. 다음\n본문")
        assertEquals(listOf("제2화. 다음"), ScopeResolver.resolve(area, 3).map { it.title })
    }

    @Test
    fun scanIsBoundedOnHugeDocuments() {
        val area = TextArea("# 제목\n" + "문장입니다.\n".repeat(50_000))
        assertEquals(emptyList(), ScopeResolver.resolve(area, 50_000, maxScanLines = 100))
    }
}
