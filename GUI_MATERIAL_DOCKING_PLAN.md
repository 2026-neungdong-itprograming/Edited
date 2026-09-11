# Texted Material GUI 및 Docking 구현 계획

작성 기준: 2026-09-11  
대상: 현재 `README.md`의 웹소설 작가용 IDE 구상과 `app/src/main/kotlin`의 자체 위젯 툴킷

## 1. 결론

Texted의 UI는 다음 경계를 지킨다.

- OS 창과 Swing 이벤트 루프를 위해 `JFrame`, `SwingUtilities`를 사용한다.
- 각 창의 콘텐츠는 프로젝트가 직접 구현한 단 하나의 `RootPane : JComponent`가 담당한다.
- `JButton`, `JPanel`, `JTextArea`, `JTree`, `JTabbedPane`, `JSplitPane`, `JPopupMenu`, `JDialog` 등 기본 Swing UI 컴포넌트는 사용하지 않는다.
- 실제 UI 요소는 `Widget` 트리, Java2D 페인팅, 자체 레이아웃·입력·포커스·접근성 계층으로 구현한다.
- 플로팅 Dock 창도 새 `JFrame` 안에 새 `RootPane`을 하나 두는 동일한 구조로 만든다.

현재 코드의 `RootPane -> Widget` 구조는 이 방향과 일치한다. 다만 Docking과 편집기 구현 전에 포인터 캡처, 키 바인딩, IME, 오버레이, 접근성, 크기 측정 계약을 먼저 보강해야 한다.

## 2. README와 현재 구현의 점검 결과

### 이미 존재하는 기반

- 단일 Swing 브리지: `RootPane : JComponent`
- 자체 retained 위젯 트리: `Widget`, `Container`
- 자체 레이아웃: `FillLayout`, `BorderLayout`
- 자체 포커스: `FocusManager`
- dirty-region 렌더링과 더블 버퍼: `RenderScheduler`, `Surface`
- 가상 스크롤 편집기의 시작점: `TextAreaWidget`, `Viewport`, `PixelMetrics`
- Material 색상 역할의 시작점: `Theme`, `MaterialLightTheme`, `MaterialDarkTheme`

### 먼저 정리할 불일치와 위험

1. README는 FlatLaf를 기술 스택으로 적었지만 빌드 의존성에는 없고, 현재의 단일 custom-painted `JComponent` 내부에는 FlatLaf UI delegate가 적용되지 않는다. FlatLaf는 제거/비적용으로 명시하고 Material 토큰을 직접 렌더링하는 편이 일관적이다.
2. README는 Java 17+를 말하지만 Gradle toolchain은 25이다. off-heap 구현이 사용하는 API까지 확인한 뒤 지원 JDK를 하나로 고정해야 한다.
3. “0ms” 지연은 측정 가능한 목표가 아니다. `입력→다음 프레임 p95`, 프레임 시간, repaint 면적, 대용량 문서 스크롤 FPS로 바꾼다.
4. 현재 마우스 드래그는 매 이벤트 위치에서 다시 hit-test한다. Dock 탭이나 splitter를 끌 때 최초 대상이 이벤트를 계속 받도록 pointer capture가 필요하다.
5. 현재 포커스는 클릭으로만 바뀐다. Tab/Shift+Tab 탐색, focus-visible 표시, 전역 단축키와 modal focus scope가 필요하다.
6. `KEY_TYPED`만으로 한국어 조합 입력을 완전하게 처리할 수 없다. `InputMethodEvent`의 committed/composed text와 `InputMethodRequests`를 `RootPane`에서 편집 위젯으로 중계해야 한다.
7. `Widget`은 실제 AWT 컴포넌트가 아니므로 각 내부 위젯의 접근성 정보가 자동으로 노출되지 않는다. `RootPane`의 `AccessibleContext`에서 가상 자식 트리를 제공해야 한다.

## 3. UI 전체 구조

```text
AppWindow (JFrame)
└─ RootPane (직접 구현한 유일한 JComponent)
   └─ AppShellWidget
      ├─ TopAppBarWidget
      ├─ NavigationRailWidget / ToolWindowStripWidget
      ├─ DockHostWidget
      │  ├─ left: Project / Structure / Characters
      │  ├─ center: EditorGroup + EditorTabs + TextEditor
      │  ├─ right: Inspector / World Bible / Relation Graph
      │  └─ bottom: Problems / Search / Git History
      ├─ StatusBarWidget
      └─ OverlayHostWidget
         ├─ Menu / Completion / Quick Fix / Tooltip
         ├─ Dialog / Snackbar / Notification
         └─ DockDropOverlay
```

