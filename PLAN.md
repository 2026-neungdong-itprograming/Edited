# Texted 작업 계획

작성 기준: 2026-10-29

## 목표

**소설 쓰는 사람을 위한 한글(워드프로세서)을 만듭니다.**
작가가 원고를 쓰고, 고치고, 분량을 맞추고, 설정을 챙기고, 연재처에 올리는 일을 한 프로그램 안에서 끝낼 수 있게 하는 것이 목표입니다.

## 진행 상황 (2026-10-29)
앱에서 쓰는 법:

| 기능 | 여는 법 |
| --- | --- |
| 원고 / 찾기 / 설정 / 분량 / 기록 패널 | 왼쪽 레일 버튼 |
| 찾기·바꾸기 | `Ctrl+Shift+F` (편집기에서 고른 글자로 바로 찾기) |
| 저장 + 기록 남기기 + 검수·분량 갱신 | `Ctrl+S` |
| TXT·EPUB 내보내기 (`export/` 폴더) | `Ctrl+Shift+E` |
| 지금 회차 본문 복사 (연재처 붙여넣기용) | `Ctrl+Shift+C` |
| 다음 / 이전 문제로 이동 | `F2` / `Shift+F2` |
| 모든 메뉴와 단축키 | 위쪽 메뉴 막대, 도움말 › 단축키 |

경로 표기: `edited/` = `app/src/main/kotlin/io/github/nd2026/edited/`, `test/` = `app/src/test/kotlin/io/github/nd2026/edited/`

---

## 한눈에 보기

| # | 작업 | 작가에게 주는 것 | 난이도 | 고칠 수 있는 경로 (이 밖은 수정 금지) | 담당자 |
| --- | --- | --- | --- | --- | --- |
| 1 | 문장 검수 | 긴 문장·어미 반복·따옴표 실수 알림 | ★ | `edited/inspect/**`, `edited/ui/panes/ProblemsPane.kt`, `test/inspect/**` | |
| 2 | 분량 통계 | 회차별 글자수(공백 포함/제외), 목표 분량 달성률 | ★ | `edited/stats/**`, `edited/ui/panes/StatsPane.kt`, `test/stats/**` | |
| 3 | 설정집 | 인물·세계관 카드, 인물 등장 회차 | ★★ | `edited/world/**`, `edited/ui/panes/CharactersPane.kt`, `test/world/**` | |
| 4 | 찾기·바꾸기 | 원고 전체 검색, 인물 이름 한 번에 바꾸기 | ★★ | `edited/search/**`, `edited/ui/panes/SearchPane.kt`, `test/search/**` | |
| 5 | 내보내기 | 연재처에 붙여넣을 TXT, 전자책 EPUB | ★★ | `edited/export/**`, `test/export/**` | |
| 6 | 원고 기록 | 자동 저장된 이전 버전 보기, 문장 단위 변경 비교 | ★★★ | `edited/vcs/**`, `edited/ui/panes/HistoryPane.kt`, `test/vcs/**` | |

---

## 작업 1. 문장 검수 ★

작가가 쓰면서 놓치기 쉬운 실수를 Problems 패널에 보여 줍니다. 규칙 하나당 파일 하나로 만들어 주세요(예: `inspect/LongSentenceRule.kt`).

**약속**
```kotlin
interface InspectionRule {
    val id: String
    fun check(lines: List<String>): List<Inspection>   // core.Inspection 재사용, line은 0부터
}
object Linter { fun run(text: String): List<Inspection> }
class ProblemsPane : Widget() { var project: Project?; var inspections: Map<Path, List<Inspection>>; var onNavigate: (Path, Int) -> Unit }
```

