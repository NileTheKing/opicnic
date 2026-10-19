# 채점 워커 폴링 쿼리 EXPLAIN 실험 (2026-10-16)

## 질문

`ScoringWorker.poll()`은 1초마다 `ScoringJobItemRepository.findClaimable`을 부른다. 이 쿼리가 다음 두 상황에서도 빠른지 확인한다.

1. 끝난 문항(DONE, FAILED)이 `scoring_job_item`에 500만 건 쌓였을 때
2. Gemini 장애로 QUEUED 문항 수천 건이 전부 백오프(`next_attempt_at`이 미래)에 걸렸을 때

이 결과로 한 테이블을 유지할지(A), 큐 테이블과 이력 테이블을 나눌지(C) 정한다.

대상 쿼리(Hibernate가 만드는 SQL과 같은 모양, `limit`은 워커가 넘기는 빈 슬롯 수이고 기본 동시 처리 60이라 60으로 측정):

```sql
select <전체 컬럼> from scoring_job_item s
where s.status = 'QUEUED' and (s.next_attempt_at is null or s.next_attempt_at <= now(6))
order by s.updated_at limit 60
```

## 환경

| 항목 | 값 |
|---|---|
| MySQL | 8.0.46, 로컬 docker 컨테이너 `opicnic_mysql_s1`(127.0.0.1:3307). 새 DB `opicnic_explain`만 사용, 앱 DB `opicnic`은 SHOW CREATE TABLE로 읽기만 함 |
| 머신 | Apple M2, 16GB, macOS |
| 설정 | 기본값. `innodb_buffer_pool_size` 128MB, `innodb_flush_log_at_trx_commit` 1 |
| 데이터 | `scoring_job` 333,334건, `scoring_job_item` 5,000,010건(DONE 4,850,010, FAILED 150,000, 3%), 잡당 15문항 |
| 시간 분포 | `updated_at`이 365일에 걸쳐 균등 |
| 행 크기 | 3분의 1은 stt_text 약 330자, 나머지는 약 40자. FAILED는 last_error 약 90자 |
| 크기 | item 데이터 1,578MB, 인덱스 합계 약 711MB(PK 1,578MB는 데이터 포함, uk 343MB, status 인덱스 153MB) |
| 로드 시간 | 잡 33만 건 + 문항 500만 건 INSERT...SELECT 합계 4분 2초(5개 청크, 청크당 약 50초), 보조 인덱스 2개 추가 34초 |
| 스키마 차이 | `scoring_job.member_id` FK는 member 테이블을 복사하지 않아 생략. 나머지는 앱 DDL과 같음 |

버퍼 풀이 128MB라서 500만 건 테이블 전체는 메모리에 들어가지 않는다. 쿼리가 건드리는 QUEUED 행과 인덱스 끝부분은 방금 쓴 데이터라 메모리에 있다.

## 시나리오

QUEUED 행만 갈아끼우고 끝난 500만 건은 그대로 둔다. QUEUED 행의 `updated_at`은 최근 1시간 안.

| 이름 | QUEUED | next_attempt_at | 의미 |
|---|---|---|---|
| S1 평소 | 10 | 전부 NULL | 정상 운영 |
| S2 장애 중 | 5,000 | 전부 미래(1~20분 뒤) | 결과 0건 |
| S3 회복 직후 | 5,000 | 2,500 과거, 2,500 미래 | 절반만 집을 수 있음 |
| S4 대형 장애 | 50,000 | 전부 미래 | S2의 10배 스트레스 |
| S1Q2 | 10 | 전부 과거 | Q2(OR 없는 쿼리)용 평소 |

인덱스 변형:

| 이름 | 인덱스 | 이유 |
|---|---|---|
| I0 | `(status, updated_at)` 현재 | 정렬 컬럼으로 `limit`을 일찍 끊는 구성 |
| I1 | `(status, next_attempt_at)` | 백오프 조건을 인덱스에서 걸러 낸다. `IS NULL`과 `<=`가 range 두 개로 처리됨 |
| I2 | `(status, next_attempt_at, updated_at)` | I1에 정렬 컬럼을 붙여 인덱스만으로 조건과 정렬 컬럼을 읽게 함 |
| AUTO | 세 인덱스를 모두 두고 힌트 없이 옵티마이저 선택 | 인덱스를 추가만 하고 I0을 지우지 않았을 때 |
| NONE | 세 인덱스 모두 `IGNORE INDEX` | 인덱스 없는 풀스캔 기준선 |