Material 3의 bar/rail/pane 기반 scaffold를 데스크톱 IDE에 맞게 조밀하게 적용한다. Material은 시각 언어이고, Docking의 행위 모델은 IDE 관례를 따른다.

## 4. 필요한 자체 컴포넌트 목록

아래 이름은 모두 기본적으로 `Widget` 또는 `Container`의 하위 타입이다. Swing 동명 컴포넌트를 사용한다는 뜻이 아니다.

### P0 — 모든 UI보다 먼저 필요한 툴킷 기반

| 자체 타입 | 대체/역할 | 필수 기능 |
| --- | --- | --- |
| `Widget` 보강 | 모든 컴포넌트 기반 | enabled, hovered, pressed, focused, selected 상태; min/preferred/max size; cursor; semantics |
| `MeasureScope` / `Constraints` | Swing preferred size 계약 대체 | min/max 제약 기반 measure 후 layout |
| `RowLayout`, `ColumnLayout`, `StackLayout` | Box/Grid/Overlay 배치 | gap, padding, alignment, flex/grow |
| `PointerRouter` | Mouse listener 분배 | enter/exit, capture, drag threshold, cancel |
| `Keymap` / `Command` / `Action` | InputMap/ActionMap 대체 | 전역·scope·widget 단축키, 활성 조건 |
| `FocusManager` 보강 | KeyboardFocusManager 역할 | traversal, focus scope, restore focus, focus-visible |
| `ImeBridge` | JTextComponent 입력기 기능 대체 | 조합/확정 텍스트, 조합 caret, 후보창 위치 |
| `ClipboardService` | cut/copy/paste | 문자열/선택 영역, OS clipboard |
| `OverlayHostWidget` | popup/glass pane 역할 | z-order, modal barrier, anchor, 화면 경계 회피 |
| `AnimationClock` | Material motion | 한 개의 공유 tick, reduced-motion 지원 |
| `SemanticsNode` / `AccessibleRoot` | Swing 접근성 자식 대체 | role, name, state, action, value, text, bounds |
| `Density` / `ScaleContext` | HiDPI | dp→device pixel, 화면 이동 시 scale 변경 |

### P1 — Material 기본 구성요소

| 자체 컴포넌트 | Texted 사용처 |
| --- | --- |
| `SurfaceWidget`, `DividerWidget` | pane, toolbar, section, elevation layer |
| `TextWidget`, `IconWidget` | 모든 label과 Material Symbols |
| `ButtonWidget` | 주요/보조 작업 |
| `IconButtonWidget` | toolbar, 탭 닫기, pane header |
| `ToggleButtonWidget`, `CheckboxWidget`, `RadioWidget`, `SwitchWidget` | 필터, 보기 옵션, 환경설정 |
| `TextFieldWidget`, `SearchFieldWidget` | 검색, 이름 변경, 필터, 설정 입력 |
| `ScrollViewportWidget`, `ScrollbarWidget` | editor, list, tree, diff, panel |
| `VirtualListWidget<T>` | 검색 결과, 자동완성, 문제 목록, 커밋 목록 |
| `VirtualTreeWidget<T>` | 프로젝트, Structure, NSI, 인물/사건 계층 |
| `TabBarWidget`, `TabWidget`, `TabOverflowWidget` | editor 및 tool pane 탭 |
| `ToolbarWidget`, `TopAppBarWidget`, `StatusBarWidget` | 앱 shell과 pane 작업 |
| `NavigationRailWidget`, `ToolWindowStripWidget` | 기능 전환과 숨긴 pane 복원 |
| `MenuWidget`, `ContextMenuWidget`, `MenuItemWidget` | 앱/편집/탭/pane 명령 |
| `TooltipWidget` | 아이콘 설명과 shortcut 안내 |
| `DialogWidget`, `ModalBarrierWidget` | 확인, 설정, 충돌 해결 |
| `SnackbarWidget`, `NotificationWidget`, `BadgeWidget` | 저장 결과, warning, 분석 완료 |
| `ProgressIndicatorWidget` | Git/Nori/내보내기 백그라운드 작업 |
| `SliderWidget` | 회차별 관계도 타임라인 |
| `EmptyStateWidget` | 결과나 열린 문서가 없는 상태 |

