# DOMAIN.md

OPIc 시험 자체의 규칙. 프로젝트 구현이 어떻게 바뀌어도 이 문서는 거의 안 바뀐다 — 코드 리팩터링과 무관한 "시험 도메인 법칙"만 담는다. 구현 디테일(어떤 클래스가 이걸 담당하는지, 태그 스키마가 어떻게 생겼는지)은 여기 두지 않고 코드 주석에 둔다. `AGENTS.md`와 함께 매 세션 읽을 것.

## OPIc 문제 유형 (TYPE_1~TYPE_10)

| Code | 유형 | 핵심 스킬 |
|---|---|---|
| TYPE_1 | 현재 상태 묘사 | 현재시제, 장소/물건/사람 묘사 |
| TYPE_2 | 루틴/습관 | 현재시제, 빈도 부사, 일상 서술 |
| TYPE_3 | 최근/최초 경험 | 과거시제, 시간순 서술 (최근 경험 + 처음 해본 경험 둘 다 포함) |
| TYPE_4 | 기억에 남는 경험 | 과거시제, 감정 표현, 이유 설명 |
| TYPE_5 | 롤플레이 · 도입 | 상황 설정, 정중한 요청 (인터뷰어에게 질문하기) |
| TYPE_6 | 롤플레이 · 전화/질문 | 3~4개 질문 구성 (정보요청 롤플레이) |
| TYPE_7 | 롤플레이 · 문제 해결 | 대안 2~3개 제시 |
| TYPE_8 | 롤플레이 · 비슷한 경험 | 과거시제, 롤플레이와 연결 |
| TYPE_9 | 과거·현재 비교 | 시제 전환, 비교 표현 |
| TYPE_10 | 사회 이슈 | 논리적 전개, 의견 표현 |

메인포인트(MP) 채점 기준은 `What + Feeling + Why` — 뭘 했는지, 어떻게 느꼈는지, 왜 그런지 세 요소가 답변에 다 있는지를 본다.

## ComboPattern 카테고리 판별 (C1~C5)

콤보에 포함된 `questionTypes` 조합으로 판별한다. **판별 우선순위가 있다** — TYPE_6/7 포함 여부를 TYPE_4보다 먼저 체크해야 한다. 콤보 III가 `TYPE_6,7,4`와 `TYPE_6,7,8` 두 종류로 존재해서, TYPE_4 포함 여부만으로는 C2와 구분이 안 된다.

```java
public String category() {
    if (questionTypes.contains(TYPE_6) || questionTypes.contains(TYPE_7)) return "C3"; // 우선
    if (questionTypes.contains(TYPE_9) || questionTypes.contains(TYPE_10)) return "C5";
    if (questionTypes.contains(TYPE_5)) return "C4";
    if (questionTypes.contains(TYPE_4)) return "C2";
    return "C1";
}
```

## 배경설문 제한 정책

OPIcnic은 고득점 전략 기준으로 배경설문 선택지를 의도적으로 제한한다. 빠진 항목은 미구현이 아니라 제외 결정된 것.

- **거주형태**: `WITH_FAMILY` / `ALONE` 2개만. 세트 수에 영향 있어서 노출.
- **직업**: UI 없음. 직업 관련 주제(직장·출장 등)는 고득점 불리 → 의도적 제외.
- **주제**: 22개 중 고득점 추천 주제만 노출.

## 모의고사 구성 규칙

- `QuestionSet`은 주제별 문제 은행. 각 `QuestionSet`은 `TYPE_1`~`TYPE_10` 문제를 갖는다.
- `ComboPattern`은 런타임에 조립되는 시험 패턴이지, DB에 영속되는 개념이 아니다.
- `MockExamService`는 15문항 모의고사를 만든다: 자기소개 1 + 콤보 슬롯 5.
- 5개 콤보 슬롯 중 3개는 선택 주제 콤보, 2개는 돌발 주제 콤보.
- 돌발 콤보 슬롯 위치는 5개 슬롯 중에서 랜덤화된다.
- 돌발 주제는 `TopicCatalog.surpriseTopics()`에 정의된 전용 풀(23개, 5그룹)을 쓴다. 22개 배경설문 주제와는 완전히 별개이며, DB에 별도 `QuestionSet`을 갖는다(DataInitializer V1/V2/V3).

