#import "@preview/touying:0.7.3": *
#import themes.simple: *
#import "@preview/theorion:0.6.0": *
#import cosmos.fancy: *
#show: show-theorion
#import "@preview/diagraph:0.3.7": raw-render

#let cl-orange = rgb("#fe580f")
#let cl-grapefruit = rgb("#fe4036")
#let cl-magenta = rgb("#fe2562")

#let primary-color = cl-grapefruit.darken(5%)
#let primary-color-darken = cl-grapefruit.darken(70%)
#let text-color = rgb("#3f3f46")

#show: simple-theme.with(
  aspect-ratio: "16-9",
  footer: [Texted — 소설 집필용 워드프로세서],
  primary: primary-color-darken,
)

#set text(font: "Noto Sans CJK KR", lang: "ko", size: 22pt, fill: text-color)
#show raw: set text(font: "Noto Sans Mono CJK KR")

#set page(
  // left colored bar
  background: align(left, rect(width: 10pt, height: 100%, fill: gradient.linear(
    dir: ttb,
    cl-orange,
    cl-magenta,
  ))),
)

#let simple-table(..args) = table(
  inset: 9pt,
  stroke: 0.5pt + luma(190),
  ..args,
)

#title-slide[
  = Texted

  소설 집필용 워드프로세서

  #v(2em)

  2026-neungdong-itprograming #h(1em)

  #datetime(year: 2026, month: 11, day: 27).display("[year]. [month]. [day].")
]

= 개요

== 목적

#definition[Texted][
  원고 작성, 교정, 분량 관리, 설정 관리, 연재용 파일 생성을 하나의 프로그램에서 수행하는 워드프로세서.
]

- 대상: 웹소설 작가

- 일반 문서 편집기의 한계
  - 변경 내역을 줄 단위로만 비교함
  - 맞춤법 외의 문장 문제(어미 반복, 이중 피동 등)를 검사하지 않음
  - 인물·세계관 설정을 원고와 별도로 관리해야 함

== 기능

#simple-table(
  columns: (auto, 1fr),
  table.header([*기능*], [*내용*]),
  [문장 검수], [긴 문장, 어미 반복, 따옴표 짝 오류, 낱말 반복, 이중 피동 검출],
  [분량 통계], [회차별 글자 수(공백 포함/제외), 목표 분량 달성률, 빈출 낱말],
  [설정집], [인물·세계관 카드, 인물별 등장 회차, 미등록 인물 추천],
  [찾기·바꾸기], [원고 전체 검색, 활용형 검색, 인물 이름 일괄 변경],
  [내보내기], [TXT, EPUB(전자책) 파일 생성],
  [원고 기록], [버전 자동 저장, 문장 단위 비교, 어미·조사·낱말 변경 구분],
)

= 기본 용어

== 프로그래밍 언어와 JVM

- 프로그래밍 언어: 컴퓨터가 수행할 작업을 기술하는 언어
  - 본 프로젝트는 Kotlin 2.4로 작성함

- JVM (Java Virtual Machine)
  - Java·Kotlin 프로그램을 실행하는 프로그램
  - 운영체제(Windows, macOS 등)와 무관하게 동일한 프로그램을 실행함
  - 본 프로젝트는 Java 25 환경에서 실행함

== GUI

- GUI (Graphical User Interface): 창, 버튼, 메뉴 등 그래픽 요소로 조작하는 화면

- CLI (Command Line Interface): 명령어를 문자로 입력하여 조작하는 화면

#simple-table(
  columns: (1fr, 1fr),
  table.header([*CLI*], [*GUI*]),
  [명령어 입력], [마우스·키보드로 화면 요소 조작],
  [`git commit -m "저장"`], [저장 버튼 클릭],
)

- 본 프로젝트는 GUI 프로그램임

= Swing

== 정의

#definition[Swing][
  Java 표준 라이브러리에 포함된 GUI 툴킷. 창, 버튼, 메뉴, 텍스트 입력 칸 등의 화면 구성 요소(컴포넌트)를 제공함.
]

- 컴포넌트 예: `JFrame`(창), `JButton`(버튼), `JTextArea`(텍스트 입력 칸), `JMenu`(메뉴)

- 1998년 최초 공개, Java 1.2부터 표준 포함

== AWT와의 비교

- AWT (Abstract Window Toolkit): Swing 이전의 Java GUI 툴킷

