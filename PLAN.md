# Texted 작업 계획

작성 기준: 2026-10-30

## 목표

**소설 쓰는 사람을 위한 한글(워드프로세서)을 만듭니다.**
작가가 원고를 쓰고, 고치고, 분량을 맞추고, 설정을 챙기고, 연재처에 올리는 일을 한 프로그램 안에서 끝낼 수 있게 하는 것이 목표입니다.

**현재 상태: 작업 1~6의 1차(글자 단위) 할 일과 2차(형태소 단위) 할 일이 모두 끝났습니다.** 테스트 197개가 통과합니다. 남은 한계와 다음 후보는 맨 아래 "알려진 한계와 다음 후보"에 있습니다.

## 앱에서 쓰는 법

| 기능 | 여는 법 |
| --- | --- |
| 원고 / 찾기 / 설정 / 분량 / 기록 패널 | 왼쪽 레일 버튼 |
| 새로 만들기(에피소드·캐릭터·세계관·폴더) / 이름 바꾸기 / 확장자 바꾸기 | 원고 패널에서 파일·폴더 우클릭 (새로 만들기는 파일 메뉴에도) |
| 찾기 / 바꾸기 | `Ctrl+F`·`Ctrl+Shift+F` / `Ctrl+H` (편집기에서 고른 글자로 바로 찾기, 커서가 입력 칸으로 이동) |
| 활용형까지 찾기 ("먹다" → 먹었다·먹고·먹는) | 찾기 패널의 **기본형으로 찾기** 버튼 |
| 회차별 자주 쓴 낱말 Top 10, 부사 100개당 N개 | 분량 패널에서 회차 줄 클릭 |
| 원고에 자주 나오는데 카드가 없는 이름 | 설정집 패널 맨 아래 **추천 인물** |
| 바뀐 문장이 어미·조사·낱말 중 무엇이 바뀐 것인지 (`[어미]` `[조사]` `[낱말] 문 → 창문`) | 원고 기록 패널에서 기록을 고르면 |
| 저장 + 기록 남기기 + 검수·분량 갱신 | `Ctrl+S` |
| TXT·EPUB 내보내기 (`export/` 폴더, EPUB에는 키워드 태그가 들어감) | `Ctrl+Shift+E` |
| 지금 회차 본문 복사 (연재처 붙여넣기용) | `Ctrl+Shift+C` |
| 다음 / 이전 문제로 이동 | `F2` / `Shift+F2` |
| 편집기 글꼴 크기 (아래 가운데에 "글꼴 크기: 22pt" 표시) | `Ctrl+휠` |
| 모든 메뉴와 단축키 | 위쪽 메뉴 막대, 도움말 › 단축키 |

경로 표기: `edited/` = `app/src/main/kotlin/io/github/nd2026/edited/`, `test/` = `app/src/test/kotlin/io/github/nd2026/edited/`

---

## 한눈에 보기

| # | 작업 | 작가에게 주는 것 | 난이도 | 고칠 수 있는 경로 (이 밖은 수정 금지) | 상태 | 담당자 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | 문장 검수 | 긴 문장·어미 반복·따옴표 실수·낱말 반복·이중 피동 알림 | ★ | `edited/inspect/**`, `edited/ui/panes/ProblemsPane.kt`, `test/inspect/**` | 1차 ✓ 2차 ✓ | |
| 2 | 분량 통계 | 회차별 글자수(공백 포함/제외), 목표 분량 달성률, 자주 쓴 낱말 | ★ | `edited/stats/**`, `edited/ui/panes/StatsPane.kt`, `test/stats/**` | 1차 ✓ 2차 ✓ | |
| 3 | 설정집 | 인물·세계관 카드, 인물 등장 회차, 추천 인물 | ★★ | `edited/world/**`, `edited/ui/panes/CharactersPane.kt`, `test/world/**` | 1차 ✓ 2차 ✓ | |
| 4 | 찾기·바꾸기 | 원고 전체 검색, 활용형 검색, 인물 이름 한 번에 바꾸기 | ★★ | `edited/search/**`, `edited/ui/panes/SearchPane.kt`, `test/search/**` | 1차 ✓ 2차 ✓ | |
| 5 | 내보내기 | 연재처에 붙여넣을 TXT, 전자책 EPUB, 키워드 태그 | ★★ | `edited/export/**`, `test/export/**` | 1차 ✓ 2차 ✓ | |
| 6 | 원고 기록 | 자동 저장된 이전 버전 보기, 문장 단위 변경 비교, 어미·조사·낱말 구분 | ★★★ | `edited/vcs/**`, `edited/ui/panes/HistoryPane.kt`, `test/vcs/**` | 1차 ✓ 2차 ✓ | |

