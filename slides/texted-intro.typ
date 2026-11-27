#import "@preview/touying:0.7.3": *
#import themes.simple: *
#import "@preview/theorion:0.6.0": *
#import cosmos.fancy: *
#show: show-theorion

#show: simple-theme.with(
  aspect-ratio: "16-9",
  footer: [Texted — 소설 쓰는 사람을 위한 워드프로세서],
)

// 한글 글꼴: 설치된 것 중 앞에서부터 사용
#set text(font: ("Pretendard", "Noto Sans CJK KR", "Malgun Gothic", "Apple SD Gothic Neo"), lang: "ko", size: 20pt)
#show raw: set text(font: ("JetBrains Mono", "D2Coding", "Noto Sans Mono CJK KR"))

#let accent = rgb("#3f83a6")
#let hl(body) = text(fill: accent, weight: "bold", body)

#title-slide[
  = Texted

  소설 쓰는 사람을 위한 워드프로세서

  #v(2em)

  2026-neungdong-itprograming #h(1em)

  #datetime(year: 2026, month: 11, day: 27).display("[year]. [month]. [day].")
]

= 무엇을 만드나요?

== 한 줄 소개

- 개발자에게 Git과 IntelliJ가 있듯, #hl[웹소설 작가에게 서사 통제실(IDE)]을.

- 원고를 쓰고, 고치고, 분량을 맞추고, 설정을 챙기고, 연재처에 올리는 일을 #hl[한 프로그램 안에서].

- 기존 에디터의 한계를 넘어서기
  - 줄 단위 Diff → #hl[문장·형태소 단위] 비교
  - 오탈자 위주 검수 → #hl[어미 반복·이중 피동·따옴표] 검수
  - 파편화된 설정집 → 원고와 연결된 #hl[인물·세계관 카드]

== 현재 상태

#definition[Texted 0.1.0 (빌드 261029)][
  + 작업 1\~6의 1차(글자 단위)·2차(형태소 단위) 할 일 완료
  + 테스트 197개 통과
  + 라이선스: GNU GPL v3.0
]

= 쓰인 기술

== 기술 스택 한눈에

#text(size: 15pt)[#table(
  columns: (auto, 1fr, 1fr),
  inset: 8pt,
  stroke: 0.5pt + luma(180),
  table.header([*구분*], [*기술*], [*역할*]),
  [언어 / 런타임], [Kotlin 2.4 · Java 25], [JVM 위 크로스 플랫폼 데스크톱 앱],
  [빌드], [Gradle (Kotlin DSL) · buildSrc 컨벤션 플러그인], [의존성·툴체인 관리, 벤치마크 태스크],
  [GUI], [Swing/AWT 브리지 + Java2D 자체 Widget], [Material 3 토큰으로 직접 그리는 UI],
  [형태소 분석], [Kiwi v0.24.0 (JNI)], [한국어 형태소 분석, 사용자 사전],
  [버전 관리], [Eclipse JGit 7.7], [자동 커밋, 원고 기록],
  [직렬화], [kotlinx-serialization-json], [프로젝트 설정·도킹 레이아웃·LLM 요청],
  [동시성], [JCTools `MpscArrayQueue`], [다시 그릴 영역(dirty region) 큐],
  [대용량 버퍼], [Java FFM (`java.lang.foreign`)], [힙 밖에 원고 저장, GC 부담 제거],
)
]

== GUI: Swing은 "창"까지만

- Swing에서 쓰는 것은 딱 세 가지
  - 최상위 `JFrame`
  - EDT 예약용 `SwingUtilities`
  - 창마다 하나씩 두는 직접 구현 `RootPane : JComponent`

- `JButton`, `JPanel`, `JTextArea`, `JTree`, `JDialog` … #hl[쓰지 않습니다.]

- 버튼·탭·메뉴·다이얼로그·트리·스크롤·편집기·도킹 UI 전부
  - #hl[Java2D로 그리는 자체 `Widget` 트리]
  - Material 3 테마: Light / Dark / Space

== 편집기 코어

- #hl[갭 버퍼(Gap Buffer)] + 줄 인덱스
  - 커서 근처 삽입·삭제가 빠름

- #hl[Virtual Scrolling & Custom Painting]
  - 화면에 보이는 줄만 처리 → 비용이 문서 길이가 아닌 viewport에 비례

- #hl[Off-heap 버퍼] (`NativeTextBuffer`)
  - `java.lang.foreign.MemorySegment`에 UTF-16으로 저장
  - 10M자 ≈ 20MB를 힙 밖에 → GC가 원고를 스캔·복사하지 않음

- 다시 그릴 영역만 모아서 그리기: `DirtyRegionTracker` + `RenderScheduler`

== 성능 측정 (4 CPU, JDK 25)

#table(
  columns: (1fr, auto, auto),
  inset: 8pt,
  stroke: 0.5pt + luma(180),
  table.header([*항목*], [*1,000,000자*], [*10,000,000자*]),
  [로드 (갭 버퍼 + 줄 인덱스)], [50 ms], [103 ms],
  [힙 사용량], [7.1 MB], [42.3 MB],
  [스크롤 프레임 p95], [4.16 ms], [2.92 ms],
  [제자리 입력 → 프레임 p95], [3.44 ms], [2.03 ms],
  [실행 취소 → 프레임 p95], [2.15 ms], [4.28 ms],
)