### P1 — Docking 전용 구성요소

| 자체 컴포넌트 | 책임 |
| --- | --- |
| `DockHostWidget` | Dock 레이아웃 트리를 화면에 배치 |
| `DockPaneWidget` | header + tab strip + active content |
| `DockTabStripWidget` | 탭 선택, 재정렬, overflow, drag 시작 |
| `DockSplitterWidget` | split 비율 조절, double-click 균등화 |
| `ToolWindowStripWidget` | left/right/bottom의 숨긴 tool pane 버튼 |
| `DockDropOverlayWidget` | center/left/right/top/bottom drop zone와 preview |
| `DockDragGhostWidget` | 드래그 중 제목/아이콘 ghost |
| `FloatingDockWindow` | 별도 `JFrame + RootPane`의 플로팅 pane |
| `DockContextMenuWidget` | 이동, 분할, 플로팅, 닫기, 고정 명령 |

### P1/P2 — README 기능을 위한 도메인 컴포넌트

| 자체 컴포넌트 | README 기능 |
| --- | --- |
| `EditorGroupWidget`, `EditorTabWidget` | 여러 원고와 split editor |
| `TextEditorWidget` 보강 | 선택, 다중 caret 후보, undo/redo, IME, clipboard, horizontal scroll |
| `GutterWidget` | 문장/문단 번호, warning, 변경 이력, quick fix |
| `InspectionStripeWidget` | scrollbar의 error/warning marker |
| `BreadcrumbWidget` | 장/절/장면 scope 표시 |
| `CompletionPopupWidget` | 문맥 인식 자동완성 |
| `QuickFixPopupWidget` | Alt+Enter 및 로컬 사전 동의 |
| `ProblemsPaneWidget` | 검사 결과 목록과 원고 위치 이동 |
| `StructurePaneWidget` | 문장/문단/장면/회차 NSI tree |
| `ProjectPaneWidget` | 원고 및 설정 파일 tree |
| `SmartDiffWidget` | 문장/형태소 단위 양쪽/인라인 diff |
| `HistoryPaneWidget` | JGit 커밋/branch/timeline |
| `CharacterInspectorWidget` | 인물 상태와 사용자 수정 |
| `GraphCanvasWidget` | 인물 관계 및 사건 인과 그래프 |
| `TimelineSliderWidget` | 회차에 따른 그래프 상태 변경 |
| `FindUsagesPaneWidget` | 인물/사건/복선 사용처 |
| `ExportPaneWidget` | EPUB/TXT 빌드 상태와 결과 |

### 당장 만들지 않을 범용 컴포넌트

`Table`, 날짜/시간 picker, color chooser, spinner, rich HTML view는 README의 핵심 흐름에서 아직 필요하지 않다. 구체적인 화면 요구가 생길 때 추가한다. 범용 Swing 복제 자체가 목적이 되어서는 안 된다.

## 5. Docking 설계

### 5.1 상태 모델

Docking 상태와 그 렌더링 위젯을 분리한다. 상태 모델은 UI 객체나 `Graphics2D`를 참조하지 않는다.

```kotlin
sealed interface DockNode {
    data class Split(
        val axis: Axis,
        val ratio: Float,
        val first: DockNode,
        val second: DockNode,
    ) : DockNode

    data class Tabs(
        val paneIds: List<PaneId>,
        val active: PaneId,
    ) : DockNode

    data object Empty : DockNode
}

data class DockLayoutState(
    val root: DockNode,
    val hidden: Map<DockEdge, List<PaneId>>,
    val floating: List<FloatingPaneState>,
    val focusedPane: PaneId?,
    val schemaVersion: Int,
)
```

- `DockManager`는 `MovePane`, `SplitPane`, `MergeTabs`, `ResizeSplit`, `HidePane`, `FloatPane`, `ClosePane`, `RestoreLayout` 명령을 원자적으로 적용한다.
- 변경 전후 상태를 검증해 pane ID의 중복, 빈 split, 잘못된 ratio가 생기지 않게 한다.
- 렌더링은 상태를 직접 고치는 대신 명령만 발행한다.
- 콘텐츠 생명주기는 `PaneDescriptor(id, title, icon, closable, factory, saveState)`가 관리한다. 탭 이동 때 콘텐츠를 재생성하지 않는다.