---

## 공용 도구 (리드가 만든 것, 가져다 쓰기만)

### 형태소 분석 (`lexer/Morphology.kt`)

1차는 글자 단위로, 2차는 **형태소(낱말의 뜻 단위)** 로 만들었습니다. "먹었다 / 먹고 / 먹는"을 모두 `먹다` 한 낱말로 알아봅니다.

```kotlin
val morphemes = Morphology.analyze("민준이가 밥을 먹었다.")
// 민준/NNP 이/XSN 가/JKS 밥/NNG 을/JKO 먹/VV 었/EP 다/EF ./SF
morphemes.filter { it.isContentWord }.map { it.lemma }   // [민준, 밥, 먹다]
morphemes.filter { it.isParticle }.map { it.surface }    // [가, 을]
morphemes.filter { it.isEnding }.map { it.surface }      // [었, 다]

Morphology.analyzeLines(lines)  // 줄마다 따로 분석(병렬). 위치(start, end)는 그 줄 안의 글자 위치
Morphology.isAvailable          // Kiwi를 못 불러왔으면 false → 이때는 빈 결과를 돌려주세요
Morphology.awaitDictionary()    // 인물 이름 사전을 다시 만드는 중이면 끝날 때까지 기다림 (화면 스레드에서는 부르지 마세요)
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

- 원고를 열면 Kiwi를 미리 불러 두고(`warmUp`), 설정집 인물 이름·별칭을 Kiwi 사용자 사전(고유명사 NNP)에 자동 등록합니다. 그래서 "하늘이 웃었다"의 `하늘`도 인물 이름으로 분석됩니다.
- 원고 파일(`.episode`·`.character`·`.world`·`.md`·`.txt`)만 형태소 분석을 하고, json 같은 파일은 하지 않습니다.
- `lexer/CommonWords.kt`: 하다·있다 같은 가벼운 동사, 이미·다시 같은 흔한 부사, 것·수·때 같은 흔한 명사 목록. 반복 검사·자주 쓴 낱말·태그가 이 목록을 건너뜁니다.
- 사용 예시 테스트: `test/lexer/MorphologyTest.kt`

**알아 둘 것**
- **동사·형용사는 줄기로 나옵니다.** "아름다웠다"는 `아름답/VA 었/EP 다/EF`로 나뉩니다. 그래서 원래 글자 위치(start~end)가 서로 겹칠 수 있습니다.
- **"민준이가"의 `이`는 조사가 아니라 접미사 `XSN`입니다.** ("민준이 웃었다"의 `이`는 조사 `JKS`입니다.)
- **Kiwi는 같은 글자도 앞뒤에 따라 다르게 나눕니다.** 예: "지아로 갔다"는 `지아/NNP 로/JKB` 이지만, 문장 끝의 "지아로"는 `지아로/NNG` 한 덩어리입니다. 결과가 이상하면 먼저 `println(Morphology.analyze("문장"))` 으로 품사를 찍어 보세요. AI에게도 이 결과를 보여 주면 훨씬 정확하게 고칩니다.
- **테스트도 진짜 Kiwi로 돌립니다.** 처음 한 번은 모델(약 88MB)을 자동으로 내려받으니 인터넷이 필요합니다.
- 인물 이름을 사전에 넣어야 하는 테스트는 `DefaultMorphemeAnalyzer.setProjectWords(listOf("카이든"))` 다음 `Morphology.awaitDictionary()` 를 부르고, 끝나면 `setProjectWords(emptyList())` 로 되돌려 주세요 (`test/world/CharacterInsightsTest.kt` 참고).

### 병렬 처리와 백그라운드 (`core/Parallel.kt`, `ui/panes/BackgroundWork.kt`)

형태소 분석은 느립니다(한 줄에 약 1ms). 회차가 여러 개면 **회차마다 따로 돌리지 말고 `Parallel.map` 으로 한꺼번에** 돌려 주세요. 모든 CPU 코어가 나눠서 처리합니다(2,000줄 기준 4~6배).

```kotlin
// 회차별로 계산 - 결과 순서는 chapters 순서 그대로
val perChapter = Parallel.map(project.chapters()) { chapter ->
    val text = Files.readString(chapter.path)
    WordStats.topWords(text)          // 안에서 Morphology를 써도 됩니다
}
Parallel.forEach(items) { ... }      // 결과가 필요 없을 때 (순서는 보장 안 됨)
```

- `Morphology.analyze` / `analyzeLines` 는 어느 스레드에서 불러도 안전하고, `analyzeLines` 는 이미 줄 단위로 병렬입니다.
- `Parallel.map` 안에서 또 `Parallel.map` 이나 `analyzeLines` 를 불러도 됩니다(멈추지 않습니다).
- **`Parallel.map` 안에서 위젯·필드 같은 공유 상태를 고치지 마세요.** 계산 결과만 돌려주고, 화면 갱신은 결과를 받은 뒤에 합니다.
- **패널에서 원고 전체를 분석하면 화면이 멈춥니다.** 패널은 `BackgroundWork` 로 계산을 백그라운드에서 돌리고 결과만 화면 스레드에서 받습니다. 더 새 요청이 시작되면 이전 결과는 버려집니다. 지금 `StatsPane`·`CharactersPane`·`HistoryPane`, 그리고 `SearchPane`의 기본형 찾기가 이렇게 합니다.

```kotlin
private val work = BackgroundWork()
work.run({ CharacterSuggestions.suggest(project) }) { suggestions = it; requestRepaint() }   // 앞은 백그라운드, 뒤는 화면 스레드
internal fun awaitIdle() = work.awaitIdle()    // 테스트가 결과를 기다릴 때
```

### Texted 문서 형식 (`project/NovelFormat.kt`, `project/ProjectFiles.kt`)

새로 만드는 파일은 마크다운(`.md`)이 아니라 Texted 전용 형식입니다. **세 형식 모두 마크업이 없는 순수 텍스트**(UTF-8, 문단 = 한 줄)이고, 확장자와 들어가는 폴더만 다릅니다.

| 형식 | 확장자 | 폴더 | 제목·이름 | 내용 |
| --- | --- | --- | --- | --- |
| 에피소드 | `.episode` | `manuscript/` | 파일 이름에서 번호를 뺀 것 (`03. 낯선 손님.episode` → `낯선 손님`) | 본문만. `# 제목` 줄 없음 |
| 캐릭터 | `.character` | `characters/` | 파일 이름 (`한서연.character`) | `별칭: 서연, 한 대리` 줄(선택) + 설명 |
| 세계관 | `.world` | `world/` | 파일 이름 | 캐릭터와 같음 |