쿼리 변형: Q1은 현재 쿼리(OR 포함). Q2는 `next_attempt_at is null or`을 뺀 형태로, enqueue 때 `next_attempt_at`을 항상 채운다는 가정이다. 코드 변경이 필요하다.

측정: 변형마다 워밍업 3회(NONE은 1회) 뒤 20회(NONE은 5회)의 서버 내 경과 시간을 `SYSDATE(6)` 차이로 기록했다. 클라이언트 왕복과 결과 전송 시간이 약간 포함된다. p95는 nearest-rank(20회면 19번째 값, NONE은 5회라 최댓값). `Handler_read_*`는 별도 1회 실행의 세션 상태 델타.

## 결과 표

시간 단위 ms. 행 수는 `Handler_read_next`(인덱스를 따라 읽은 행 수, 한 번의 실행). `Handler_read_key`는 어디서나 1이다. `Handler_read_rnd_next` 329는 성능 스키마 조회가 만드는 고정 잡음이라 NONE 외에는 무시한다.

### Q1 (현재 쿼리)

| 시나리오 | 인덱스 | p50 | p95 | Handler_read_next | EXPLAIN type / key / rows |
|---|---|---|---|---|---|
| S1 | I0 | 0.117 | 0.217 | 10 | ref / status / 10 |
| S1 | I1 | 0.110 | 0.418 | 10 | range / status_next / 10 |
| S1 | I2 | 0.100 | 0.187 | 10 | range / status_next_upd / 10 |
| S1 | AUTO | 0.063 | 0.131 | 10 | ref / status |
| S1 | NONE | 2850.6 | 3188.4 | rnd_next 5,000,350 | ALL / - / 4,908,238 |
| S2 | I0 | 3.040 | 3.227 | 5,000 | ref / status / 5,000 |
| S2 | I1 | 0.059 | 0.087 | 0 | range / status_next / 1 |
| S2 | I2 | 0.047 | 0.075 | 0 | range / status_next_upd / 1 |
| S2 | AUTO | 0.081 | 0.106 | 0 | range / status_next |
| S2 | NONE | 2488.0 | 2594.3 | rnd_next 5,005,340 | ALL |
| S3 | I0 | 0.143 | 0.242 | 119 | ref / status / 5,000 |
| S3 | I1 | 2.065 | 3.274 | 2,500 | range / status_next / 2,500, filesort |
| S3 | I2 | 1.900 | 2.637 | 2,500 | range / status_next_upd / 2,500, filesort |
| S3 | AUTO | 1.888 | 2.423 | 2,500 | range / status_next (옵티마이저가 I1 선택) |
| S3 | NONE | 2534.3 | 3038.8 | rnd_next 5,005,340 | ALL |
| S4 | I0 | 28.833 | 35.207 | 50,000 | ref / status / 50,000 |
| S4 | I1 | 0.082 | 0.398 | 0 | range / status_next / 1 |
| S4 | I2 | 0.063 | 0.109 | 0 | range / status_next_upd / 1 |
| S4 | AUTO | 0.073 | 0.108 | 0 | range / status_next |
| S4 | NONE | 3657.5 | 3923.0 | rnd_next 5,050,340 | ALL |

### Q2 (OR 없음, 코드 변경 필요)

