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
#let accent = rgb("#0074d9")

#show: simple-theme.with(
  aspect-ratio: "16-9",
  footer: [Texted 소개 — 소설 쓰는 사람을 위한 워드프로세서],
  primary: primary-color-darken,
)

#set text(font: ("Noto Sans CJK KR"), lang: "ko", size: 22pt, fill: text-color)
#show raw: set text(font: ("Noto Sans Mono CJK KR"))
#show strong: set text(fill: primary-color)

#let hl(body) = text(fill: accent, weight: "bold", body)

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

  소설 쓰는 사람을 위한 워드프로세서

  #v(2em)

  2026-neungdong-itprograming #h(1em)

  #datetime(year: 2026, month: 11, day: 27).display("[year]년 [month padding:none]월 [day padding:none]일")
]

== 오늘 이야기할 것

+ Texted가 뭔가요?
+ 먼저 알아 둘 말 — 프로그램, 화면(GUI)
+ Swing이 뭔가요?
+ 어떤 기술을 썼나요?
+ 정리

= Texted가 뭔가요?

== 웹소설 작가의 고민

- 연재가 길어지면 회차가 #hl[수백 화]

- "3화에서 이 인물 눈 색깔이 뭐였더라?"

- "같은 말을 너무 자주 쓰는 것 같은데…"

- "어제 지운 문단을 다시 살리고 싶다!"

- 다 쓰고 나면 연재 사이트에 올릴 파일도 따로 만들어야 함

== 그래서 만들었습니다

- 프로그래머에게는 코드를 쓰고 · 검사하고 · 기록하는 #hl[전용 도구]가 있습니다.

- 작가에게도 그런 도구가 있으면 어떨까?

#definition[Texted][
  원고를 #strong[쓰고], #strong[고치고], #strong[분량을 맞추고], #strong[설정을 챙기고], #strong[연재처에 올리는] 일을 한 프로그램 안에서 끝내는 워드프로세서
]

== Texted로 할 수 있는 일

#simple-table(
  columns: (auto, 1fr),
  table.header([*기능*], [*작가에게 주는 것*]),
  [문장 검수], [너무 긴 문장, 같은 끝말("\~했다") 반복, 따옴표 실수를 알려 줌],
  [분량 통계], [회차마다 몇 글자인지, 목표 분량을 얼마나 채웠는지],
  [설정집], [인물·세계관 카드, 이 인물이 몇 화에 나왔는지],
  [찾기·바꾸기], ["먹다"로 찾으면 먹었다·먹고·먹는까지 찾아 줌],
  [내보내기], [연재 사이트에 붙여 넣을 글, 전자책 파일(EPUB)],
  [원고 기록], [예전 버전 보기, 어느 문장이 어떻게 바뀌었는지],
)

= 먼저 알아 둘 말

== 프로그램은 무엇으로 만드나요?

- #hl[프로그래밍 언어]: 컴퓨터에게 할 일을 적어 주는 글
  - Texted는 #strong[Kotlin] (코틀린)으로 썼습니다.

- Kotlin으로 쓴 프로그램은 #hl[JVM] (자바 가상 머신) 위에서 돌아갑니다.
  - JVM = 어느 컴퓨터에서나 같은 방식으로 프로그램을 돌려 주는 "통역사"
  - 그래서 윈도우에서도, 맥에서도 같은 프로그램이 돌아갑니다.

- #hl[Java] (자바)는 JVM에서 돌아가는 또 하나의 언어로, Kotlin과 섞어 쓸 수 있습니다.

== 화면(GUI)이란?

#simple-table(
  columns: (1fr, 1fr),
  table.header([*CLI* — 글자로 명령], [*GUI* — 그림으로 조작]),
  [검은 창에 명령어를 직접 타이핑], [창, 버튼, 메뉴를 마우스로 클릭],
  [`git commit -m "저장"`], [\[저장\] 버튼 클릭],
  [개발자에게 익숙함], [누구나 쓰기 쉬움],
)

- GUI = Graphical User Interface (그래픽 사용자 인터페이스)

- 작가가 쓰는 프로그램이니 당연히 #hl[GUI]가 필요합니다.

- 그런데 창과 버튼은 어떻게 만들까요? → #hl[Swing]

= Swing이 뭔가요?

== Swing

#definition[Swing][
  Java에 #strong[기본으로 들어 있는 GUI 도구 상자]. 창·버튼·메뉴·글 입력 칸 같은 화면 부품을 미리 만들어 둔 것
]

- 부품 이름은 J로 시작합니다: `JFrame`(창), `JButton`(버튼), `JTextArea`(글 입력 칸), `JMenu`(메뉴) …

- 레고 블록처럼 부품을 끼워 맞추면 화면이 완성됩니다.