- 회차 제목은 `ChapterTitle.of(path, text)` 로 얻으면 형식에 맞게 나옵니다.
- **`.md`·`.txt` 도 계속 읽힙니다.** `ProjectNode.File.isChapter` 가 다섯 가지 확장자를 모두 원고로 봅니다. 확장자에 `.md` 를 직접 비교하는 코드가 있으면 `isChapter` 나 `NovelFormat` 으로 바꿔 주세요.
- 새 프로젝트의 첫 회차는 `01. 제1화.episode`(빈 파일)입니다. 테스트에서 가짜 원고를 쓰려면 `ProjectService.FIRST_CHAPTER` 를 지운 뒤 쓰세요(예: `test/export/ExportTest.kt`, `test/world/CharacterInsightsTest.kt`).

### 맞춤법 검사와 지시문 `@` (`spell/`, `core/Directives.kt`)

- **맞춤법**: 자주 틀리는 맞춤법(됬다, 몇일, 할께, 왠일, 깨끗히 …)에 편집기에서 빨간 물결 밑줄이 생기고, 저장하면 Problems 패널에도 `ERROR`로 나옵니다. 규칙은 `spell/SpellChecker.kt` 에 있습니다. Kiwi의 오타 모델은 `됬`을 그냥 `되+었`으로 고쳐 읽어서 맞춤법 검사에 쓸 수 없었습니다.
- **`@` 는 지시문의 시작입니다.** 글자·숫자 바로 뒤의 `@`(메일 주소)는 지시문이 아닙니다. 지시문은 노란색이고, 평소에는 `@` 칩으로 접혀 있다가 칩을 누르면(커서가 안에 들어가면) 펼쳐집니다. 보기 › 지시문(@) 모두 펼치기로 전부 볼 수도 있습니다.
- **지시문은 원고가 아닙니다.** 글자수(`ManuscriptStats`), 내보내기·복사(`ChapterText`), 검수 규칙, 낱말 통계·검색·태그가 모두 지시문을 빼고 봅니다.

| 쓰는 곳 | 예 | 무시하는 범위 |
| --- | --- | --- |
| 파일 맨 위 (본문보다 앞) | `@file:Suppress("맞춤법")` | 파일 전체 |
| 혼자 있는 줄 | `@Suppress("긴 문장")` | 바로 다음 줄 |
| 문장 앞 | `그는 웃었다. @Suppress("맞춤법") 됬다고 했다.` | 그 문장 하나 |