**할 일**
- [x] 1-1. 긴 문장: 한 문장이 120자를 넘으면 `WARNING`
- [x] 1-2. 어미 반복: 연속 3문장 이상이 같은 어미(`~했다.`)로 끝나면 `INFO`
- [x] 1-3. 따옴표·괄호 짝: `" "`, `“ ”`, `‘ ’`, `( )` 가 맞지 않는 줄은 `ERROR`
- [x] 1-4. 습관어: "갑자기", "순간", "그리고" 등이 한 문단에 3번 이상 나오면 `INFO`
- [x] 1-5. `ProblemsPane`: 문제 목록을 보여 주고, 클릭하면 `onNavigate(파일, 줄)` 호출

**완료 조건**: 규칙마다 "걸리는 예문"과 "안 걸리는 예문" 테스트가 있어야 합니다.

---

## 작업 2. 분량 통계 ★

웹소설은 회차당 분량(예: 공백 제외 5,000자)이 중요합니다. 회차별 글자수와 목표 달성률을 보여 줍니다.

**약속**
```kotlin
data class ChapterStats(val path: Path, val title: String, val chars: Int, val charsNoSpace: Int, val sentences: Int)
object ManuscriptStats { fun of(project: Project): List<ChapterStats> }
class StatsPane : Widget() { var project: Project?; var goalNoSpace: Int = 5000 }
```

**할 일**
- [x] 2-1. 한 회차의 글자수 세기: 공백 포함 / 공백 제외 / 문장 수
- [x] 2-2. 프로젝트 전체 회차 목록과 합계
- [x] 2-3. `StatsPane`: 회차별 표와 목표 대비 막대(%)
- [x] 2-4. 원고지 매수 환산(200자 기준)

**완료 조건**: 빈 파일, 한글·영어·이모지가 섞인 글, 줄바꿈만 있는 글에 대한 테스트가 있어야 합니다.

---

## 작업 3. 설정집 ★★

긴 연재에서 인물 설정이 꼬이지 않도록 인물·세계관 카드를 보여 줍니다.

**약속**
```kotlin
data class CharacterSheet(val name: String, val aliases: List<String>, val description: String, val path: Path)
object CharacterRepository { fun load(project: Project): List<CharacterSheet> }
class CharactersPane : Widget() { var project: Project? }
```

**할 일**
- [x] 3-1. `characters/*.md` 읽기: 첫 줄 `# 이름`, `별칭: 가, 나` 줄, 나머지는 설명
- [x] 3-2. `CharactersPane`: 인물 목록과 선택한 인물의 설명
- [x] 3-3. 등장 기록: 이름·별칭이 몇 화에 몇 번 나왔는지 (`world/CharacterUsage.kt`)
- [x] 3-4. `world/*.md` 세계관 카드도 같은 방식으로 읽기

**완료 조건**: 별칭이 없는 파일, 빈 파일, 제목이 없는 파일에서도 오류 없이 동작하는지 테스트해야 합니다.

---

## 작업 4. 찾기·바꾸기 ★★

"이 인물 이름이 어디 나왔지?", "주인공 이름을 바꾸고 싶다"를 해결합니다.

**약속**
```kotlin
data class SearchHit(val path: Path, val line: Int, val column: Int, val preview: String)
object ProjectSearch { fun find(project: Project, query: String): List<SearchHit> }
class SearchPane : Container() {
    var project: Project?; var onOpenHit: (SearchHit) -> Unit
    var beforeReplace: () -> Unit; var onReplaced: (Int) -> Unit   // 바꾸기 전 저장 / 바꾼 뒤 다시 읽기
}
```

**할 일**
- [x] 4-1. `ProjectSearch.find`: 원고 전체에서 찾고 줄·칸 위치 반환
- [x] 4-2. `SearchPane`: 기존 `TextFieldWidget` 을 입력창으로 쓰고 결과 목록 표시, 클릭하면 `onOpenHit` 호출
- [x] 4-3. 전체 바꾸기(`search/ProjectReplace.kt`): 바꾸기 전에 바뀔 목록 미리 보기
- [x] 4-4. 조사 처리: "민준이가 → 서연이가"처럼 받침에 따라 조사(이/가, 을/를, 은/는)도 맞춰 바꾸기

