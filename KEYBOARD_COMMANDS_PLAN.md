# 키보드 명령 제공(발견 가능성) 구현 계획

작성 기준: 2026-10-02  
대상: `GUI_MATERIAL_DOCKING_PLAN.md` 단계 D에서 만든 Dock 키보드 명령

## 1. 목표와 현재 문제

Dock 키보드 명령(F6, Alt+Shift+방향키, Shift+Esc 등)은 구현되어 있지만 사용자가 그런 명령이 있다는 사실을 알 방법이 없다. 다음을 목표로 한다.

1. 사용자가 앱 안에서 **어떤 키보드 명령이 있는지 찾아볼 수 있다** (메뉴, 단축키 목록, 상태 표시줄 안내).
2. 명령의 이름·단축키·동작이 **한 곳에서만 정의**되어 메뉴, 단축키 목록, 실제 키 바인딩이 어긋나지 않는다.
3. 단축키가 OS/입력기와 충돌해도 **메뉴나 명령 팔레트로 같은 명령을 실행**할 수 있다.

### 현재 상태

- 명령은 `DockHostWidget.bindKeys`에서 `Keymap`에 바인딩한다.
- 같은 명령의 이름과 단축키 문자열이 `DockContextMenuWidget.itemsFor`에 **한 번 더 하드코딩**되어 있다. 단축키를 바꾸면 두 곳을 고쳐야 하고, 어긋나도 알 수 없다.
- master의 `App.kt`가 `TextedWindow` + `ProjectWorkspaceWidget` 구조로 바뀌어 **현재 앱 화면은 Dock을 쓰지 않는다**. 즉 사용자가 키보드 명령을 쓸 수 있는 화면 자체가 아직 없다.
- 상단 바의 메뉴(`파일 편집 보기 …`)는 글자만 그려진 정적 텍스트이고 클릭해도 열리지 않는다.
- 아이콘 버튼 툴팁, 단축키 목록, 명령 팔레트가 없다.

## 2. 제약

`AGENTS.md`: **기존 생성자 인자를 바꾸지 않는다.** 따라서 다음 규칙을 지킨다.

- `ProjectWorkspaceWidget(initialSession)`, `TextedWindow(initialProject)`, `DockHostWidget(manager, registry, defaultLayout)`, `ContextMenuWidget(items, onDismiss)` 등 기존 생성자 시그니처는 유지한다.
- 새 기능은 **새 클래스, 새 메서드, 기본값이 있는 새 프로퍼티**로 추가한다.
- 기존 `ContextMenuItem(label, shortcut, enabled, action)`도 그대로 두고, 새 모델에서 이 타입으로 변환한다.

## 3. 설계

### 3.1 명령 카탈로그 (단일 출처)

```kotlin
data class AppCommand(
    val id: String,                 // "dock.focusNext"
    val title: String,              // "다음 Pane으로 이동"
    val category: String,           // "도구 창"
    val shortcuts: List<KeyStroke>, // 첫 항목이 대표 단축키
    val enabled: () -> Boolean = { true },
    val run: () -> Unit,
)

class CommandCatalog {
    fun register(command: AppCommand)
    fun all(): List<AppCommand>
    fun bindInto(keymap: Keymap)          // 단축키 → Command
    fun find(query: String): List<AppCommand>
}
```

- `DockHostWidget`은 명령을 `CommandCatalog`에 **등록만** 한다(`registerCommands(catalog)` 신규 메서드). 기존 `bindKeys(keymap, pane)`은 시그니처를 유지하고 내부에서 카탈로그를 거치도록 바꾼다.
- `DockContextMenuWidget.itemsFor`도 카탈로그 항목에서 라벨과 단축키를 읽는다. 하드코딩한 문자열을 제거한다.
- 명령 id는 안정적인 문자열로 두어 이후 사용자 키맵 파일(3.5)에서 참조한다.

### 3.2 단축키 표시

- `KeyStroke.display(platform)` 신규 확장: Windows/Linux는 `Ctrl+Alt+Shift+→`, macOS는 `⌃⌥⇧→`.
- 메뉴, 단축키 목록, 툴팁이 모두 이 함수를 쓴다.

### 3.3 발견 경로 (사용자가 명령을 찾는 방법)