- 1998년에 처음 공개됐고, Java 1.2부터 Java에 기본으로 들어 있습니다.

== Swing 이전: AWT

- Java의 첫 GUI 도구는 #hl[AWT] (Abstract Window Toolkit)였습니다.

#simple-table(
  columns: (auto, 1fr, 1fr),
  table.header([], [*AWT*], [*Swing*]),
  [부품을 누가 그리나], [운영체제(윈도우·맥)의 부품을 빌려 씀], [Java가 직접 그림],
  [모양], [운영체제마다 다르게 보임], [어디서나 똑같이 보임],
  [별명], [무거운(heavyweight) 부품], [가벼운(lightweight) 부품],
)

- Swing은 AWT 위에 지어졌습니다. 창을 띄우는 일 같은 바닥 일은 여전히 AWT가 합니다.

== 화면 담당 일꾼은 한 명뿐

- Swing에는 화면을 그리고 클릭·키보드 입력을 처리하는 #hl[전용 일꾼(스레드)]이 하나 있습니다.
  - 이름: EDT (Event Dispatch Thread)

- 이 일꾼에게 오래 걸리는 일을 시키면?
  - 그동안 화면이 #strong[멈춥니다]. (버튼을 눌러도 반응 없음)

- 그래서 원고 전체 분석 같은 무거운 일은 #hl[다른 일꾼]에게 맡기고, 결과만 화면 일꾼에게 넘겨 줍니다.

== Texted는 Swing을 이렇게 썼습니다

- Swing에서 빌린 것은 딱 #strong[두 가지]
  - 창 (`JFrame`)
  - 창마다 빈 도화지 한 장 (`RootPane`)

- 버튼·메뉴·탭·편집기는 Swing 부품(`JButton` 등)을 #strong[쓰지 않고], 도화지 위에 #hl[직접 그렸습니다] (Java2D).

- 비유: 문구점에서 완성된 스티커를 사는 대신, #hl[빈 도화지를 사서 직접 그림]

- 왜? 원하는 모양(Material 3 디자인)을 마음대로 만들고, 아주 긴 원고도 빠르게 보여 주려고

#pagebreak()

#align(center)[
  #raw-render(
    ```dot
    digraph {
      rankdir=TB;
      node [shape=box, style="rounded,filled", fontsize=16];
      F [label="창 (JFrame) — Swing", fillcolor="#ffe4d6"];
      R [label="도화지 (RootPane) — Swing", fillcolor="#ffe4d6"];
      W [label="직접 만든 부품 묶음 (Widget)", fillcolor="#dbeafe"];
      E [label="편집기", fillcolor="#dbeafe"];
      M [label="메뉴", fillcolor="#dbeafe"];
      P [label="패널 (원고·찾기·설정…)", fillcolor="#dbeafe"];
      B [label="버튼·탭", fillcolor="#dbeafe"];
      F -> R -> W;
      W -> E; W -> M; W -> P; W -> B;
    }
    ```,
    width: 50%,
  )
]

주황색: Swing에서 빌린 것 · 파란색: 직접 그린 것

= 어떤 기술을 썼나요?

== 기술 한눈에

#text(size: 18pt)[
  #simple-table(
    columns: (auto, auto, 1fr),
    table.header([*하는 일*], [*기술*], [*쉽게 말하면*]),
    [언어], [Kotlin 2.4, Java 25], [프로그램을 쓴 글],
    [화면], [Swing + Java2D], [창은 빌리고, 안쪽은 직접 그림],
    [형태소 분석], [Kiwi], [문장을 뜻 단위로 쪼개는 도구],
    [원고 기록], [JGit], [자동 세이브 포인트],
    [설정 저장], [kotlinx-serialization], [설정을 파일(JSON)로 저장·읽기],
    [빠른 그리기], [JCTools], [다시 그릴 곳 목록을 여러 일꾼이 안전하게 공유],
    [아주 긴 원고], [Java FFM], [원고를 JVM 바깥 메모리에 보관],
    [조립·빌드], [Gradle], [여러 코드 파일을 하나의 프로그램으로 묶음],
  )
]

== 형태소 분석: Kiwi

- #hl[형태소]: 뜻을 가진 가장 작은 말의 단위 (국어 시간에 배운 그것!)

#align(center)[
  #simple-table(
    columns: 9,
    [민준], [이], [가], [밥], [을], [먹], [었], [다], [.],
    [이름], [], [조사], [명사], [조사], [동사], [과거], [끝말], [],
  )
]

- 그래서 "먹었다 · 먹고 · 먹는"이 모두 #hl[`먹다`] 한 낱말인 걸 압니다.
  - → "먹다"로 찾으면 전부 찾아 줌

- #hl[Kiwi]: 한국어 형태소 분석기. 다른 언어(C++)로 만든 것을 Java에서 불러 씁니다 (JNI).

