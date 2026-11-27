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

#let caption(body) = align(center, text(size: 16pt, fill: luma(110), body))

#let shot(path, height: 66%) = align(center, box(
  stroke: 0.5pt + luma(160),
  image(path, height: height),
))

// 터미널 화면 표시용
#let term-green = rgb("#4ec94e")
#let term-blue = rgb("#6cb6ff")
#let prompt(cmd) = [#text(fill: term-green)[writer\@pc]:#text(fill: term-blue)[\~/별빛서점]\$ #cmd]
#let terminal(body) = block(
  width: 100%,
  fill: rgb("#1e1e1e"),
  radius: 6pt,
  inset: 12pt,
  text(font: "Noto Sans Mono CJK KR", size: 14pt, fill: rgb("#d4d4d4"), body),
)

#title-slide[
  = Texted

  소설 집필용 워드프로세서

  #v(1em)

  #box(image("assets/kotlin.svg", height: 1.4em)) #h(0.8em) #box(image("assets/java.svg", height: 1.6em))

  #v(1em)

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

== 실행 화면

#shot("assets/editor.png")

#caption[왼쪽: 원고 목록 · 가운데: 편집기 · 아래: 검수 결과(맞춤법 오류 1, 긴 문장 경고 1)]

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

== 프로그래밍 언어

#grid(
  columns: (1fr, 1fr),
  gutter: 24pt,
  align(center)[
    #image("assets/kotlin.svg", height: 2.2em)
    #v(0.3em)
    *Kotlin*
    #v(0.2em)
    #text(size: 18pt)[JetBrains가 개발한 언어. \ 본 프로젝트의 주 언어 (2.4)]
  ],
  align(center)[
    #image("assets/java.svg", height: 2.5em)
    #v(0.1em)
    *Java*
    #v(0.2em)
    #text(size: 18pt)[JVM의 표준 언어. \ 표준 라이브러리(Swing 등)를 Kotlin에서 그대로 사용 (25)]
  ],
)

#v(0.3em)

#text(size: 19pt)[
  - 프로그래밍 언어: 컴퓨터가 수행할 작업을 기술하는 언어
  - JVM (Java Virtual Machine): Java·Kotlin 프로그램을 실행하는 프로그램. 운영체제와 무관하게 동일한 프로그램을 실행함
]

== CLI 예시

- CLI (Command Line Interface): 명령어를 문자로 입력하고 결과를 문자로 출력하는 화면

#terminal[
  #prompt[ls manuscript] \
  '01. 낯선 손님.episode' #h(1em) '02. 오래된 약속.episode' \
  #prompt[git log \-\-oneline] \
  #text(fill: rgb("#d7ba7d"))[132d67b] (HEAD -> main) 1화 수정 \
  #text(fill: rgb("#d7ba7d"))[9f7e240] 사전 \
  #text(fill: rgb("#d7ba7d"))[bf451d5] 프로젝트 생성: 별빛 서점 \
  #prompt[head \-c 120 "manuscript/01. 낯선 손님.episode"] \
  비가 그친 저녁이었다. 서연은 서점 문을 닫으려다 말고 손을 멈췄다. \
  \
  "아직 영업하나요?"
]

#caption[예시 원고 폴더에서 실제로 실행한 명령과 출력]

== GUI 예시

- GUI (Graphical User Interface): 창, 버튼, 메뉴 등 그래픽 요소를 마우스·키보드로 조작하는 화면

#shot("assets/stats.png", height: 58%)

#caption[같은 원고 폴더를 Texted로 연 화면 (왼쪽: 분량 패널)]

== CLI와 GUI 비교

#simple-table(
  columns: (auto, 1fr, 1fr),
  table.header([], [*CLI*], [*GUI*]),
  [입력], [명령어 문자열], [마우스 클릭, 단축키, 텍스트 입력],
  [출력], [문자], [창, 목록, 강조 표시, 그래프 등],
  [변경 기록 조회], [`git log`, `git diff` 명령], [기록 패널에서 항목 선택],
  [학습 비용], [명령어를 외워야 함], [화면에 보이는 요소를 선택함],
)

- 본 프로젝트는 GUI 프로그램이며, 내부적으로 Git(CLI 도구와 같은 저장소 형식)을 사용함

= Swing

== 정의

#definition[Swing][
  Java 표준 라이브러리에 포함된 GUI 툴킷. 창, 버튼, 메뉴, 텍스트 입력 칸 등의 화면 구성 요소(컴포넌트)를 제공함.
]

- 컴포넌트 예: `JFrame`(창), `JButton`(버튼), `JTextArea`(텍스트 입력 칸), `JMenu`(메뉴)

