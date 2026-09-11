# Texted

---
### 1. 비전 및 핵심 가치

* **개발자에게 Git과 IntelliJ가 있듯, 웹소설 작가에게 완벽한 서사 통제실(IDE) 제공**
* 기존 텍스트 에디터의 한계(줄 단위 Diff, 오탈자 위주의 검수, 파편화된 설정집)를 극복하고, **문장·형태소·사건·인물 중심의 정밀한 버전 관리 및 서사 분석** 수행.

---

### 2. 전체 기술 스택 및 역할 (Kotlin/JVM + Swing/AWT 브리지)

| 구분 | 선택 기술 스택 | 핵심 역할 및 도입 이유 |
| --- | --- | --- |
| **App Framework** | **Java 25 / Kotlin + Swing bridge** | 창마다 직접 구현한 하나의 `JComponent`만 Swing에 연결하고, 내부 UI는 자체 `Widget` 트리와 Material 3 토큰으로 렌더링. Windows/Mac 크로스 플랫폼 지원. |
| **Editor Core** | **Custom JComponent Canvas** | Virtual Scrolling & Custom Painting (`paintComponent`)으로 수십만 자의 대용량 원고도 viewport에 비례하는 비용으로 렌더링. |
| **Version Control** | **Eclipse JGit** | Pure Java 기반 Git 코어. 백그라운드 **자동 커밋, 브랜칭(IF 시나리오), 커밋 히스토리 관리**. |
| **NLP & Lexer** | **Apache Lucene Nori** | C++ 의존성 없는 Pure Java 형태소 분석기. **로컬 사용자 사전(Custom Dict)** 바인딩으로 고유명사 오탐 방지. |
| **Graph / Diagram** | **JGraphT** | **인물 관계 트리 및 사건 인과관계도(Event Graph)**의 백엔드 데이터 구조 및 시각화 렌더링. |

---

### 3. 컴파일러론 기반 데이터 파이프라인

```text
[ 소스 원고 (.md / .txt) ]
          │
          ▼
[ 1. Lexer (Nori + 로컬 사용자 사전) ] ➔ 형태소 및 고유명사 토큰화
          │
          ▼
[ 2. Parser & NSI Tree (Novel Structure Interface) ] ➔ 문장/문단/인물/사건 AST 구조화
          │
          ▼
[ 3. Inspection & Linter Engine ] ➔ 설정 파괴 감지, 동일 어미 반복, 복선 미회수 검수
          │
          ▼
[ 4. Target Generation & JGit Commit ] ➔ JGit 버저닝 스냅샷 저장 및 EPUB/TXT 출판 빌드

```

---

### 4. 4대 핵심 혁신 기능

#### ① 문장(Sentence) 단위 Smart Diff & JGit 버저닝

* 코드의 라인/함수 기준이 아닌 **국문학적 최소 단위인 '문장' 및 '형태소'를 기준으로 Git Diff 탐색**.
* 과거 커밋 시점의 특정 문장만 우클릭하여 이력을 조회하거나 롤백 가능.

#### ② 유저 동의 기반 로컬 사전 & 상태 관리 (User-in-the-Loop)

* 문학적/의학적 표현(예: *"심박수가 0이 되버렸다"*)으로 인한 인물 상태 변화 감지.
* 시스템 오탐 시 작가가 직접 상태를 수정하면 **"이 문구를 사전에 추가할까요?" (Quick Fix)** 팝업 제공 후 **로컬 사전(`custom-rules.json`)에 반영 및 JGit 백업**.

#### ③ 문맥 인식 스마트 자동완성 (Context-Aware Completion)

* 현재 커서 위치의 장소/시간대(Scope)를 파악하여 해당 장소에 존재하는 인물 및 고유명사를 최우선 추천.
* 오타 입력 시에도 정상 고유명사로 자동 보정 추천.

#### ④ 동적 인물 관계도 & 사건 인과관계도 (Dynamic Event Graph)

* **회차 타임라인 슬라이더:** 회차 변경에 따라 인물의 상태(생사, 관계, 호감도)가 동적으로 변화하는 그래프 시각화.
* **사건 관계도:** `[원인]` $\rightarrow$ `[결과]` $\rightarrow$ `[복선]` 인과관계를 추적하고, 회수되지 않은 복선을 IDE 상단에 **Warning 알림**으로 포착.

---

### 5. 핵심 경쟁력 요약

* **Zero GPU / Measurable On-Device Performance:** LLM 없이 Pure Java (Nori + Rule Engine + FSM)로 구동하고, 입력 지연·프레임 시간·repaint 면적을 회귀 지표로 관리.
* **Determinism & Stability:** 환각(Hallucination) 없는 명확한 규칙 기반 검수와 JGit의 절대적인 데이터 안정성 확보.
* **Developer-like Author UX:** IntelliJ의 `Alt + Enter`, `Find Usages`, `Gutter bar`, `Structure View` 등의 UX를 웹소설 창작 환경에 이식.

---

### 6. GUI 구현 경계

* 허용되는 Swing 경계는 최상위 `JFrame`, EDT 예약용 `SwingUtilities`, 그리고 창마다 하나씩 두는 직접 구현 `RootPane : JComponent`이다.
* `JButton`, `JPanel`, `JTextArea`, `JTree`, `JTabbedPane`, `JSplitPane`, `JPopupMenu`, `JDialog` 등의 기본 Swing UI 컴포넌트는 사용하지 않는다.
* 버튼, 탭, 메뉴, 다이얼로그, 트리, 스크롤, 편집기와 Docking UI는 Java2D로 그리는 자체 `Widget`으로 구현한다.
* 상세 컴포넌트 목록과 구현 순서는 [`GUI_MATERIAL_DOCKING_PLAN.md`](GUI_MATERIAL_DOCKING_PLAN.md)를 따른다.