- 설정집에 등록한 인물 이름은 Kiwi 사전에 자동으로 넣어서 이름으로 알아봅니다.

== 원고 기록: JGit

- 게임의 #hl[세이브 포인트]를 떠올려 보세요.

- `Ctrl+S`를 누를 때마다 원고 상태를 자동으로 기록합니다.
  - 언제든 예전 버전으로 돌아가 볼 수 있음

- #hl[Git]: 프로그래머들이 코드의 변경 기록을 남길 때 쓰는 도구
  - #hl[JGit] = Java로 만든 Git

- 프로그래머의 Git은 #strong[줄] 단위로 비교하지만, Texted는 #strong[문장] 단위로 비교합니다.
  - `[어미]` `[조사]` `[낱말] 문 → 창문` 처럼 무엇이 바뀌었는지 알려 줌

== 아주 긴 원고도 빠르게

- 원고 수백 화 = 수백만 글자. 매번 전부 다시 그리면 느려집니다.

- 비유: 아주 긴 #hl[두루마리]를 창문으로 본다면?
  - 창문에 보이는 부분만 그리면 됩니다 → #hl[보이는 줄만 그리기]

- 비유: 글을 끼워 넣을 자리에 #hl[빈칸을 미리 비워 두기] → 갭 버퍼(Gap Buffer)
  - 글자를 하나 넣을 때마다 뒤의 글자 수백만 개를 밀지 않아도 됨

- 바뀐 부분만 다시 그리기 (dirty region)

== 얼마나 빠를까?

#simple-table(
  columns: (1fr, auto, auto),
  table.header([*항목*], [*100만 자*], [*1,000만 자*]),
  [원고 불러오기], [0.05초], [0.1초],
  [스크롤 한 장면 그리기 (95%가 이 안에 끝남)], [4.16 ms], [2.92 ms],
  [글자 입력 → 화면에 보이기 (95%)], [3.44 ms], [2.03 ms],
)

- 1 ms = 1000분의 1초. 1초에 60장면을 보여 주려면 한 장면에 약 16.7 ms 안에 그려야 합니다.

- 원고가 #hl[10배] 길어져도 속도가 거의 같습니다 = 보이는 줄만 그리기 때문

#text(size: 16pt, fill: luma(120))[측정: CPU 4개, JDK 25, 2026-10-02 (`docs/EDITOR_PERFORMANCE.md`)]

== 일을 나눠서 하기

- 컴퓨터 CPU에는 일꾼(코어)이 여러 명 있습니다.

- 형태소 분석은 한 줄에 약 1 ms. 2,000줄이면 혼자서 2초!

- 원고를 나눠서 #hl[여러 일꾼이 동시에] 분석 → 4\~6배 빨라짐 (2,000줄 기준)

- 화면 일꾼(EDT)은 무거운 일을 하지 않고, 결과만 받아서 그립니다.
  - → 분석하는 동안에도 화면이 멈추지 않음

== 그 밖의 기능

- #hl[맞춤법 검사]: 자주 틀리는 맞춤법(됬다, 몇일, 할께 …)에 빨간 물결 밑줄

- #hl[지시문 `@`]: 원고에 `@Suppress("맞춤법")`라고 적으면 그 부분은 검사하지 않음
  - 지시문은 원고 글자 수나 내보내기에 들어가지 않습니다.

- #hl[내보내기]: 연재 사이트용 글(TXT), 전자책(EPUB)

- #hl[AI 문장 다듬기] (선택): AI 서버 주소를 설정하면 고른 문장을 다듬어 줌
  - 마음에 안 들면 실행 취소 한 번으로 되돌림

= 정리

== 한 장 요약

#simple-table(
  columns: (auto, 1fr),
  [*무엇을*], [웹소설 작가를 위한 워드프로세서 Texted],
  [*화면*], [Swing에서 창과 도화지만 빌리고, 나머지는 직접 그림],
  [*한국어*], [Kiwi로 형태소 분석 → 검수 · 통계 · 찾기],
  [*기록*], [JGit으로 자동 세이브, 문장 단위 비교],
  [*속도*], [보이는 줄만 그리기, 여러 일꾼이 나눠서 분석],
  [*현재*], [0.1.0 버전, 자동 테스트 197개 통과],
)

== 아직 부족한 점

- 맞춤법 검사는 #hl[목록 방식]이라, 목록(약 70개)에 없는 실수는 못 잡습니다.

- Kiwi는 앞뒤 문맥에 따라 다르게 분석할 때가 있습니다.
  - 특히 사전에 없는 이름일 때

- 프로그램을 다시 켜면 글꼴 크기가 기본(16pt)으로 돌아갑니다.

#focus-slide[
  #text(size: 48pt)[
    질문 있나요?
  ]
]