- 1998년 최초 공개, Java 1.2부터 표준 포함

- 같은 Swing 기반 프로그램 예: IntelliJ IDEA (JetBrains의 IDE)

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

= 라이브러리

== 사용 라이브러리 목록

- 라이브러리: 다른 개발자가 만들어 공개한 기능 묶음. 직접 구현하지 않고 가져다 씀

#text(size: 16pt)[
  #simple-table(
    columns: (auto, auto, 1fr),
    table.header([*라이브러리*], [*분류*], [*본 프로젝트에서 구현한 기능*]),
    [Kiwi v0.24.0], [한국어 형태소 분석], [문장 검수, 활용형 검색, 빈출 낱말, 추천 인물, 변경 유형 구분],
    [Eclipse JGit 7.7], [Git (버전 관리)], [저장 시 자동 기록, 기록 목록, 이전 버전 조회],
    [kotlinx-serialization-json], [JSON 읽기·쓰기], [프로젝트 정보, 사용자 사전, 패널 배치 저장],
    [JCTools 4.0.7], [동시성 자료구조], [다시 그릴 화면 영역 요청 큐],
    [Java 표준 라이브러리], [GUI, 압축 등], [창(Swing), 화면 그리기(Java2D), EPUB 생성(`java.util.zip`)],
    [Gradle], [빌드 도구], [컴파일, 의존성 관리, 성능 측정 작업],
  )
]

== Kiwi — 한국어 형태소 분석기

- 형태소: 의미를 가진 가장 작은 말의 단위

#align(center)[
  #simple-table(
    columns: 9,
    [민준], [이], [가], [밥], [을], [먹], [었], [다], [.],
    [NNP], [XSN], [JKS], [NNG], [JKO], [VV], [EP], [EF], [SF],
  )
]

#text(size: 16pt)[NNP 고유명사 · JKS 주격 조사 · NNG 일반명사 · JKO 목적격 조사 · VV 동사 · EP 선어말 어미 · EF 종결 어미]

- C++로 구현됨. JNI(Java Native Interface)를 통해 Kotlin에서 호출함

- 설정집의 인물 이름·별칭을 사용자 사전(고유명사)에 자동 등록함

== Kiwi로 구현한 기능

#simple-table(
  columns: (auto, 1fr),
  table.header([*기능*], [*형태소 분석 결과의 사용 방법*]),
  [어미 반복 검사], [연속된 문장 끝의 선어말 어미(EP)+종결 어미(EF) 비교 (했다·갔다 → `었`+`다`)],
  [이중 피동 검사], [피동 동사(열리다·보이다·되다 등) 뒤의 "-어지다" 검출 (열려졌다)],
  [활용형 검색], [먹었다·먹고·먹는 → 기본형 `먹다`로 환원하여 비교],
  [빈출 낱말], [조사·어미를 제외한 실질 형태소(명사·동사·형용사·부사)만 집계],
  [추천 인물], [고유명사(NNP) 중 설정집에 없는 이름 추출],
  [원고 기록], [바뀐 형태소의 품사로 `[어미]` `[조사]` `[낱말]` 구분],
)

== JGit — Git의 Java 구현

- Git: 파일 변경 이력을 기록하는 버전 관리 시스템. 원래 CLI 프로그램임

- JGit: Git 저장소를 Java 코드에서 직접 다루는 라이브러리. 별도의 Git 프로그램 설치가 필요 없음

- 구현한 기능
  - `commitAll`: 저장(`Ctrl+S`) 시 원고 전체를 자동 기록
  - `log`: 기록 목록 조회
  - `fileAt`: 특정 기록 시점의 원고 내용 조회 → 현재 원고와 문장 단위로 비교

- 저장 형식이 일반 Git과 같으므로 CLI의 `git log`로도 같은 기록을 볼 수 있음

== 원고 기록 화면

#shot("assets/history.png")

#caption[왼쪽 위: 기록 목록 · 왼쪽 아래: 선택한 기록과 현재 원고의 문장 단위 비교 (− 삭제 / + 추가)]

== kotlinx-serialization · JCTools

- kotlinx-serialization-json: Kotlin 객체와 JSON 문자열 사이의 변환
  - `texted.project.json`: 작품 제목, 작가, 형식 버전
  - `custom-rules.json`: 사용자 사전
  - 패널 배치(도킹 레이아웃) 저장·복원

- JCTools: 여러 스레드가 동시에 사용하는 고성능 큐(queue) 제공
  - `MpscArrayQueue` (Multi-Producer Single-Consumer)
  - 여러 스레드가 "이 영역을 다시 그려야 함" 요청을 잠금 없이 추가하고, EDT 하나만 꺼내서 처리함