- 이름: `맞춤법`, `긴 문장`, `어미 반복`, `따옴표`, `습관어`, `낱말 반복`, `이중 피동`(규칙 id `long-sentence` 등도 됨). 이름 없이 `@Suppress` 또는 `@Suppress("전부")` 는 모든 검사. `@무시(...)` 는 `@Suppress(...)` 와 같습니다. 다른 이름(`@메모("…")`)도 쓸 수 있고, 검사에는 영향이 없이 접히기만 합니다.
- 검수는 `spell/Proofreader.run` 이 `Linter.rules`·`Linter.morphemeRules` 를 하나씩 돌리면서 지시문을 빼고 `@Suppress` 를 적용합니다. **작업 1은 새 규칙을 `Linter` 목록에 넣기만 하면 됩니다.** `id` 가 `@Suppress` 이름이 되니, 한글 이름이 필요하면 리드에게 알려 주세요.

---

## 작업 1. 문장 검수 ★

작가가 쓰면서 놓치기 쉬운 실수를 Problems 패널에 보여 줍니다. 규칙 하나당 파일 하나로 만들어 주세요(예: `inspect/LongSentenceRule.kt`).

**약속**
```kotlin
interface InspectionRule {
    val id: String
    fun check(lines: List<String>): List<Inspection>   // core.Inspection 재사용, line은 0부터
}
interface MorphemeRule { val id: String; fun check(lines: List<String>, morphemes: List<List<Morpheme>>): List<Inspection> }
object Linter { fun run(text: String): List<Inspection> }
class ProblemsPane : Widget() { var project: Project?; var inspections: Map<Path, List<Inspection>>; var onNavigate: (Path, Int) -> Unit }
```

**할 일**
- [x] 1-1. 긴 문장: 한 문장이 120자를 넘으면 `WARNING`
- [x] 1-2. 어미 반복: 연속 3문장 이상이 같은 어미(`~했다.`)로 끝나면 `INFO`
- [x] 1-3. 따옴표·괄호 짝: `" "`, `“ ”`, `‘ ’`, `( )` 가 맞지 않는 줄은 `ERROR`
- [x] 1-4. 습관어: "갑자기", "순간", "그리고" 등이 한 문단에 3번 이상 나오면 `INFO`
- [x] 1-5. `ProblemsPane`: 문제 목록을 보여 주고, 클릭하면 `onNavigate(파일, 줄)` 호출
- [x] 1-6. `RepeatedWordRule`: 한 문단(빈 줄로 나뉨) 안에서 같은 낱말(`lemma` 기준)이 3문장 안에 다시 나오면 `INFO`. 예: "그는 문을 열었다. 문 밖은 추웠다." → `문` 반복.
- [x] 1-7. `EndingRepetitionRule` 을 형태소 기반으로: 문장 끝의 `EP`+`EF` 조합(예: `었`+`다`)이 연속 3문장 같으면 `INFO`.
- [x] 1-8. `PassiveVoiceRule`: 이중 피동이면 `WARNING`. 예: "열려졌다", "보여지다", "결정되어졌다".

**완료 조건**: 규칙마다 "걸리는 예문"과 "안 걸리는 예문" 테스트가 있어야 합니다. (`test/inspect/LinterTest.kt`, `MorphemeRulesTest.kt`)

**구현 메모**
- 1-6: "3문장 안"은 처음 쓴 문장을 포함해 세 문장 이내(같은 문장 안의 반복도 포함)입니다. 조사·어미는 세지 않고, **고유명사(이름은 자연스럽게 반복됨)와 하다·있다 같은 흔한 말(`CommonWords`)도 세지 않습니다.** 빈 줄이 나오면 처음부터 다시 셉니다. 한 줄에 같은 낱말은 한 번만 알립니다.
- 1-7: `EndingRepetitionRule` 은 `InspectionRule` 과 `MorphemeRule` 을 **둘 다** 구현합니다. 그래서 `check(lines)` 를 쓰던 코드와 테스트는 그대로 되고(마지막 두 음절을 비교하는 옛 방식), `Linter.morphemeRules` 에서는 형태소로 비교합니다. 그래서 "일어났다 / 갔다 / 먹었다"처럼 글자는 달라도 `었다` 로 같은 어미가 이어지면 잡힙니다. Kiwi가 없으면 형태소 규칙에 빈 목록이 전달되고, 이 규칙은 옛 방식으로 돌아갑니다.
- 1-8: 피동사 목록(열리·닫히·보이·잡히 …)과 `되다` 뒤에 `-어지다` 가 붙은 경우만 잡습니다. "만들어졌다", "기다려졌다", "예뻐졌다"는 정상이라 안 걸립니다. Kiwi가 동사로 나누지 못하는 "되어지다"는 글자 패턴으로도 잡고, 같은 자리는 한 번만 알립니다.

---

## 작업 2. 분량 통계 ★