## 실제 OPIc 등급 기준 (조사, 2026-10-08)

채점 프롬프트(`prompts/scoring.md`)의 등급 설명은 이 절을 따른다. ACTFL 원문 기술은 실제 OPIc 체감보다 한 단계쯤 빡빡하다 — AL은 원어민 수준이 아니다(AL 취득자 확인).

**말의 단위가 1차 기준** — 채점표 "speaker produces mainly" 항목(강지완 오픽TV, 세부 진단서 해설):

| 단위 | 등급 |
|---|---|
| 단어 | NH 이하 |
| 문장 하나하나 | IM1 |
| 쉬운 문장의 연속(strings of sentences) | IM2 |
| 접속사·관계사로 이은 문장(connected sentences: which, when, where, if…) | IM3 |
| 뼈대 있는 문단(skeletal paragraph) | IH, AL |

- **문단** = 한 주제로 5~6문장 이상을 이어 말하되, "뭐가 있고 뭐가 있고"식 나열이 아니라 그 주제를 강화하는 이유·예시를 붙인 것. 서론·본론·결론(커뮤니티 경험칙: 서론 2·본론 6·결론 2, 최소 10문장).
- **IM3는 드물다**(취득자 약 4%). 채점자가 "문장 단위"로 판단하면 상한이 IM3이고, 오류가 있으면 IM2·IM1로 내려간다 — IM과 IH 사이에 담이 있는 구조. 문단으로 말하면 IH 이상, 아니면 IM2 이하로 갈리는 경우가 대부분.
- **IM2의 전형**(오픽노잼): 말은 많이 하는데 핵심(MP)이 없다. IM1은 말을 너무 적게 한다. MP는 What·Feeling·Why 중 **Why**가 가장 중요하다.

**IH와 AL을 가르는 것** — 둘 다 문단 수준이고, 차이는 오류와 폭:
- 과거 시제 통제. 과거 경험 문항에서 시제 실수가 나오면 상한 IH.
- 어휘 폭(breadth of vocabulary). 같은 표현 반복(delicious, delicious…)이면 상한 IH. 바꿔 말하기(appetizing, mouth-watering…)가 AL 쪽.
- 감점 항목: 수 일치(one of the most famous singer**s**, 3인칭 -s), 단조로운 문장 구조, 2~3초 이상 끊김, 발음·강세·억양(STT 텍스트로는 못 본다).
- 사회·전문 주제(돌발, 14·15번 비교·이슈)에서 의견을 제시하고 설명할 수 있는가.
- 단어 선택의 사소한 오류는 AL에서도 크게 문제 삼지 않는다.

**답변 하나로는 볼 수 없는 것** — 실제 등급은 채점자(2명)가 15문항 전체를 듣고 매긴다:
- 롤플레이 12번(예상 못한 문제 상황 해결)이 IM2와 IH를 가르고, 같은 등급대 안의 최종 등급도 롤플레이로 정해진다.
- 14·15번(비교·이슈, 난이도 5~6)이 IH와 AL을 가른다.
- 외운 스크립트처럼 들리면(지나치게 매끄럽고 빠른 시작) 감점.
- 우리 채점은 답변 하나 단위라 이 부분은 근사일 뿐이다.

출처(영상 자막을 읽고 요약): 강지완 오픽TV — "IM2와 IH를 가르는 채점 기준 3가지"(youtu.be/BLOLtPcw33s), "채점자가 IM3를 적게 주는 이유"(youtu.be/yxjmfJTAzjU), "숨겨진 채점기준 IL vs IM, IH vs AL"(youtu.be/zwltCNJmk6E), "IH에서 AL로"(youtu.be/zmamhX5s3Fg), "IH vs AL 실제 답변"(youtu.be/Se4X7UWUpLU) / 오픽노잼 — "왜 계속 IM1?"(youtu.be/oLivwcfQ9V4), "IM2 받았다고 기뻐하지 마세요"(youtu.be/EOPpAJzePgo) / 진짜녀석들 오픽 — 14·15번(youtu.be/id9hIxXEiss) / 오픽 마이너 갤러리 IM2→IH 후기(gall.dcinside.com/mgallery/board/view/?id=opic&no=44525).