#simple-table(
  columns: (auto, 1fr, 1fr),
  table.header([], [*AWT*], [*Swing*]),
  [구현 방식], [운영체제의 컴포넌트를 사용함], [Java 코드로 직접 그림],
  [화면 모양], [운영체제마다 다름], [운영체제와 무관하게 동일함],
  [분류], [heavyweight 컴포넌트], [lightweight 컴포넌트],
)

- Swing은 AWT를 기반으로 구현됨. 최상위 창 생성 등은 AWT가 담당함.

== EDT (Event Dispatch Thread)

- 스레드: 프로그램 안에서 작업을 수행하는 실행 단위

- EDT: Swing에서 화면 그리기와 입력(클릭, 키보드) 처리를 전담하는 단일 스레드

- EDT에서 오래 걸리는 작업을 수행하면 그동안 화면이 응답하지 않음

- 따라서 시간이 오래 걸리는 작업은 별도 스레드에서 수행하고, 결과만 EDT로 전달해야 함

== 본 프로젝트의 Swing 사용 범위

- 사용한 Swing 요소
  - `JFrame`: 최상위 창
  - `SwingUtilities`: EDT에 작업 예약
  - `RootPane : JComponent`: 창마다 하나씩 두는 직접 구현 컴포넌트

- 사용하지 않은 Swing 요소
  - `JButton`, `JPanel`, `JTextArea`, `JTree`, `JTabbedPane`, `JPopupMenu`, `JDialog` 등

- 버튼, 탭, 메뉴, 대화상자, 트리, 스크롤, 편집기는 Java2D로 직접 그린 `Widget`으로 구현함

== 화면 구성

#align(center)[
  #raw-render(
    ```dot
    digraph {
      rankdir=TB;
      node [shape=box, style="rounded,filled", fontsize=16];
      F [label="JFrame (Swing)", fillcolor="#ffe4d6"];
      R [label="RootPane : JComponent (Swing)", fillcolor="#ffe4d6"];
      W [label="Widget 트리 (Java2D)", fillcolor="#dbeafe"];
      E [label="편집기", fillcolor="#dbeafe"];
      M [label="메뉴", fillcolor="#dbeafe"];
      P [label="패널", fillcolor="#dbeafe"];
      B [label="버튼·탭", fillcolor="#dbeafe"];
      F -> R -> W;
      W -> E; W -> M; W -> P; W -> B;
    }
    ```,
    height: 52%,
  )
]

#text(size: 18pt)[주황: Swing 컴포넌트 · 파랑: 직접 구현한 Widget]

- 직접 구현한 이유: Material 3 디자인 규격 적용, 대용량 원고의 렌더링 비용 제어

= 사용 기술

== 기술 스택

#text(size: 18pt)[
  #simple-table(
    columns: (auto, auto, 1fr),
    table.header([*구분*], [*기술*], [*용도*]),
    [언어·런타임], [Kotlin 2.4, Java 25], [프로그램 작성 및 실행],
    [빌드], [Gradle (Kotlin DSL)], [소스 코드 컴파일, 의존성 관리],
    [GUI], [Swing, Java2D], [창 생성, 화면 그리기],
    [형태소 분석], [Kiwi v0.24.0 (JNI)], [한국어 문장 분석],
    [버전 관리], [Eclipse JGit 7.7], [원고 변경 기록 저장],
    [직렬화], [kotlinx-serialization-json], [설정 파일(JSON) 읽기·쓰기],
    [동시성], [JCTools `MpscArrayQueue`], [다시 그릴 영역 목록 관리],
    [메모리], [Java FFM (`java.lang.foreign`)], [JVM 힙 외부 메모리에 원고 저장],
  )
]

== 형태소 분석 (Kiwi)

- 형태소: 의미를 가진 가장 작은 말의 단위

#align(center)[
  #simple-table(
    columns: 9,
    [민준], [이], [가], [밥], [을], [먹], [었], [다], [.],
    [NNP], [XSN], [JKS], [NNG], [JKO], [VV], [EP], [EF], [SF],
  )
]

#text(size: 18pt)[NNP 고유명사 · JKS 주격 조사 · NNG 일반명사 · JKO 목적격 조사 · VV 동사 · EP 선어말 어미 · EF 종결 어미]