웹소설은 회차당 분량(예: 공백 제외 5,000자)이 중요합니다. 회차별 글자수와 목표 달성률을 보여 줍니다.

**약속**
```kotlin
data class ChapterStats(val path: Path, val title: String, val chars: Int, val charsNoSpace: Int, val sentences: Int)
object ManuscriptStats { fun of(project: Project): List<ChapterStats> }
class StatsPane : Widget() { var project: Project?; var goalNoSpace: Int = 5000 }

data class WordCount(val lemma: String, val partOfSpeech: String, val count: Int)
data class PosRatio(val nouns: Int, val predicates: Int, val adverbs: Int, val total: Int)
object WordStats { fun topWords(text: String, limit: Int = 10): List<WordCount>; fun posRatio(text: String): PosRatio }
```

**할 일**
- [x] 2-1. 한 회차의 글자수 세기: 공백 포함 / 공백 제외 / 문장 수
- [x] 2-2. 프로젝트 전체 회차 목록과 합계
- [x] 2-3. `StatsPane`: 회차별 표와 목표 대비 막대(%)
- [x] 2-4. 원고지 매수 환산(200자 기준)
- [x] 2-5. `topWords`: 뜻 있는 낱말만 기본형으로 묶어 많이 쓴 순서대로. "먹었다 먹고"는 `먹다` 2번입니다.
- [x] 2-6. `posRatio`: 명사·동사형용사·부사 개수. `adverbsPer100`(뜻 있는 낱말 100개당 부사 수)
- [x] 2-7. `StatsPane`: 회차 줄을 클릭하면 그 회차의 "자주 쓴 낱말 Top 10"과 "부사 100개당 N개"를 아래에 보여 주기.

**완료 조건**: 빈 파일, 한글·영어·이모지가 섞인 글, 줄바꿈만 있는 글, 빈 글, 대사만 있는 글, 같은 동사를 여러 형태로 쓴 글에 대한 테스트가 있어야 합니다. (`test/stats/ManuscriptStatsTest.kt`, `WordStatsTest.kt`)

**구현 메모**
- 2-5: **하다·있다·되다 같은 가벼운 동사(`CommonWords.LIGHT_PREDICATES`)는 뺍니다.** 안 빼면 어느 회차든 맨 위에 나옵니다. 횟수가 같으면 가나다순입니다. 글자수와 마찬가지로 지시문은 낱말로 세지 않습니다.
- 2-6: `total` 은 명사·동사·형용사·부사 등 뜻이 있는 낱말(접속부사 포함)의 수라서 `adverbsPer100` 이 100을 넘지 않습니다.
- 2-7: 분석은 백그라운드에서 하고(`분석하는 중…` 표시), 저장하면 열려 있는 회차의 통계도 다시 계산합니다.

---

## 작업 3. 설정집 ★★

긴 연재에서 인물 설정이 꼬이지 않도록 인물·세계관 카드를 보여 줍니다.

**약속**
```kotlin
data class CharacterSheet(val name: String, val aliases: List<String>, val description: String, val path: Path)
object CharacterRepository { fun load(project: Project): List<CharacterSheet> }
class CharactersPane : Widget() { var project: Project? }

data class CharacterSuggestion(val name: String, val count: Int, val chapters: Int)
object CharacterSuggestions { fun suggest(project: Project, minCount: Int = 3): List<CharacterSuggestion> }
```

**할 일**
- [x] 3-1. `characters/` 파일 읽기: 이름, `별칭: 가, 나` 줄, 나머지는 설명 (`.character`; `.md` 는 첫 줄 `# 이름`)
- [x] 3-2. `CharactersPane`: 인물 목록과 선택한 인물의 설명
- [x] 3-3. 등장 기록: 이름·별칭이 몇 화에 몇 번 나왔는지 (`world/CharacterUsage.kt`)
- [x] 3-4. `world/` 세계관 카드도 같은 방식으로 읽기
- [x] 3-5. `suggest`: 원고에 자주 나온 고유명사 중 카드의 이름과 별칭에 없는 것. 많이 나온 순서대로 돌려줍니다.
- [x] 3-6. `CharactersPane`: 목록 맨 아래 "추천 인물" 칸에 후보와 등장 횟수 보여 주기.
- [x] 3-7. `CharacterUsage.count` 를 형태소 기반으로: 이름과 **같은 형태소**만 셉니다. 그래서 "하늘색"이나 "서연아"의 일부로 잘못 세지 않습니다. Kiwi를 못 쓰면 글자로 셉니다.