**완료 조건**: 결과가 0개인 경우, 한 줄에 여러 번 나오는 경우, 받침 있는/없는 이름으로 바꾸는 경우를 테스트해야 합니다.

---

## 작업 5. 내보내기 ★★

다 쓴 원고를 연재처나 전자책으로 내보냅니다. **새 라이브러리 없이** 만들어 주세요.

**약속**
```kotlin
object TxtExporter  { fun export(project: Project, target: Path) }
object EpubExporter { fun export(project: Project, target: Path) }
```

**할 일**
- [x] 5-1. TXT: 회차 순서대로 합치고, 회차 사이에 빈 줄 2개
- [x] 5-2. 연재처용 정리: 마크다운 기호 제거(`# 제목` → `제목`, `**굵게**` → `굵게`), 문단 사이 빈 줄 맞추기
- [x] 5-3. 회차 하나를 클립보드용 문자열로 만들기 (`export/ChapterText.kt`)
- [x] 5-4. EPUB: `java.util.zip` 으로 직접 만들기 (막히면 리드와 상의해 주세요)

**완료 조건**: 임시 폴더에 가짜 프로젝트를 만들고 결과 파일 내용을 비교하는 테스트가 있어야 합니다.

---

## 작업 6. 원고 기록 ★★★

"어제 쓴 문단이 더 나았는데"를 해결합니다. 저장할 때마다 Git으로 기록하고, 문장 단위로 비교합니다. `project/ProjectService.kt` 의 JGit 사용법은 참고만 하고, 코드는 `vcs/` 에 새로 작성해 주세요.

**약속**
```kotlin
data class CommitInfo(val id: String, val message: String, val author: String, val time: Instant)
class HistoryService(val root: Path) {
    fun log(limit: Int = 100): List<CommitInfo>
    fun commitAll(message: String): CommitInfo?   // 바뀐 게 없으면 null
}
class HistoryPane : Widget() { var project: Project?; var activePath: Path? }   // activePath = 비교할 회차
```

**할 일**
- [x] 6-1. `log`: 최근 기록 목록
- [x] 6-2. `commitAll`: 바뀐 파일을 모두 기록하고, 바뀐 게 없으면 `null`
- [x] 6-3. `HistoryPane`: 기록 목록(시간, 메시지) 표시
- [x] 6-4. 특정 시점의 원고 읽기 `fileAt(commitId, path): String?`
- [x] 6-5. 문장 단위 비교(`vcs/SentenceDiff.kt`): 두 글을 문장으로 나눠 추가/삭제/변경된 문장 반환

**완료 조건**: 임시 폴더에 `Git.init()` 으로 저장소를 만들어 기록 → 조회 → 비교까지 테스트해야 합니다.

---

## 2차 작업: 형태소 분석 활용 (2026-10-29 시작)

1차는 글자 단위로 만들었습니다. 2차는 같은 기능을 **형태소(낱말의 뜻 단위)** 로 더 똑똑하게 만듭니다. 예를 들어 "먹었다 / 먹고 / 먹는"을 모두 `먹다` 한 낱말로 알아봅니다.

- **담당과 경로는 1차와 똑같습니다.** 위 "한눈에 보기" 표의 경로 밖은 고치지 않습니다. 그래서 2차도 서로 충돌하지 않습니다.
- **할 일은 작업마다 3개이고, 바이브코딩으로 합쳐서 2시간쯤 걸리게 잡았습니다.** 할 일 하나에 40분 정도입니다.
- 리드가 공용 도구와 **빈 껍데기**를 미리 만들어 두었습니다(아래 체크 표시). 껍데기는 빈 결과를 돌려주고 `TODO(작업 N-M)` 주석이 달려 있습니다. 그 함수 안만 채우면 됩니다.

