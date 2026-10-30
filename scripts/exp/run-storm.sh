#!/usr/bin/env bash
# 재시도 폭풍 실험(2026-10-30). 시즌 피크(초당 46문항, 제출 13.2건/s), 슬롯 500, 메모리 저장소, 서킷 켬(429는 안 셈).
# 내 한도 = OpenAI Grow 등급표: STT(gpt-4o-mini-transcribe) 분당 10,000회 / 800만 토큰, LLM(gpt-4o-mini) 분당 30,000회 / 1억 5천만 토큰.
# 거절도 요청 수 한도를 깎는다(OpenAI 문서), 토큰은 안 깎는다(문서 없음). 호출당 토큰은 CallTokens. STT 응답 1.5초(실측 80초 녹음 1.3~1.8초).
# 예열 60초 -> 전면 장애(503) 300초 -> 복구 후 300초 관찰(적체 0이면 먼저 끝). 설정 R1~R5(exp-run.sh 사다리).
# 사용: run-storm.sh [ONLY="R1 R4" 처럼 일부만]. VM ~/opicnic-exp 에서 별도 체인 파일로 실행(실행 중 스크립트 수정 금지).
cd "$(dirname "$0")/../.."
P=$HOME/opicnic-exp-results/progress.log
export STORAGE=mem RATE=13.2 WARM_S=60 OUTAGE_S=300 MAX_RECOVER_S=300 STT_MOCK_DELAY_MS=1500
export STT_PM_OVERRIDE=10000 LLM_PM_OVERRIDE=30000 STT_TPM=8000000 LLM_TPM=150000000
export COUNT_REJECTED=true COUNT_RATE_LIMITED=false
for l in R1 R2 R3 R4 R5; do
  [ -n "$ONLY" ] && case " $ONLY " in *" $l "*) ;; *) continue ;; esac
  id="storm-slots500-$l"
  echo "$(date '+%F %T') START $id" >> "$P"
  scripts/exp/exp-run.sh "$id" 2 500 on "$l" >> "$HOME/opicnic-exp-results/master.log" 2>&1
  rc=$?
  echo "$(date '+%F %T') END $id rc=$rc" >> "$P"
  python3 scripts/exp/exp-storm.py "$HOME/opicnic-exp-results/$id" >> "$HOME/opicnic-exp-results/storm-summary.jsonl" 2>&1
done
echo "$(date '+%F %T') STORM DONE" >> "$P"