**완료 조건**: 별칭이 없는 파일, 빈 파일, 제목이 없는 파일에서도 오류 없이 동작하는지, 카드에 이미 있는 이름·별칭은 추천하지 않는지, 원고가 없으면 빈 목록인지 테스트해야 합니다. (`test/world/CharacterRepositoryTest.kt`, `CharacterInsightsTest.kt`)

**구현 메모**
- 3-5: `minCount` 번 **이상** 나온 이름입니다("3번 넘게"가 아니라 3번부터). 카드의 이름·별칭과 같거나, 서로 포함하는 이름(`한서연` 카드가 있을 때 `서연`, `민준` 카드가 있을 때 `김민준`)도 같은 인물로 보고 뺍니다. 한 글자 이름은 Kiwi의 추측이라 뺍니다. Kiwi가 없으면 빈 목록입니다.
- 3-6: 목록이 길어져서 **목록에 휠 스크롤을 넣었습니다.** 추천 인물을 누르면 아래에 등장 횟수와 "카드를 만들 수 있다"는 안내가 나옵니다. 추천과 등장 기록 계산은 백그라운드에서 합니다.
- 3-7: 이름이 형태소가 시작하는 곳에서 시작해 끝나는 곳에서 끝나야 셉니다. 설정집 카드가 바뀌면 이름 사전 재구성이 끝난 뒤에 세도록 `Morphology.awaitDictionary()` 를 먼저 부릅니다.

---

## 작업 4. 찾기·바꾸기 ★★

"이 인물 이름이 어디 나왔지?", "주인공 이름을 바꾸고 싶다"를 해결합니다.

**약속**
```kotlin
data class SearchHit(val path: Path, val line: Int, val column: Int, val preview: String)
object ProjectSearch { fun find(project: Project, query: String): List<SearchHit> }
object LemmaSearch { fun find(project: Project, lemma: String): List<SearchHit> }
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
- [x] 4-5. `LemmaSearch.find`: "먹다"를 찾으면 먹었다·먹고·먹는이 모두 걸리게. 명사는 그대로 비교합니다. 찾은 위치는 그 형태소의 `start` 입니다.
- [x] 4-6. `SearchPane`: "기본형으로 찾기" 버튼(켜고 끄기)을 넣고, 켜면 `LemmaSearch` 를 쓰기.
- [x] 4-7. `ProjectReplace` 조사 맞춤을 형태소로 확인하기: 바꿀 이름 **바로 다음 형태소가 조사일 때만** 조사를 고칩니다.

**완료 조건**: 결과가 0개인 경우, 한 줄에 여러 번 나오는 경우, 받침 있는/없는 이름으로 바꾸는 경우, 여러 활용형이 한 줄에 있는 경우, 이름 뒤가 조사가 아닌 경우("민준호")를 테스트해야 합니다. (`test/search/SearchAndReplaceTest.kt`, `LemmaSearchTest.kt`)

**구현 메모**
- 4-5: **활용형으로 입력해도 됩니다**("열었다"를 넣으면 `열다`로 찾습니다). `열린다`는 `열리다`(피동사)라서 `열다`의 활용형이 아닙니다. 복합어나 띄어쓴 말은 입력한 그대로 비교합니다.
- 4-6: 기본형 모드에서는 **찾기만** 합니다(기본형으로 바꾸기는 의미가 없어서 "모두 바꾸기"가 꺼집니다). 찾은 글자가 입력과 다른 활용형이라 편집기의 글자 강조도 이 모드에서는 끕니다. 분석은 백그라운드에서 합니다.
- 4-7: 바로 다음 형태소가 조사(`J*`), 이름 접미사 `이`(`XSN`, "민준이가"), 서술격 `이`(`VCP`, "민준이었다")일 때만 고칩니다. 그래서 "민준아버지"의 `아`는 조사가 아니라 `아버지`의 일부라서 건드리지 않습니다. **Kiwi가 문장 끝의 "지아로"를 한 덩어리 명사로 읽는 경우**에는, 이름 뒤에 남은 글자가 정확히 조사 하나일 때만 고칩니다. Kiwi를 못 쓰면 이 확인 없이 글자 규칙(`Josa`)만 씁니다.

---

## 작업 5. 내보내기 ★★

다 쓴 원고를 연재처나 전자책으로 내보냅니다. **새 라이브러리 없이** 만들어 주세요.

**약속**
```kotlin
object TxtExporter  { fun export(project: Project, target: Path) }
object EpubExporter { fun export(project: Project, target: Path) }
object ChapterTags { fun of(text: String, limit: Int = 5): List<String>; fun ofProject(project: Project, limit: Int = 8): List<String> }
```

**할 일**
- [x] 5-1. TXT: 회차 순서대로 합치고, 회차 사이에 빈 줄 2개
- [x] 5-2. 연재처용 정리: 마크다운 기호 제거(`# 제목` → `제목`, `**굵게**` → `굵게`), 문단 사이 빈 줄 맞추기
- [x] 5-3. 회차 하나를 클립보드용 문자열로 만들기 (`export/ChapterText.kt`)
- [x] 5-4. EPUB: `java.util.zip` 으로 직접 만들기
- [x] 5-5. `ChapterTags.of`: 많이 나온 고유명사 먼저, 그다음 일반 명사. 한 글자 낱말과 "것·수·때·말" 같은 흔한 말은 뺍니다.
- [x] 5-6. `ofProject` 를 만들고, `EpubExporter` 가 책 정보에 `<dc:subject>`로 넣기.
- [x] 5-7. `ChapterText.hashtags`: 연재처용 해시태그 한 줄 만들기 (예: `#서연 #마법학교`). 기존 함수는 그대로 둡니다.