### 리드 사전 작업 (끝남)

- [x] `lexer/Morphology.kt`: 어디서나 쓰는 형태소 분석 입구와 품사 도우미
- [x] 원고를 열면 Kiwi를 미리 불러 두기(`Morphology.warmUp()`)
- [x] 설정집 인물 이름·별칭을 Kiwi 사용자 사전(고유명사 NNP)에 자동 등록. 이제 "하늘이 웃었다"의 `하늘`도 인물 이름으로 분석됩니다.
- [x] `.md` 파일만 형태소 분석을 하고, json 같은 파일은 하지 않음
- [x] 작업마다 빈 껍데기와 `Linter` 연결 (작업 1의 형태소 규칙은 이미 저장할 때 실행됩니다)
- [x] 사용 예시 테스트: `test/lexer/MorphologyTest.kt`

### 공용 도구 사용법 (`lexer/Morphology.kt`, 가져다 쓰기만)

```kotlin
val morphemes = Morphology.analyze("민준이가 밥을 먹었다.")
// 민준/NNP 이/XSN 가/JKS 밥/NNG 을/JKO 먹/VV 었/EP 다/EF ./SF
morphemes.filter { it.isContentWord }.map { it.lemma }   // [민준, 밥, 먹다]
morphemes.filter { it.isParticle }.map { it.surface }    // [가, 을]
morphemes.filter { it.isEnding }.map { it.surface }      // [었, 다]

Morphology.analyzeLines(lines)  // 줄마다 따로 분석. 위치(start, end)는 그 줄 안의 글자 위치
Morphology.isAvailable          // Kiwi를 못 불러왔으면 false → 이때는 빈 결과를 돌려주세요
```

| 도우미 | 뜻 | 품사 태그 |
| --- | --- | --- |
| `isNoun` | 명사·대명사·수사 | NNG NNP NNB NP NR |
| `isProperNoun` | 고유명사(인물·지명) | NNP |
| `isPredicate` | 동사·형용사 줄기 | VV VA VX VCP VCN |
| `isParticle` | 조사 | J로 시작 (JKS 이/가, JKO 을/를, JX 은/는 …) |
| `isEnding` | 어미 | E로 시작 (EP 었, EF 다, EC 고 …) |
| `isAdverb` | 부사 | MAG MAJ |
| `isContentWord` | 뜻이 있는 낱말 | NNG NNP VV VA MAG |
| `lemma` | 기본형 | 동사·형용사는 줄기+"다" (`먹` → `먹다`), 나머지는 그대로 |

**알아 둘 것**
- **동사·형용사는 줄기로 나옵니다.** "아름다웠다"는 `아름답/VA 었/EP 다/EF`로 나뉩니다. 그래서 원래 글자 위치(start~end)가 서로 겹칠 수 있습니다.
- **"민준이가"의 `이`는 조사가 아니라 접미사 `XSN`입니다.**
- **테스트도 진짜 Kiwi로 돌립니다.** 처음 한 번은 모델(약 88MB)을 자동으로 내려받으니 인터넷이 필요합니다.
- **결과가 이상하면** 먼저 `println(Morphology.analyze("문장"))` 으로 품사를 찍어 보세요. AI에게도 이 결과를 보여 주면 훨씬 정확하게 고칩니다.

### 2차 할 일

| # | 약속(껍데기 파일) | 할 일 |
| --- | --- | --- |
| 1 | `inspect/MorphemeRule.kt`, `RepeatedWordRule.kt`, `PassiveVoiceRule.kt` | 1-6, 1-7, 1-8 |
| 2 | `stats/WordStats.kt` | 2-5, 2-6, 2-7 |
| 3 | `world/CharacterSuggestions.kt` | 3-5, 3-6, 3-7 |
| 4 | `search/LemmaSearch.kt` | 4-5, 4-6, 4-7 |
| 5 | `export/ChapterTags.kt` | 5-5, 5-6, 5-7 |
| 6 | `vcs/MorphemeDiff.kt` | 6-6, 6-7, 6-8 |