### 5.2 드래그 동작

1. 탭/header press에서 포인터를 capture한다.
2. OS drag threshold를 넘으면 `DockDragSession`을 시작한다.
3. 현재 포인터 아래의 `DockPaneWidget`과 drop zone을 계산한다.
4. `DockDropOverlayWidget`이 center(탭 합치기), left/right/top/bottom(분할) preview를 그린다.
5. release 시 명령 하나로 commit하고, Esc/창 focus 상실 시 원상 복구한다.
6. drop target이 없고 main window 밖이면 floating window를 만든다.

키보드에서도 같은 명령을 사용할 수 있어야 한다: pane 이동 메뉴, `Split Right/Down`, 이전/다음 pane, focus editor, layout reset. Docking을 마우스에만 의존시키지 않는다.

### 5.3 레이아웃 규칙

- split ratio는 픽셀이 아니라 0..1 비율로 저장하되 양쪽 pane의 최소 크기로 clamp한다.
- splitter hit target은 보이는 선보다 넓게 둔다.
- 창 resize와 DPI 변경 뒤에도 ratio를 유지한다.
- 마지막 탭이 빠진 `Tabs`는 제거하고 부모 `Split`을 남은 자식으로 collapse한다.
- 탭은 reorder, close, close others, pin/preview, overflow를 지원한다.
- 초기 버전은 `DOCKED`, `HIDDEN`, `FLOATING`을 지원한다. overlay형 unpinned/sliding mode는 P2로 미룬다.
- 레이아웃은 버전이 있는 JSON으로 저장한다. 없는 pane ID는 무시하고 새 필수 pane은 기본 위치에 합친다.

### 5.4 기본 Texted 레이아웃

- 왼쪽: `Project`, `Structure`
- 중앙: `EditorGroup`
- 오른쪽: `Characters`, `World`, `Graph`
- 아래: `Problems`, `Search`, `Git History`
- edge strip: 숨겨진 pane의 아이콘/짧은 이름

## 6. Material 3 적용 방침

- `Theme`를 `ColorScheme`, `Typography`, `Shapes`, `Elevation`, `Motion`, `StateLayer`, `Density` 토큰 묶음으로 확장한다.
- 모든 interactive widget은 enabled/disabled, hover, focus, pressed, dragged, selected 상태를 동일한 state-layer 규칙으로 표현한다.
- IDE는 정보 밀도가 높으므로 모바일용 큰 치수를 그대로 복사하지 않고, compact/comfortable density profile을 제공한다. 색 역할·상태·형태·계층 원칙은 유지한다.
- 반응형 scaffold는 창 너비에 따라 rail/pane을 축소하거나 tool window strip으로 접는다. 사용자 Dock 레이아웃은 자동 반응형 변경보다 우선한다.
- 아이콘은 Material Symbols의 필요한 SVG만 저장소에 vendoring하고 Java2D `Path2D` 또는 캐시된 벡터 경로로 렌더링한다. 네트워크 폰트 로딩은 하지 않는다.
- 애니메이션은 기능 이해에 도움이 되는 hover/focus/ripple, pane reveal, drop preview에만 짧게 적용한다. reduced-motion 설정과 즉시 완료 경로를 둔다.
- light/dark 모두 같은 semantic color role을 사용하고, 하드코딩한 색은 금지한다.

## 7. 구현 순서

### 단계 A — 계약과 검증 기반

- 허용 Swing 경계(`JFrame`, `JComponent`, `SwingUtilities`)를 문서화하고 forbidden-import 테스트를 추가한다.
- README의 FlatLaf, Java 버전, 0ms 문구를 실제 방침과 맞춘다.
- `Widget` measure/layout/state/semantics 계약과 좌표계를 확정한다.
- headless `BufferedImage` 렌더 테스트와 이벤트 테스트 harness를 만든다.

완료 조건: 빈 AppShell을 resize/DPI/theme 전환해도 layout과 repaint가 안정적이다.

### 단계 B — 입력·포커스·IME·오버레이