| 시나리오 | 인덱스 | p50 | p95 | Handler_read_next |
|---|---|---|---|---|
| S1Q2 | I0 | 0.127 | 0.252 | 10 |
| S1Q2 | I1 | 0.081 | 0.140 | 10 |
| S1Q2 | I2 | 0.069 | 0.104 | 10 |
| S2 | I0 | 2.830 | 2.981 | 5,000 |
| S2 | I1 | 0.073 | 0.126 | 0 |
| S2 | I2 | 0.045 | 0.069 | 0 |
| S3 | I0 | 0.191 | 0.217 | 119 |
| S3 | I1 | 1.983 | 2.356 | 2,500 |
| S3 | I2 | 1.981 | 2.579 | 2,500 |
| S4 | I0 | 32.677 | 43.282 | 50,000 |
| S4 | I1 | 0.104 | 0.402 | 0 |
| S4 | I2 | 0.073 | 0.309 | 0 |

(S4 Q2 I1의 max 100ms 한 번은 일회성 튐이고 p95에는 들어가지 않았다.)

### 콜드 캐시 근사 (`raw/cold.txt`, S2 데이터)

500만 건 풀스캔으로 버퍼 풀을 씻은 직후 첫 실행, 변형당 3회, 단위 ms.

| 인덱스 | 3회 |
|---|---|
| I0 | 3.70, 4.63, 3.88 |
| I1 | 0.76, 0.96, 0.35 |
| I2 | 0.39, 0.37, 0.34 |

## 실행 계획 해석

- **NONE**: 풀스캔 500만 행에 filesort. 2.5~3.9초. 인덱스 없이는 쓸 수 없다. 현재 인덱스가 의미 있다는 확인이다.
- **I0, S1**: `status='QUEUED'` 구간이 10건뿐이라 0.1ms. 끝난 500만 건은 `status` 값이 달라 인덱스의 다른 구간에 있고, 쿼리는 그 구간에 닿지 않는다. 테이블이 커져도 이 쿼리 비용은 변하지 않는다.
- **I0, S2**: `next_attempt_at`이 인덱스에 없어서 QUEUED 5,000건을 `updated_at` 순으로 전부 읽고 PK로 되돌아가 조건을 검사한다(Handler_read_next 5,000). 3ms. 결과는 0건이다. 비용은 QUEUED 수에 선형이다(5만 건이면 29ms). 1초 주기 폴링에서 3ms는 0.3% 점유라 문제 수준이 아니다.
- **I0, S3**: 집을 수 있는 행이 절반이라 60건을 채우는 데 119건만 읽고 끝난다. `updated_at` 순 인덱스가 `limit`을 일찍 끊는다. 0.14ms.
- **I1, I2, S2/S4**: `next_attempt_at <= now` 범위에 해당하는 행이 없으므로 인덱스를 열자마자 끝난다(rows 1, Handler_read_next 0). QUEUED가 5만 건이어도 0.06~0.08ms로 변하지 않는다.
- **I1, I2, S3**: 반대로 집을 수 있는 2,500건을 전부 읽고 filesort한 다음 60건을 자른다. 2ms. `ORDER BY updated_at`을 인덱스로 얻지 못해서 I0보다 10배 이상 느리다. 회복 직후 폴링마다 반복되는 비용이다.
- **AUTO**: 세 인덱스를 모두 두면 옵티마이저가 S3에서도 I1을 고른다(rows 추정 2,500 대 5,000이라 비용이 낮게 계산됨). 즉 인덱스를 추가만 하면 S3이 0.14ms에서 1.9ms로 나빠진다. 새 인덱스를 넣는다면 I0을 지우고 `USE INDEX`를 쓰는 선택 중 하나가 필요하다.
- I2는 I1보다 약간 빠르거나 비슷하다. 인덱스 크기는 I0 153MB, I1 86MB, I2 130MB.
- Q2(OR 제거)는 Q1과 거의 같은 수치다. 이 데이터에서 OR 때문에 생기는 차이는 없었다(I1/I2는 OR를 range 두 개로 처리한다). 코드 변경 가치는 없다.

## 추가: 분리 후 가정(큐 테이블에 대기 5,000건만)

테이블을 나누면 큐 테이블에는 대기 행만 남는다. 끝난 500만 행이 없는 테이블 `queue_only`에 대기 5,000건만 복사하고(인덱스 구성은 같음) 같은 쿼리를 쟀다(`11_queue_only.sh`, 원본 `raw/queue_only.txt`). warm 20회, p50 / p95, 단위 ms.