#### 작업 1. 문장 검수

```kotlin
interface MorphemeRule { val id: String; fun check(lines: List<String>, morphemes: List<List<Morpheme>>): List<Inspection> }
```
- [ ] 1-6. `RepeatedWordRule`: 한 문단(빈 줄로 나뉨) 안에서 같은 낱말(`isContentWord`, `lemma` 기준)이 3문장 안에 다시 나오면 `INFO`. 예: "그는 문을 열었다. 문 밖은 추웠다." → `문` 반복. 조사·어미는 세지 않습니다.
- [ ] 1-7. `EndingRepetitionRule` 을 형태소 기반으로 바꾸기: 문장 끝의 `EP`+`EF` 조합(예: `었`+`다`)이 연속 3문장 같으면 `INFO`. `MorphemeRule` 로 바꾸고 `Linter.morphemeRules` 로 옮기면 됩니다.
- [ ] 1-8. `PassiveVoiceRule`: 이중 피동이면 `WARNING`. 예: "열려졌다"(`열리/VV 어/EC 지/VX`), "되어지다"(`되/VV 어/EC 지/VX`), "보여지다".

**완료 조건**: 규칙마다 "걸리는 예문"과 "안 걸리는 예문" 테스트가 있어야 합니다.

#### 작업 2. 분량 통계

```kotlin
data class WordCount(val lemma: String, val partOfSpeech: String, val count: Int)
data class PosRatio(val nouns: Int, val predicates: Int, val adverbs: Int, val total: Int)
object WordStats { fun topWords(text: String, limit: Int = 10): List<WordCount>; fun posRatio(text: String): PosRatio }
```
- [ ] 2-5. `topWords`: 뜻 있는 낱말만(`isContentWord`) 기본형으로 묶어 많이 쓴 순서대로. "먹었다 먹고"는 `먹다` 2번입니다.
- [ ] 2-6. `posRatio`: 명사·동사형용사·부사 개수. `adverbsPer100`(뜻 있는 낱말 100개당 부사 수)은 이미 계산됩니다.
- [ ] 2-7. `StatsPane`: 회차 줄을 클릭하면 그 회차의 "자주 쓴 낱말 Top 10"과 "부사 100개당 N개"를 아래에 보여 주기.

**완료 조건**: 빈 글, 대사만 있는 글, 같은 동사를 여러 형태로 쓴 글에 대한 테스트가 있어야 합니다.

#### 작업 3. 설정집

```kotlin
data class CharacterSuggestion(val name: String, val count: Int, val chapters: Int)
object CharacterSuggestions { fun suggest(project: Project, minCount: Int = 3): List<CharacterSuggestion> }
```
- [ ] 3-5. `suggest`: 원고에 `minCount`번 넘게 나온 고유명사(`isProperNoun`) 중 `characters/`·`world/` 카드의 이름과 별칭에 없는 것. 많이 나온 순서대로 돌려줍니다.
- [ ] 3-6. `CharactersPane`: 목록 맨 아래 "추천 인물" 칸에 후보와 등장 횟수 보여 주기.
- [ ] 3-7. `CharacterUsage.count` 를 형태소 기반으로 바꾸기: 이름과 **같은 형태소**만 셉니다. 그래서 "하늘색"이나 "서연아"의 일부로 잘못 세지 않습니다. Kiwi를 못 쓰면 지금처럼 글자로 셉니다.

**완료 조건**: 카드에 이미 있는 이름은 추천하지 않는다, 별칭도 제외된다, 원고가 없으면 빈 목록이다 — 이 세 가지 테스트가 있어야 합니다.

#### 작업 4. 찾기·바꾸기