**완료 조건**: 임시 폴더에 가짜 프로젝트를 만들고 결과 파일 내용을 비교하는 테스트가 있어야 합니다. EPUB은 `content.opf`에 태그가 들어갔는지 확인합니다. (`test/export/ExportTest.kt`, `ChapterTagsTest.kt`)

**구현 메모**
- 5-5: 횟수가 같으면 먼저 나온 것부터입니다. 지시문과 `#` 제목 줄은 태그가 되지 않습니다. 형태소 분석이 `마법학교`를 `마법`+`학교` 로 나누면 태그도 그렇게 나뉩니다.
- 5-6: 태그가 없으면 `<dc:subject>` 줄이 생기지 않습니다. 회차 분석은 병렬입니다.
- 5-7: **함수만 만들었고 메뉴·단축키에는 아직 연결하지 않았습니다**(리드 몫). 태그가 없으면 빈 문자열이라 호출하는 쪽에서 줄을 건너뛰면 됩니다.

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

enum class WordChangeKind { ENDING, PARTICLE, WORDS }
object MorphemeDiff { fun classify(before: String, after: String): WordChangeKind; fun changedWords(before: String, after: String): List<Pair<String?, String?>> }
```

**할 일**
- [x] 6-1. `log`: 최근 기록 목록
- [x] 6-2. `commitAll`: 바뀐 파일을 모두 기록하고, 바뀐 게 없으면 `null`
- [x] 6-3. `HistoryPane`: 기록 목록(시간, 메시지) 표시
- [x] 6-4. 특정 시점의 원고 읽기 `fileAt(commitId, path): String?`
- [x] 6-5. 문장 단위 비교(`vcs/SentenceDiff.kt`): 두 글을 문장으로 나눠 추가/삭제/변경된 문장 반환
- [x] 6-6. `classify`: 두 문장의 형태소를 비교해서, 다른 곳이 전부 어미(`E*`)이면 `ENDING`, 전부 조사(`J*`)이면 `PARTICLE`, 그 밖은 `WORDS`.
- [x] 6-7. `HistoryPane`: `CHANGED` 문장 옆에 `[어미]` / `[조사]` / `[낱말]` 표시.
- [x] 6-8. `changedWords`: 달라진 뜻 있는 낱말을 "전 → 후"로 돌려주고, `HistoryPane`에 "문 → 창문"처럼 보여 주기.

**완료 조건**: 임시 폴더에 `Git.init()` 으로 저장소를 만들어 기록 → 조회 → 비교까지 테스트해야 합니다. "먹었다→먹는다"는 `ENDING`, "민준이→민준은"은 `PARTICLE`, "문을 열었다→창문을 열었다"는 `WORDS` 입니다. (`test/vcs/HistoryTest.kt`, `MorphemeDiffTest.kt`)

**구현 메모**
- 6-6: 두 문장의 형태소를 최장 공통 부분열(LCS)로 맞추고, 맞지 않고 남은 쪽만 봅니다. **구두점(`S*`)은 무시합니다.** 이름 접미사 `이`(`XSN`)는 조사 자리에 오므로 조사로 칩니다. 어미와 조사가 함께 바뀌었거나, 띄어쓰기만 바뀌어서 다른 형태소가 없으면 `WORDS` 입니다. Kiwi가 없으면 항상 `WORDS` 입니다.
- 6-8: 달라진 뜻 있는 낱말을 기본형으로 돌려줍니다. 같은 구간에서 앞뒤를 차례로 짝짓고, 짝이 없으면 `null`(추가된 낱말은 `null → 활짝`). 기록 패널에서는 `[낱말]  문 → 창문, 열다 → 닫다` 로 보이고, 추가·삭제만 된 문장에는 표시가 없습니다.
- 표시는 문장 목록이 먼저 나오고 형태소 표시는 백그라운드에서 분석한 뒤 붙습니다(최대 400개 문장).

---

## 알려진 한계와 다음 후보

- **맞춤법은 목록 방식입니다**(약 70개). 목록에 없는 실수는 못 잡습니다. 규칙은 `spell/SpellChecker.kt` 에 한 줄씩 더하면 됩니다.
- **이중 피동은 피동사 목록 방식입니다.** 목록에 없는 피동사는 못 잡습니다(`PassiveVoiceRule` 의 `PASSIVE_STEMS`).
- **문장 앞 `@Suppress` 는 맞춤법만 그 문장 하나에 적용됩니다.** 긴 문장·습관어 같은 기존 규칙은 결과가 줄 단위라서 그 줄 전체에서 해당 검사가 꺼집니다.
- **낱말 반복은 "한 문단 = 빈 줄 사이"입니다.** 웹소설처럼 문장마다 빈 줄을 넣으면 문단이 문장 하나라서 거의 안 걸립니다(습관어 검사는 줄 단위).
- **Kiwi의 분석이 앞뒤에 따라 달라집니다**(위 "알아 둘 것"). 이름이 사전에 없을 때 특히 그렇습니다. 새 카드를 만든 직후에는 사전 재구성이 끝나야 정확합니다.
- **해시태그 한 줄은 함수만 있습니다.** 회차 복사 메뉴나 `Ctrl+Shift+C` 에 붙이는 것은 아직 안 했습니다.
- **`.md` → `.episode` 로 확장자를 바꿀 때 `# 제목` 줄이 본문으로 남습니다.** 변환할 때 제목 줄을 빼 주는 기능이 필요합니다.
- **앱을 다시 켜면 글꼴 크기가 기본(16pt)으로 돌아갑니다.**