- 문서가 10배 커져도 프레임 시간이 거의 그대로 = #hl[보이는 줄만 처리]

- `EditorPerformanceTest`가 "한 프레임이 문서 전체를 읽지 않는다"를 직접 검증

== 형태소 분석: Kiwi

- C++ 기반 한국어 형태소 분석기를 #hl[JNI 바인딩]으로 사용

```kotlin
Morphology.analyze("민준이가 밥을 먹었다.")
// 민준/NNP 이/XSN 가/JKS 밥/NNG 을/JKO 먹/VV 었/EP 다/EF ./SF
```

- "먹었다 / 먹고 / 먹는" → 모두 기본형 `먹다`

- 설정집 인물 이름·별칭을 #hl[사용자 사전(NNP)]에 자동 등록

- 모델(약 88MB)은 처음 쓸 때 자동 다운로드 후 캐시에 재사용

== 데이터 파이프라인

#align(center)[
  #table(
    columns: (auto, 1fr),
    inset: 8pt,
    stroke: 0.5pt + luma(180),
    [*소스 원고*], [`.episode` · `.character` · `.world` (+ `.md`, `.txt`)],
    [*1. Lexer*], [Kiwi + 사용자 사전 → 형태소·고유명사 토큰화],
    [*2. Parser*], [문장 / 문단 / 인물 / 사건 구조화],
    [*3. Linter*], [검수 규칙 + 맞춤법 검사, `@Suppress` 지시문 반영],
    [*4. Target*], [JGit 스냅샷 저장, TXT / EPUB 출판 빌드],
  )
]

- 원고 전체 분석은 화면 스레드에서 하지 않음
  - `Parallel.map`으로 모든 코어에 분산, `BackgroundWork`로 결과만 화면에

= 기능

== 여섯 가지 작업

#table(
  columns: (auto, auto, 1fr),
  inset: 8pt,
  stroke: 0.5pt + luma(180),
  table.header([*\#*], [*작업*], [*작가에게 주는 것*]),
  [1], [문장 검수], [긴 문장·어미 반복·따옴표 실수·낱말 반복·이중 피동 알림],
  [2], [분량 통계], [회차별 글자수, 목표 분량 달성률, 자주 쓴 낱말],
  [3], [설정집], [인물·세계관 카드, 인물 등장 회차, 추천 인물],
  [4], [찾기·바꾸기], [원고 전체 검색, 활용형 검색, 인물 이름 한 번에 바꾸기],
  [5], [내보내기], [연재처용 TXT, 전자책 EPUB, 키워드 태그],
  [6], [원고 기록], [이전 버전 보기, 문장 단위 비교, 어미·조사·낱말 구분],
)

== 검수와 지시문 `@`

- 자주 틀리는 맞춤법(됬다, 몇일, 할께 …)에 빨간 물결 밑줄

- 지시문으로 검사를 끌 수 있음 — 원고에는 포함되지 않음

#table(
  columns: (auto, auto, auto),
  inset: 8pt,
  stroke: 0.5pt + luma(180),
  table.header([*쓰는 곳*], [*예*], [*범위*]),
  [파일 맨 위], [`@file:Suppress("맞춤법")`], [파일 전체],
  [혼자 있는 줄], [`@Suppress("긴 문장")`], [다음 줄],
  [문장 앞], [`@Suppress("맞춤법") 됬다고 했다.`], [그 문장],
)

== 원고 기록과 내보내기

- #hl[원고 기록] (JGit)
  - `Ctrl+S` → 저장 + 기록 남기기 + 검수·분량 갱신
  - 바뀐 문장이 무엇이 바뀐 것인지 표시: `[어미]` `[조사]` `[낱말] 문 → 창문`

- #hl[내보내기]
  - `Ctrl+Shift+E`: TXT · EPUB (`java.util.zip`으로 직접 패키징, 키워드 태그 포함)
  - `Ctrl+Shift+C`: 지금 회차 본문 복사 (연재처 붙여넣기용)

- #hl[AI 보조 (선택)]
  - OpenAI 호환 Chat Completions 주소를 설정하면 `Ctrl+Shift+R`로 문장 다듬기
  - 실행 취소 한 번으로 되돌림

= 앞으로

== 알려진 한계

- 맞춤법·이중 피동은 #hl[목록 방식] — 목록에 없는 실수는 못 잡음

- Kiwi 분석이 앞뒤 문맥에 따라 달라짐 (사전에 없는 이름일 때 특히)

- 해시태그 한 줄을 회차 복사에 붙이는 기능은 아직

- `.md` → `.episode` 변환 시 `# 제목` 줄이 본문으로 남음

- 앱을 다시 켜면 글꼴 크기가 기본(16pt)으로 돌아감

#focus-slide[
  감사합니다
]