- pointer routing/capture, hover, cursor, drag threshold
- command/keymap과 focus traversal/scope
- clipboard, selection 기반 편집 명령
- 한국어 IME 조합 문자열과 후보창 위치
- overlay positioning, modal barrier, tooltip timing
- 접근성 가상 트리의 최소 구현

완료 조건: 마우스 drag 중 대상이 바뀌지 않고, 키보드만으로 focus 이동이 되며, 한글 조합 입력이 깨지지 않는다.

### 단계 C — Material primitive

- token 체계와 light/dark theme
- text/icon/surface/divider/button/icon button/text field
- scroll viewport/scrollbar/virtual list/virtual tree
- tabs/menu/dialog/snackbar/progress/slider

완료 조건: 각 컴포넌트의 모든 상태, 키보드 동작, scale 100/125/150/200%, light/dark 렌더 테스트가 통과한다.

### 단계 D — AppShell과 Docking MVP

- top bar, navigation/tool-window strips, status bar
- Dock 상태 모델과 명령, split layout, pane/tab UI
- 탭 reorder, pane 간 이동, 5방향 drop, splitter resize
- hide/restore, floating window, JSON 저장/복원, default reset

완료 조건: Project/Editor/Problems 세 pane를 임의로 분할·합치기·플로팅한 뒤 재시작해 동일하게 복원된다.

### 단계 E — 편집기 셸 완성

- editor group/tabs, 선택/caret/undo/redo/clipboard/IME
- gutter, breadcrumbs, inspection stripe, search field
- virtual scrolling과 incremental repaint 계측

완료 조건: 1M/10M 문자 fixture에서 입력 지연과 스크롤 프레임 지표를 기록하고 회귀 기준을 둔다.

### 단계 F — README 기능 pane

- Project/Structure/Problems
- completion/quick fix/로컬 사전 동의
- sentence/morpheme diff 및 Git History
- Characters/World/Find Usages
- relation/event graph와 timeline slider
- export와 background task progress

완료 조건: README의 네 핵심 기능 각각이 editor와 최소 한 개의 dock pane을 오가며 end-to-end로 동작한다.

### 단계 G — 품질 마감

- 접근성 role/action/value/text와 상태 변경 이벤트
- 다중 모니터/DPI/floating window 테스트
- 레이아웃 schema migration과 손상 복구
- 테마 대비, reduced motion, 키보드 전용 사용성
- 성능 프로파일과 메모리 누수/리스너 해제 검사

## 8. 테스트 전략

- **Dock 모델 단위 테스트:** 임의 명령열 뒤 모든 pane ID가 정확히 한 위치에 있고, 빈 split과 범위 밖 ratio가 없는지 property test한다.
- **레이아웃 단위 테스트:** 최소 크기, nested split, 작은 창, DPI 변경, 마지막 탭 제거를 검증한다.
- **입력 테스트:** press-drag-release capture, Esc 취소, Tab traversal, 단축키 충돌, 메뉴 modal scope를 검증한다.
- **IME 테스트:** 한글 조합 수정/확정, selection 대체, caret 이동, undo 단위를 검증한다.
- **렌더 회귀 테스트:** light/dark와 주요 DPI에서 `BufferedImage` golden image를 비교하되 글꼴 차이는 허용 오차를 둔다.
- **통합 테스트:** 실제 `RootPane`에서 탭 이동→split→float→저장→재시작→복원 시나리오를 실행한다.
- **성능 테스트:** 1M/10M 문자에서 visible line만 처리되는지, frame time p50/p95/p99, dirty area, allocation을 기록한다.

## 9. 의존성 결정

- Docking 라이브러리는 도입하지 않는다. 대부분 `JTabbedPane`, `JSplitPane`, `JPanel` 등의 Swing 컴포넌트 계층을 전제로 하므로 현재 제약과 아키텍처에 맞지 않는다.
- FlatLaf도 custom widget 렌더러에는 실익이 없으므로 도입하지 않는다.
- Java2D/AWT의 `Graphics2D`, event, font, clipboard, input-method, top-level window API는 플랫폼 브리지로 사용한다.
- Material Symbols SVG는 Apache 2.0 라이선스 고지와 함께 필요한 아이콘만 포함한다.

## 10. 조사 근거