```kotlin
object LemmaSearch { fun find(project: Project, lemma: String): List<SearchHit> }
```
- [ ] 4-5. `LemmaSearch.find`: "먹다"를 찾으면 먹었다·먹고·먹는이 모두 걸리게. 명사는 그대로 비교합니다. 찾은 위치는 그 형태소의 `start` 입니다.
- [ ] 4-6. `SearchPane`: "기본형으로 찾기" 버튼(켜고 끄기)을 넣고, 켜면 `LemmaSearch` 를 쓰기.
- [ ] 4-7. `ProjectReplace` 조사 맞춤을 형태소로 확인하기: 바꿀 이름 **바로 다음 형태소가 조사(`isParticle`)일 때만** 조사를 고칩니다. "민준이가"처럼 `이/XSN`이 끼면 그것까지 함께 처리합니다.

**완료 조건**: 결과 0개, 여러 활용형이 한 줄에 있는 경우, 이름 뒤가 조사가 아닌 경우("민준호")를 테스트해야 합니다.

#### 작업 5. 내보내기

```kotlin
object ChapterTags { fun of(text: String, limit: Int = 5): List<String>; fun ofProject(project: Project, limit: Int = 8): List<String> }
```
- [ ] 5-5. `ChapterTags.of`: 많이 나온 고유명사 먼저, 그다음 일반 명사. 한 글자 낱말과 "것·수·때·말" 같은 흔한 말은 뺍니다.
- [ ] 5-6. `ofProject` 를 만들고, `EpubExporter` 가 책 정보에 `<dc:subject>`로 넣기.
- [ ] 5-7. `ChapterText` 에 연재처용 해시태그 한 줄 만들기 함수 추가(예: `#서연 #마법학교`). 기존 함수는 그대로 둡니다.

**완료 조건**: 가짜 프로젝트로 EPUB을 만들어 `content.opf`에 태그가 들어갔는지 확인하는 테스트가 있어야 합니다.

#### 작업 6. 원고 기록

```kotlin
enum class WordChangeKind { ENDING, PARTICLE, WORDS }
object MorphemeDiff { fun classify(before: String, after: String): WordChangeKind; fun changedWords(before: String, after: String): List<Pair<String?, String?>> }
```
- [ ] 6-6. `classify`: 두 문장의 형태소를 비교해서, 다른 곳이 전부 어미(`E*`)이면 `ENDING`, 전부 조사(`J*`)이면 `PARTICLE`, 그 밖은 `WORDS`.
- [ ] 6-7. `HistoryPane`: `CHANGED` 문장 옆에 `[어미]` / `[조사]` / `[낱말]` 표시.
- [ ] 6-8. `changedWords`: 달라진 뜻 있는 낱말을 "전 → 후"로 돌려주고, `HistoryPane`에 "문 → 창문"처럼 보여 주기.

**완료 조건**: "먹었다→먹는다"는 `ENDING`, "민준이→민준은"은 `PARTICLE`, "문을 열었다→창문을 열었다"는 `WORDS` 인 테스트가 있어야 합니다.

### 2차에서 AI에게 붙일 말

```text
저는 PLAN.md 의 작업 [3] 담당입니다. 먼저 PLAN.md 의 "2차 작업" 부분과 lexer/Morphology.kt 를 읽어 주세요.
"한눈에 보기" 표에 있는 작업 [3]의 경로 안의 파일만 만들거나 고쳐 주세요. lexer/ 는 가져다 쓰기만 하세요.
"약속"에 적힌 클래스·함수 이름과 인자는 바꾸지 마세요. TODO(작업 3-5) 주석이 있는 함수 안을 채우면 됩니다.
품사가 헷갈리면 먼저 Morphology.analyze 결과를 println 으로 찍는 테스트를 만들어 확인해 주세요.
이번 할 일: [3-5. CharacterSuggestions.suggest]
```

---

## 공용 파일 (리드만 수정)