- 활용형을 기본형으로 환원함: 먹었다·먹고·먹는 → `먹다`

- Kiwi: C++로 구현된 한국어 형태소 분석기. JNI를 통해 Java에서 호출함.

- 설정집의 인물 이름·별칭을 사용자 사전(고유명사)에 자동 등록함

== 버전 관리 (JGit)

- Git: 파일 변경 이력을 기록하는 버전 관리 시스템

- JGit: Java로 구현된 Git

- 저장(`Ctrl+S`) 시 원고 상태를 자동으로 기록함

- 비교 단위
  - 일반적인 Git: 줄 단위
  - 본 프로젝트: 문장 단위, 형태소 단위

- 변경 유형 표시: `[어미]` `[조사]` `[낱말] 문 → 창문`

== 편집기 구조

- 갭 버퍼 (Gap Buffer)
  - 커서 위치에 빈 공간을 확보해 두는 텍스트 저장 방식
  - 커서 위치에서의 삽입·삭제 시 뒤쪽 문자를 이동하지 않음

- 가상 스크롤 (Virtual Scrolling)
  - 화면에 보이는 줄만 그림
  - 처리 비용이 원고 전체 길이가 아닌 화면 크기에 비례함

- 부분 갱신 (Dirty Region)
  - 변경된 영역만 다시 그림

- 힙 외부 버퍼 (`NativeTextBuffer`)
  - 원고를 JVM 힙 외부 메모리에 저장하여 가비지 컬렉션 부담을 줄임

== 성능 측정 결과

#simple-table(
  columns: (1fr, auto, auto),
  table.header([*항목*], [*100만 자*], [*1,000만 자*]),
  [원고 불러오기], [50 ms], [103 ms],
  [힙 메모리 사용량], [7.1 MB], [42.3 MB],
  [스크롤 1프레임 (p95)], [4.16 ms], [2.92 ms],
  [글자 입력 → 화면 반영 (p95)], [3.44 ms], [2.03 ms],
  [실행 취소 → 화면 반영 (p95)], [2.15 ms], [4.28 ms],
)

- p95: 전체 측정값 중 95%가 이 값 이하임

- 60 fps 기준 1프레임 허용 시간: 약 16.7 ms

#text(size: 16pt, fill: luma(120))[측정 환경: CPU 4개, JDK 25, 2026-10-02 (`docs/EDITOR_PERFORMANCE.md`)]

== 병렬 처리

- 형태소 분석 소요 시간: 1줄당 약 1 ms

- `Parallel.map`: 작업을 CPU 코어 수만큼 나누어 동시에 처리함
  - 2,000줄 기준 4\~6배 단축

- `BackgroundWork`: 원고 전체 분석을 EDT 외부 스레드에서 수행하고 결과만 EDT로 전달함
  - 분석 중에도 화면이 응답함

== 검수 규칙과 지시문

- 맞춤법 검사: 자주 틀리는 표기(됬다, 몇일, 할께 등) 목록 기반 검출

- 지시문 `@Suppress`: 지정한 범위의 검사를 끔

#simple-table(
  columns: (auto, auto, auto),
  table.header([*위치*], [*예*], [*적용 범위*]),
  [파일 맨 위], [`@file:Suppress("맞춤법")`], [파일 전체],
  [단독 줄], [`@Suppress("긴 문장")`], [다음 줄],
  [문장 앞], [`@Suppress("맞춤법") 됬다고 했다.`], [해당 문장],
)

- 지시문은 글자 수 집계, 내보내기, 검색 대상에서 제외됨

= 현황

== 현재 상태

- 버전 0.1.0 (빌드 261029)

- 작업 1\~6의 1차(글자 단위)·2차(형태소 단위) 구현 완료

- 자동 테스트 197개 통과

- 라이선스: GNU GPL v3.0

== 알려진 한계

- 맞춤법·이중 피동 검사는 목록 방식이므로 목록에 없는 오류는 검출하지 못함

- Kiwi의 분석 결과가 앞뒤 문맥에 따라 달라짐 (사전에 없는 이름에서 두드러짐)

- `.md` → `.episode` 확장자 변경 시 `# 제목` 줄이 본문으로 남음

- 프로그램 재실행 시 글꼴 크기가 기본값(16pt)으로 돌아감

#focus-slide[
  #text(size: 48pt)[
    질의응답
  ]
]