| 시나리오 | 테이블 | I0 현재 | I1 | I2 | 인덱스 없음 | AUTO |
|---|---|---|---|---|---|---|
| S2 장애 중 | 지금(500만 + 5천) | 3.04 / 3.23 | 0.06 / 0.09 | 0.05 / 0.08 | 2,488 / 2,594 | |
| S2 장애 중 | 분리 후(5천만) | 2.82 / 2.93 | 0.04 / 0.09 | 0.04 / 0.09 | 2.25 / 3.21 | 0.05 / 0.10 |
| S3 회복 직후 | 지금(500만 + 5천) | 0.14 / 0.24 | 2.07 / 3.27 | 1.90 / 2.64 | 2,534 / 3,039 | |
| S3 회복 직후 | 분리 후(5천만) | 0.20 / 0.86 | 2.35 / 2.67 | 2.26 / 2.41 | 2.28 / 2.87 | 2.32 / 2.73 |

- 인덱스가 있으면 분리 전후 수치가 같다. 대기 5,000건을 다루는 비용은 테이블에 끝난 행이 있든 없든 같다.
- 분리 후에는 인덱스 없이 통째로 읽어도 2.3ms다. 지금 테이블에서 인덱스가 하던 "끝난 행 건너뛰기"를 분리가 대신한다.
- 분리 후에도 I0의 "정렬된 채로 읽다가 60건에서 멈추기"는 남는다. S3에서 0.20ms 대 2.28ms(인덱스 없음).
- 장애 중(S2)에는 분리 후에도 I0과 인덱스 없음이 비슷하다(2.8ms 대 2.3ms). `next_attempt_at`이 인덱스에 없으면 대기 5,000건을 모두 확인해야 하는 건 분리와 무관하다.
- AUTO는 분리 후에도 S3에서 I1 계열을 골라 2.3ms가 된다. 옵티마이저의 선택 문제도 분리와 무관하다.

## 추가: 인덱스가 없을 때 정리 UPDATE의 잠금

워커의 `requeueStale`(처리 중에 멈춘 행을 대기로 되돌림)과 같은 UPDATE를 세션 A에서 autocommit으로 실행하고, 0.5초 뒤 세션 B에서 다른 쓰기를 했다. 3회씩, 단위 ms(`12_lock_wait.sh`, `13_lock_wait_old_row.sh`, 원본 `raw/lock_wait*.txt`).

| A의 UPDATE | 격리 수준 | A 소요 | B: 새 item INSERT | B: 오래된 행(id=1) UPDATE |
|---|---|---|---|---|
| 인덱스 사용 | REPEATABLE READ | 0~2 | 5~15 | 4~8 |
| 인덱스 무시(풀스캔) | REPEATABLE READ | 2,685~9,153 | 4~18 | 2,265~2,551 |
| 인덱스 무시(풀스캔) | READ COMMITTED | 3,527~3,861 | 3~5 | 4~6 |

- 풀스캔 UPDATE는 PK 순서로 읽으면서 잠금을 하나씩 건다. 0.5초 시점에는 스캔이 PK 앞쪽에 있어서, 자동 증가 id로 PK 끝에 붙는 새 INSERT(녹음 제출)는 막히지 않았다. 스캔이 끝에 닿은 뒤 바로 커밋되므로 INSERT가 막히는 구간은 매우 짧다. 이 구간은 따로 재지 않았다.
- 스캔이 이미 지나간 앞쪽 행을 고치는 쓰기는 A가 커밋할 때까지 기다렸다(약 2.3~2.6초, A의 남은 시간만큼). opicnic에서는 오래된 게스트 행을 지우는 게스트 정리가 여기에 해당한다.
- READ COMMITTED에서는 조건에 맞지 않는 행의 잠금을 확인 직후 풀어서 기다림이 없었다.
- 인덱스가 있으면 "PROCESSING이면서 오래된" 구간만 읽고 잠가서 어느 쪽도 기다리지 않았다.
- 예상과 달랐던 점: 처음에는 인덱스가 없으면 녹음 제출 INSERT가 막힐 것으로 봤다. 실제로 막힌 것은 스캔이 지나간 기존 행에 대한 쓰기였다.