| 경로 | 내용 | 비고 |
| --- | --- | --- |
| 상단 바 **보기 → 도구 창** 메뉴 | 숨긴 pane 복원, 플로팅/도킹, 레이아웃 초기화 등을 단축키와 함께 표시 | 상단 바 메뉴를 클릭 가능하게 만들어야 함(3.4) |
| 상단 바 **도움말 → 키보드 단축키** | 단축키 목록 대화상자 열기 | |
| **F1** / `Ctrl+Shift+A` | 단축키 목록 / 명령 팔레트 | |
| Pane 헤더 `⋮` 메뉴, 우클릭, `Shift+F10` | 이미 있음. 카탈로그로 통일 | |
| 상태 표시줄 힌트 | 포커스 pane이 바뀔 때 `F6 Pane 이동 · Shift+F10 메뉴 · F1 단축키`를 짧게 표시 | 첫 실행에만 강조, 설정으로 끌 수 있게 |
| 아이콘 버튼 툴팁 | "숨기기 (Shift+Esc)" | `TooltipWidget`(계획서 P0 `OverlayHost` 항목)이 필요하므로 후순위 |

### 3.4 필요한 새 위젯

- `MenuBarWidget`: 기존 `TopBarWidget`의 정적 메뉴 글자를 클릭 가능한 메뉴로 만든다. 클릭 시 `ContextMenuWidget`을 메뉴 항목 아래에 열고, 열린 상태에서 좌우 방향키로 이웃 메뉴로 이동한다. `TopBarWidget`은 `ProjectWorkspaceWidget`의 private 클래스이므로, 새 위젯을 만들어 교체하고 `ProjectWorkspaceWidget` 생성자는 건드리지 않는다.
- `ShortcutsDialogWidget`: 기존 `DialogWidget`/`TextFieldWidget` 위에 구축. 상단 검색 필드로 필터링하고, 카테고리별로 명령 이름과 단축키를 나열한다. `Esc`로 닫는다.
- `CommandPaletteWidget`(후순위): 같은 목록을 입력 중 필터링해 `Enter`로 실행한다. 단축키 목록과 데이터 모델을 공유하므로 추가 비용이 작다.

### 3.5 충돌 대응과 사용자 지정

- `Alt+Shift+방향키`는 일부 OS/입력기에서 시스템 단축키와 겹칠 수 있다. 어떤 키도 메뉴/팔레트로 같은 명령을 실행할 수 있으므로 치명적이지 않지만, 사용자가 바꿀 수 있어야 한다.
- `~/.texted/keymap.json`(`schemaVersion` 포함, 레이아웃 파일과 같은 규칙): `{ "dock.focusNext": ["F6"], ... }`. 없는 id는 무시, 손상되면 기본값으로 대체.
- 로드 시 **중복 단축키를 검출**하여 먼저 등록된 명령을 유지하고 나중 것은 경고로 상태 표시줄/로그에 알린다.
- 설정 UI는 이번 범위가 아니다. 파일 편집 방식으로 시작한다.

### 3.6 접근성

- 각 메뉴 항목과 단축키 목록 행에 `Semantics`(role=MENU_ITEM, name, description=단축키)를 채운다.
- 스크린 리더가 단축키를 읽을 수 있도록 단축키 문자열을 `description`에 넣는다.

## 4. 구현 순서

### 단계 1 — Dock을 현재 앱에 다시 연결 (선행 조건)

master 병합으로 앱 화면에서 Dock이 빠졌으므로, 사용자가 쓸 화면부터 복구한다.

1. `ProjectWorkspaceWidget` 내부의 고정 배치(`tree/editor/inspector/problems`)를 `DockHostWidget`으로 교체한다. 기존 위젯(`ProjectTreeWidget`, `DocumentEditorWidget`, `ProjectInspectorWidget`, `ProblemsWidget`)을 `PaneDescriptor`의 `content`로 재사용하고, 없는 pane(Structure, World, Graph, Search, Git History)은 빈 상태 위젯으로 채운다.
2. `ProjectWorkspaceWidget`에 `val dock: DockHostWidget`을 추가하고 `TextedWindow`에서 `workspace.dock.install(rootPane)`을 호출한다. 생성자는 그대로 둔다.
3. `TextedWindow`에 `windowOpened` 시 `floatingWindowsEnabled = true`, `windowClosing` 시 레이아웃 저장과 `disposeFloatingWindows()`를 추가한다.
4. `NavigationRail`(P, S, C, G, H)은 숨긴 pane 복원 strip과 역할이 겹치므로, Dock의 `ToolWindowStripWidget`으로 대체하거나 제거할지 결정한다.