- Material 3 Foundations: design token, 접근성, interaction state, layout을 공통 기반으로 정의한다.  
  https://m3.material.io/foundations/
- Material canonical layouts: bar, rail, pane scaffold와 list-detail/supporting-pane 구성을 제안한다.  
  https://m3.material.io/foundations/layout/canonical-examples/overview
- Material component 참고:  
  https://m3.material.io/components/navigation-rail/overview  
  https://m3.material.io/components/tabs/overview  
  https://m3.material.io/components/text-fields/overview  
  https://m3.material.io/components/dialogs/overview  
  https://m3.material.io/components/menus/overview
- Oracle `JComponent`: custom component의 painting, key handling, accessibility, double buffering 기반과 top-level container 필요성을 설명한다.  
  https://docs.oracle.com/en/java/javase/25/docs/api/java.desktop/javax/swing/JComponent.html
- Oracle custom painting: 그리기는 `paintComponent`에서 수행하며 repaint 영역을 제한할 수 있다.  
  https://docs.oracle.com/javase/tutorial/uiswing/painting/
- Oracle EDT: UI 작업은 EDT에서 짧게 처리하고 긴 작업은 worker로 보내야 한다.  
  https://docs.oracle.com/javase/tutorial/uiswing/concurrency/dispatch.html
- Oracle Input Method Framework: 한국어를 포함한 조합 입력을 위해 편집 컴포넌트가 input method 요청을 처리해야 한다.  
  https://docs.oracle.com/en/java/javase/25/docs/api/java.desktop/java/awt/im/package-summary.html
- Oracle Accessibility: name, role, state, action, selection, text, value를 접근성 트리로 노출하는 계약을 제공한다.  
  https://docs.oracle.com/en/java/javase/25/docs/api/java.desktop/javax/accessibility/AccessibleContext.html
- Material Symbols: SVG/PNG 및 variable font로 제공되고 Apache 2.0 라이선스이다.  
  https://developers.google.com/fonts/docs/material_symbols
- IntelliJ Tool Windows: IDE tool pane의 header/content/tabs/toolbar/edge strip 구조를 설명한다.  
  https://www.jetbrains.com/help/idea/tool-windows.html
- IntelliJ view modes와 layout: docked/hidden/floating/windowed 모드 및 사용자 레이아웃 저장 관례를 설명한다.  
  https://www.jetbrains.com/help/idea/viewing-modes.html  
  https://www.jetbrains.com/help/idea/tool-window-layouts.html
- IntelliJ editor split UX: 탭 drag를 통한 split/move/unsplit 관례를 설명한다.  
  https://www.jetbrains.com/help/idea/using-code-editor.html

## 11. 첫 구현 묶음 권장 범위

첫 코드 작업은 Docking 화면부터 그리지 말고 다음 묶음으로 시작한다.

1. `Widget`의 상태·측정 계약
2. pointer capture가 있는 `PointerRouter`
3. `Keymap`과 확장된 `FocusManager`
4. `OverlayHostWidget`
5. `SurfaceWidget`, `TextWidget`, `IconButtonWidget`, `TabBarWidget`
6. 순수 상태 기반 `DockNode`/`DockManager`와 불변식 테스트

이 기반이 통과하면 `DockHostWidget`, splitter, drop overlay를 올리는 순서가 가장 재작업이 적다.

## 12. 구현 진행 상황

2026-09-11 첫 기반 묶음 완료:

- `Widget` 상태(enabled/hovered/pressed/focused/selected), 측정 계약, semantics 모델
- pointer enter/exit와 press 대상 capture를 제공하는 `PointerRouter`
- Tab/Shift+Tab 순환 및 modal scope를 지원하는 `FocusManager`
- 전역/상위 key binding 기반이 되는 `Keymap`
- Swing popup 없이 z-order와 modal 입력 차단을 제공하는 `OverlayHostWidget`
- `SurfaceWidget`, `TextWidget`, `IconButtonWidget`, `TabBarWidget` Material primitive
- `Split/Tabs/Empty` 트리와 move/hide/restore/float/close/resize/focus 명령을 제공하는 `DockManager`
- Swing import 경계, pointer capture, focus traversal, Dock 불변식에 대한 자동 테스트 8개

다음 묶음: 한국어 IME 브리지와 `DockHostWidget`/splitter/drop overlay의 실제 렌더링 연결.