## 추가: 인덱스가 없을 때 버퍼 풀과 다른 조회

폴링이 풀스캔을 하면 다른 화면의 조회가 버퍼 풀에서 밀려나는지 봤다(`14_victim_setup.sql`, `15_buffer_pool.sh`, 원본 `raw/buffer_pool.txt`).

- 다른 조회 역할: `feedback_result` 요약 조회를 흉내 낸 `fr` 테이블(2,000명 x 100개 = 20만 행, 약 78MB, 인덱스 `(member_id, created_at)`). 무작위 사용자의 최근 100개를 읽는 조회를 쉬지 않고 반복했다.
- 폴링: `ScoringWorker.poll()` 한 바퀴와 같은 쿼리 4개(`requeueStale` UPDATE, count, `findClaimable` 60, count) 후 1초 쉼. 대기 10건(S1).
- 조건별 3분. 버퍼 풀 128MB.

| 조건 | 폴링 바퀴 수 | 다른 조회 평균 | 조회 1건당 디스크 읽기 | 최악 배치 평균 | 버퍼 풀의 fr(전 → 후) |
|---|---|---|---|---|---|
| 폴링 없음 | | 0.141ms | 0 | 0.79ms | 75.3 → 75.3MB |
| 인덱스 쓰는 폴링 | 172 | 0.141ms | 0 | 0.71ms | 75.3 → 75.3MB |
| 인덱스 무시 폴링 | 16 | 0.185ms | 0.74 | 1.54ms | 75.3 → 75.3MB |
| 다시 인덱스 쓰는 폴링 | 172 | 0.140ms | 0 | 0.72ms | 75.3 → 75.3MB |

- 인덱스가 없으면 폴링 한 바퀴가 약 11초가 된다(3분에 16바퀴).
- 다른 조회는 평균 31% 느려졌고(0.141 → 0.185ms), 디스크 읽기가 0에서 조회당 0.74회로 생겼다. 배치별로 보면 폴링 한 바퀴 주기로 디스크 읽기가 올라갔다 0으로 돌아오기를 반복했다.
- fr 테이블은 끝까지 버퍼 풀에 거의 다 남았다. InnoDB 버퍼 풀은 새로 읽은 페이지를 LRU 중간(old 구역)에 넣고, 짧은 시간(`innodb_old_blocks_time`, 기본 1초) 안에 다시 쓰여야 앞쪽(young 구역)으로 올린다. 한 번 훑고 지나가는 풀스캔 페이지는 old 구역에서 먼저 버려져서, 자주 쓰는 페이지가 통째로 밀려나지 않는다.
- 인덱스를 다시 쓰자 바로 원래 수치로 돌아왔다.

이 실험의 한계:
- 다른 조회를 1초에 수천 번 돌려 fr 페이지가 늘 young 구역에 있었다. 실제 트래픽은 드문드문이라 1초 넘게 안 쓰인 페이지가 old 구역으로 내려가 풀스캔에 밀려날 수 있다. 실제 영향은 이보다 클 수 있다.
- `innodb_flush_method=fsync`라 버퍼 풀에서 밀려난 페이지를 다시 읽을 때 OS 파일 캐시(Docker VM 메모리)에서 가져왔을 가능성이 크다. 진짜 디스크라면 디스크 읽기 1회의 비용이 더 크다.
- 첫 실행은 사용자 조회에 `RAND()`를 WHERE에 직접 써서 행마다 다시 계산돼 인덱스를 못 타고 풀스캔했다. 비교에서 제외했다(`raw/buffer_pool_bug_rand_fullscan.txt`).

## 결론

