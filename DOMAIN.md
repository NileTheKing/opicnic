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