완료 조건: 앱을 실행하면 기본 레이아웃이 보이고, 기존 `WorkspaceSessionTest`가 계속 통과하며, 키보드 명령이 동작한다.

### 단계 2 — 명령 카탈로그와 단축키 표시

1. `AppCommand`, `CommandCatalog`, `KeyStroke.display()` 추가.
2. Dock 명령을 카탈로그에 등록하고 `bindKeys`/`DockContextMenuWidget`이 카탈로그를 사용하도록 변경(기존 시그니처 유지).
3. 파일 열기/새 프로젝트/저장(`Ctrl+N/O/S`) 같은 기존 앱 명령도 카탈로그로 이전한다.

완료 조건: 단축키 문자열이 코드에 한 번만 존재한다.

### 단계 3 — 메뉴 바와 단축키 목록

1. `MenuBarWidget`: 보기 → 도구 창, 도움말 → 키보드 단축키.
2. `ShortcutsDialogWidget` + `F1` 바인딩.
3. 상태 표시줄 힌트.

완료 조건: 마우스만으로도 키보드 명령 목록을 열 수 있고, 키보드만으로도 메뉴를 열고 닫을 수 있다.

### 단계 4 — 사용자 키맵과 충돌 검출

1. `keymap.json` 로드/저장, 중복 검출, 손상 시 기본값 대체.
2. 명령 팔레트(`Ctrl+Shift+A`).

### 단계 5 — 툴팁 (`TooltipWidget` 도입 시)

아이콘 버튼에 단축키가 붙은 툴팁을 표시한다. 계획서의 `OverlayHostWidget` 타이밍 요구를 먼저 충족해야 한다.

## 5. 테스트 계획

- **카탈로그 단위 테스트**: 같은 id 중복 등록 거부, 단축키 중복 검출, 비활성 명령은 실행되지 않음.
- **표시 테스트**: `KeyStroke.display()`가 플랫폼별 문자열을 낸다.
- **일관성 테스트**: 카탈로그의 모든 Dock 명령이 `Keymap`에 바인딩되어 있고, 컨텍스트 메뉴 라벨과 단축키가 카탈로그와 일치한다(문자열 하드코딩 재발 방지).
- **키맵 파일 테스트**: 손상/알 수 없는 id/중복 단축키 처리.
- **위젯 테스트**: 메뉴 바에서 메뉴 열기/좌우 이동/Esc 닫기, 단축키 목록의 필터링과 닫기. 기존 `DockHostWidgetTest`처럼 헤드리스로 이벤트를 직접 전달한다.
- **화면 확인**: Xvfb에서 실제 앱을 띄워 `F1`, 메뉴, 상태 표시줄 힌트 스크린샷을 찍어 확인한다(Dock 스크린샷에 사용한 방식).

## 6. 위험과 미결정 사항

- **단계 1의 범위**: Dock 연결 때 `ProjectWorkspaceWidget`을 상당히 고쳐야 한다. 이 파일은 master에서 다른 작업자가 계속 수정 중이므로 충돌이 잦을 수 있다. 시작 전에 해당 파일의 담당자와 조율하는 것이 좋다.
- **상단 바 메뉴를 어디까지 만들 것인가**: 이번에는 보기 → 도구 창, 도움말 → 단축키만 구현하고 나머지 메뉴는 비활성 자리표시자로 둘 것을 권장한다.
- **내비게이션 레일 유지 여부**: Dock의 가장자리 strip과 중복된다. 제품 의도를 확인해야 한다.
- **단축키 선택**: `Alt+Shift+방향키`와 `F1` 등이 사용 중인 OS/입력기(한글 IME 포함)에서 충돌하는지 실제 환경에서 확인이 필요하다. 이 환경에서는 Xvfb만 사용해 검증했다.
- **아직 구현되지 않은 Dock 항목**: 탭 드래그 재정렬, 5방향 drop overlay, 주기적 자동 저장은 이 계획의 범위 밖이며 `GUI_MATERIAL_DOCKING_PLAN.md`에 남아 있다.