- **A(한 테이블)로 충분한가**: 충분하다. 끝난 행 500만 건은 폴링 쿼리 비용에 영향을 주지 않는다(S1에서 0.1ms). 지금 서비스의 문항 수 규모에서는 테이블 분리(C)로 얻을 것이 없다. 분리의 실익은 이 쿼리가 아니라 저장 공간, 백업, 보존 정책 같은 다른 이유에서 따져야 한다.
- **인덱스 변경이 필요한가**: 필요 없다. 장애 중 최악 경우(S2)도 QUEUED 5,000건에서 약 3ms, 5만 건에서 약 30ms다. 폴링 주기 1초에 비해 작고, 이 구간에서는 어차피 서킷이 열려 폴링 쿼리를 부르지 않는다(`circuit.isOpen()`이면 `poll()`이 `findClaimable` 전에 반환하고 `countByStatus(QUEUED)`만 부른다. 이 count 쿼리는 이번에 재지 않았다). 반대로 `(status, next_attempt_at)` 계열로 바꾸면 S2는 좋아지지만 S3이 나빠진다(0.14ms에서 약 2ms). 이득과 손실이 모두 작아서 바꿀 이유가 없다. QUEUED가 수만 건 이상으로 오래 쌓이는 운영이 예상될 때만 다시 본다.
- **테이블 분리가 필요한가**: 이 쿼리 기준으로는 필요 없다. 풀스캔이 아닌 인덱스 접근이라 끝난 행 수와 무관하다.

## 한계

- 데이터가 합성이다. `updated_at`은 균등 분포이고 잡 간 시간 겹침이 없다. 실제 트래픽의 시간대 편중은 반영하지 않았다.
- 버퍼 풀 128MB이지만 QUEUED 행과 인덱스 끝부분은 방금 쓴 데이터라 메모리에 있다. 콜드 캐시 측정은 풀스캔으로 씻은 근사이며, QUEUED 행은 PK 끝에 있어 풀스캔 직후에도 일부 남는다. 진짜 콜드(재시작 후) 값은 재지 않았다. 컨테이너를 재시작하지 않기로 해서 생략했다.
- 측정에는 클라이언트 전달 시간이 소량 포함되고, 동시 부하(접수 INSERT, claim UPDATE, 워커 트랜잭션)는 없는 단일 세션 측정이다. 락 경합과 InnoDB 퍼지 지연은 보지 않았다.
- QUEUED 행의 `stt_text`가 비어 있어 행이 실제보다 작다. PK 되읽기 비용은 조금 과소 추정일 수 있다.
- limit 60은 빈 슬롯이 60일 때 값이다. 슬롯이 적으면 S3의 I0 비용은 더 작아진다.
- MySQL 8.0.46 단일 노드. 운영 서버는 측정하지 않았다.
- 실험 DB `opicnic_explain`은 로컬 `opicnic_mysql_s1`에 그대로 남겼다(데이터 약 2.4GB). 지우려면 `DROP DATABASE opicnic_explain;`.

## 재현

```sh
export M="docker exec -i opicnic_mysql_s1 sh -c 'MYSQL_PWD=\$MYSQL_ROOT_PASSWORD mysql -uroot'"   # 또는 래퍼 스크립트
$M < 01_setup.sql          # DB, 테이블, 보조 테이블, 잡 ID 스테이징
./04_load.sh               # 02_gen_jobs.sql, 03_gen_items_chunk.sql 5회, ANALYZE
$M < 05_indexes.sql        # I1, I2 추가
$M < 07_measure_proc.sql   # measure 프로시저, results 테이블
SCENARIOS="S1 S1Q2 S2 S3 S4" ./08_run_measure.sh
$M -t < 09_summary.sql     # 중앙값, p95, Handler 델타
./10_cold.sh               # 콜드 근사 (S2 데이터 상태에서)
```

파일: `01_setup.sql`, `02_gen_jobs.sql`, `03_gen_items_chunk.sql`, `04_load.sh`, `05_indexes.sql`, `06_scenario.sql`, `07_measure_proc.sql`, `08_run_measure.sh`, `09_summary.sql`, `10_cold.sh`. 원본 출력은 `raw/`(`explain_*.txt`는 시나리오별 EXPLAIN과 EXPLAIN ANALYZE 전문, `summary.txt`, `results_all_runs.tsv`, `cold.txt`, `load.log`, `scenario_*.txt`).