= 다른 편집기와의 비교

== 비교 대상

#text(size: 15pt)[
  #simple-table(
    columns: (auto, 1fr, 1fr, 1fr, 1fr),
    table.header([], [*Texted*], [*IntelliJ IDEA*], [*VS Code*], [*Obsidian*]),
    [용도], [소설 집필], [프로그램 개발(IDE)], [코드 편집], [노트·지식 관리],
    [GUI 기술], [Swing + Java2D], [Swing], [Electron], [Electron],
    [문서 형식], [순수 텍스트], [소스 코드], [소스 코드], [Markdown],
    [검사 대상], [한국어 문장 (형태소)], [코드 문법·오류], [코드 문법·오류], [—],
    [변경 기록], [자동 기록, 문장 단위], [Git 연동, 줄 단위], [Git 연동, 줄 단위], [파일 복구 스냅샷 (로컬, 기본 7일 보관)],
  )
]

#text(size: 15pt)[
  - IDE (Integrated Development Environment): 코드 작성·검사·실행·버전 관리를 한 프로그램에서 수행하는 개발 도구
  - Electron: HTML·CSS·JavaScript(웹 기술)로 데스크톱 프로그램을 만드는 프레임워크
]

== IntelliJ IDEA와 같은 점

#simple-table(
  columns: (auto, 1fr, 1fr),
  table.header([*항목*], [*IntelliJ IDEA*], [*Texted*]),
  [GUI 기반], [Swing], [Swing (창만 사용)],
  [문제 목록], [Problems 창], [Problems 패널],
  [문제 이동], [`F2` / `Shift+F2`], [`F2` / `Shift+F2`],
  [검사 끄기], [`@Suppress("...")` (Kotlin)], [`@Suppress("맞춤법")`],
  [화면 배치], [도구 창 도킹], [패널 도킹, 떼어 내기],
  [버전 관리], [Git 연동], [JGit 자동 기록],
)

== IntelliJ IDEA와 다른 점

#simple-table(
  columns: (auto, 1fr, 1fr),
  table.header([*항목*], [*IntelliJ IDEA*], [*Texted*]),
  [다루는 대상], [프로그래밍 언어의 문법 (변수, 함수, 클래스)], [한국어 문장 (형태소, 어미, 조사)],
  [비교 단위], [줄 단위 Diff], [문장 단위 Diff, 바뀐 형태소의 품사 표시],
  [화면 구현], [Swing 컴포넌트를 확장한 자체 컴포넌트], [Swing 컴포넌트 미사용, Java2D로 직접 그림],
  [기록 방식], [사용자가 커밋 시점과 메시지를 지정], [저장할 때마다 자동 기록],
)

== Obsidian과의 비교

#simple-table(
  columns: (auto, 1fr, 1fr),
  table.header([*항목*], [*Obsidian*], [*Texted*]),
  [저장 방식], [내 컴퓨터 폴더의 일반 파일 (같음)], [내 컴퓨터 폴더의 일반 파일 (같음)],
  [문서 단위], [파일 하나 = 노트 하나 (같음)], [파일 하나 = 회차·인물·세계관 하나 (같음)],
  [문서 간 연결], [링크·백링크, 그래프 뷰], [인물 카드 ↔ 등장 회차],
  [문서 형식], [Markdown], [마크업 없는 순수 텍스트],
  [목적], [범용 노트·지식 관리], [소설 원고 전용],
  [고유 기능], [플러그인 확장], [한국어 문장 검수, 분량 통계, 자동 버전 기록, EPUB 출력],
)

= 성능

== 편집기 구조

- 갭 버퍼 (Gap Buffer)
  - 커서 위치에 빈 공간을 확보해 두는 텍스트 저장 방식
  - 커서 위치에서의 삽입·삭제 시 뒤쪽 문자를 이동하지 않음

- 가상 스크롤 (Virtual Scrolling)
  - 화면에 보이는 줄만 그림
  - 처리 비용이 원고 전체 길이가 아닌 화면 크기에 비례함

- 부분 갱신 (Dirty Region)
  - 변경된 영역만 다시 그림 (JCTools 큐 사용)

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

- Kiwi JNI 바인딩은 현재 Windows용만 포함됨. 다른 운영체제는 해당 플랫폼용 jar로 교체 필요

- 프로그램 재실행 시 글꼴 크기가 기본값(16pt)으로 돌아감

#focus-slide[
  #text(size: 48pt)[
    질의응답
  ]
]