바꿔야 할 부분이 있으면 직접 고치지 말고, GitHub Issue에 "어떤 파일을 왜 바꿔야 하는지" 적어서 리드에게 요청해 주세요.

- `edited/App.kt`, `edited/ui/` 바로 아래 파일 전부(`ProjectWorkspaceWidget.kt`, `Widget.kt`, `RootPane.kt` 등)
- `edited/core/**`, `edited/lexer/**`, `edited/project/**`, `edited/theme/**`, `edited/render/**`, `edited/event/**`
- `edited/ui/docking/**`, `edited/ui/editor/**`, `edited/ui/layout/**`, `edited/ui/components/**`, `edited/ui/panes/PaneStyle.kt`
- 여러 작업이 함께 쓰는 도구: `core/Sentences.kt`(문장 나누기), `project/ChapterTitle.kt`(회차 제목), `ui/panes/PaneStyle.kt`(패널 그리기), `lexer/Morphology.kt`(형태소 분석), `ui/components/PressFeedback.kt`(클릭 피드백) — 가져다 쓰기만 하세요
- `edited/AppInfo.kt`(버전 정보), `app/src/main/resources/**`(번들 폰트 등)
- 빌드 파일(`*.gradle.kts`, `gradle/**`, `buildSrc/**`) — **새 라이브러리 추가는 리드만 할 수 있습니다**
- 문서: `README.md`, `AGENTS.md`, `PLAN.md`

다른 작업의 코드나 공용 클래스는 **가져다 쓰는 것은 자유**이지만, 고치는 것은 안 됩니다.

---

## 작업 규칙

1. **내 경로 밖의 파일은 고치지 않습니다.**
2. **생성자 인자를 바꾸지 않습니다** (`AGENTS.md`). 기능이 더 필요하면 기본값이 있는 프로퍼티나 새 함수를 추가해 주세요.
3. **"약속"에 적힌 이름과 인자는 바꾸지 않습니다.** 내부 구현이나 새 함수 추가는 자유입니다.
4. 프로젝트 전체에 "Reformat Code"를 실행하지 마세요. 내가 고치지 않은 파일까지 바뀝니다.
5. `.idea/`, `build/`, `app/models/` 는 커밋하지 않습니다. `git push --force` 도 사용하지 않습니다.
6. **할 일 한 줄 = PR 하나**입니다. PR 제목에 `[3-2]` 처럼 번호를 적어 주세요.

### 매일 작업 순서

```bash
git switch master
git pull                          # 1) 최신 내용 받기
git switch -c 3/characters-pane   # 2) 새 브랜치 만들기: 작업번호/짧은-설명
gradlew.bat test                  # 3) 작업 후 테스트
git status                        # 4) 내 경로 밖 파일이 바뀌었으면 git restore <파일>
git pull origin master            # 5) 올리기 전에 최신 master 합치기
git push -u origin 3/characters-pane   # 6) GitHub에서 PR 만들기
```

충돌이 나면 혼자 해결하려고 다른 사람 코드를 지우지 마시고, **화면 캡처와 함께 리드에게 알려 주세요.**

### AI(Claude, Cursor 등)에게 작업을 시킬 때

항상 맨 앞에 아래 내용을 붙여 주세요.

```text
저는 PLAN.md 의 작업 [3] 담당입니다. 먼저 PLAN.md 를 읽어 주세요.
"한눈에 보기" 표에 있는 작업 [3]의 경로 안의 파일만 만들거나 고쳐 주세요.
그 밖의 파일을 고쳐야 할 것 같으면 고치지 말고 멈춘 뒤, 어떤 파일을 왜 바꿔야 하는지 알려 주세요.
"약속"에 적힌 클래스·함수 이름과 인자는 바꾸지 마세요.
이번 할 일: [3-2. CharactersPane]
```

작업이 끝나면 PR을 올리기 전에 `git status` 로 바뀐 파일이 내 경로 안에만 있는지 꼭 확인해 주세요.