---

## 공용 파일 (리드만 수정)

바꿔야 할 부분이 있으면 직접 고치지 말고, GitHub Issue에 "어떤 파일을 왜 바꿔야 하는지" 적어서 리드에게 요청해 주세요.

- `edited/App.kt`, `edited/ui/` 바로 아래 파일 전부(`ProjectWorkspaceWidget.kt`, `Widget.kt`, `RootPane.kt` 등)
- `edited/core/**`, `edited/lexer/**`, `edited/project/**`, `edited/theme/**`, `edited/render/**`, `edited/event/**`, `edited/spell/**`
- `edited/ui/docking/**`, `edited/ui/editor/**`, `edited/ui/layout/**`, `edited/ui/components/**`, `edited/ui/panes/PaneStyle.kt`, `edited/ui/panes/BackgroundWork.kt`
- 여러 작업이 함께 쓰는 도구: `core/Sentences.kt`(문장 나누기), `core/Directives.kt`(지시문), `core/Parallel.kt`(병렬 처리), `project/ChapterTitle.kt`(회차 제목), `ui/panes/PaneStyle.kt`(패널 그리기), `ui/panes/BackgroundWork.kt`(백그라운드 계산), `lexer/Morphology.kt`·`lexer/CommonWords.kt`(형태소 분석), `ui/components/PressFeedback.kt`(클릭 피드백) — 가져다 쓰기만 하세요
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
7. **원고 전체를 분석하는 계산은 화면 스레드에서 하지 않습니다.** `BackgroundWork` 와 `Parallel.map` 을 쓰세요(위 "병렬 처리와 백그라운드").
8. 새 기능에는 "걸리는 예문"과 "안 걸리는 예문" 테스트를 함께 넣어 주세요. 형태소 결과가 헷갈리면 먼저 `println(Morphology.analyze("문장"))` 으로 확인하세요.

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
저는 PLAN.md 의 작업 [3] 담당입니다. 먼저 PLAN.md 의 "공용 도구"와 작업 [3] 부분, 그리고 lexer/Morphology.kt 를 읽어 주세요.
"한눈에 보기" 표에 있는 작업 [3]의 경로 안의 파일만 만들거나 고쳐 주세요. lexer/, core/ 는 가져다 쓰기만 하세요.
그 밖의 파일을 고쳐야 할 것 같으면 고치지 말고 멈춘 뒤, 어떤 파일을 왜 바꿔야 하는지 알려 주세요.
"약속"에 적힌 클래스·함수 이름과 인자는 바꾸지 마세요.
품사가 헷갈리면 먼저 Morphology.analyze 결과를 println 으로 찍는 테스트를 만들어 확인해 주세요.
이번 할 일: [3-2. CharactersPane]
```

작업이 끝나면 PR을 올리기 전에 `git status` 로 바뀐 파일이 내 경로 안에만 있는지 꼭 확인해 주세요.
