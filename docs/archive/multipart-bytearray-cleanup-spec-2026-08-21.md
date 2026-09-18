# 멀티파트 답변 처리 InputStream → byte[] 정리 작업 명세

> 작성일: 2026-08-21
> 목적: 순수 타입 정리(리팩터링). 기능/성능 변화 없음. 구현 에이전트가 별도 해석 없이 진행할 수 있게 실행 명세로 남긴다.
> 원칙: 이 문서는 구현 인계서다. 작성 중 애플리케이션 코드는 수정하지 않았다.

## 1. 배경

`STTService.sendStreamToStt()`가 `InputStream`을 받는 이유는 원래(2026-04-27, `6c5c761`) 톰캣→STT API로 오디오를 **한 번도 메모리에 통째로 안 올리고 그대로 릴레이**하기 위해서였다(`InputStreamResource`, `contentLength()` -1로 청크 인코딩 유도). 그때 당시 진짜 병목은 톰캣의 멀티파트 임시파일 디스크 쓰기였고, `file-size-threshold: 2MB` 설정 + 이 릴레이 구조로 함께 해결됐다.

그런데 2026-06-06(`f66b4d1`, STT+LLM 재시도 기능 추가)에서 `FeedbackService.getComboFeedbackStreaming()`이 각 서브태스크 맨 앞에서 `byte[] audioBuffer = is.readAllBytes();`를 무조건 실행하도록 바뀌었다 — `InputStream`은 한 번 읽으면 끝이라, 재시도(최대 3회)마다 같은 데이터를 다시 읽으려면 어차피 통째로 버퍼링해둬야 하기 때문이다.

그 결과 지금은 `InputStream`이 컨트롤러→서비스→STT 3개 레이어를 타입으로 관통하지만, 실제로는 맨 처음(FeedbackService 진입 직후)에 무조건·즉시·전체가 `byte[]`로 소진돼서 "스트리밍"의 이점(지연 읽기, 부분 처리)을 하나도 못 쓰고 있다. `STTService.sendStreamToStt()`가 지금 받는 `InputStream`도 실제로는 항상 `new ByteArrayInputStream(audioBuffer)` — 이미 메모리에 있는 배열을 감싸기만 한 것이다.

**디스크 I/O 제거(`file-size-threshold: 2MB`)는 이 정리와 무관하게 그대로 유지된다 — 이번 작업은 그 최적화를 되돌리는 게 아니라, 이미 사실상 사라진 "스트리밍인 척하는" 타입 시그니처를 정직하게 `byte[]`로 바꾸는 것뿐이다.**

## 2. 범위

**포함**
- `PracticeAttemptApiController.processSubmission()`: `Part` → `byte[]` 변환을 이 레이어에서 바로 함
- `FeedbackService.getComboFeedbackStreaming()`: `List<InputStream>` 파라미터 → `List<byte[]>`
- `STTService.sendStreamToStt()`: `InputStream` 파라미터 → `byte[]`, 내부적으로 `ByteArrayResource` 사용

**제외 (이번 범위 아님, 손대지 않음)**
- 실제 청크 스트리밍 재도입 등 새로운 기능
- `application.yml`의 `file-size-threshold`/`max-file-size` 등 멀티파트 설정
- 재시도 횟수/백오프 로직
- `PracticeAttemptApiController`의 파일 크기(`MAX_ANSWER_FILE_BYTES`)/콘텐츠 타입 검증 로직(이미 `part.getSize()`/`getContentType()`으로 처리 중 — `Part`에서 직접 얻는 메타데이터라 `byte[]` 전환과 무관)
- REVIEW-05(멀티파트 파싱 실패 → 413/400) 처리 로직

## 3. 변경 지점

### 3.1 `PracticeAttemptApiController.java`

현재(약 200번째 줄 부근, `processSubmission()` 안):
```java
List<InputStream> streams = new ArrayList<>();
for (var part : fileParts) streams.add(part.getInputStream());
```
→
```java
List<byte[]> answerBytes = new ArrayList<>();
for (var part : fileParts) answerBytes.add(part.getInputStream().readAllBytes());
```

호출부:
```java
List<FeedbackDTO> submittedFeedbackResults = feedbackService.getComboFeedbackStreaming(streams, questions);
```
→
```java
List<FeedbackDTO> submittedFeedbackResults = feedbackService.getComboFeedbackStreaming(answerBytes, questions);
```

- `import java.io.InputStream;` 제거 대상인지 확인(다른 곳에서 안 쓰면 제거).
- `part.getInputStream().readAllBytes()`가 `IOException`을 던질 수 있는 지점은 기존 `processSubmission(...) throws IOException, ServletException` 시그니처 안에 이미 있으므로 메서드 시그니처 변경은 불필요. 단, 이 지점이 REVIEW-05의 `request.getParts()` try/catch 블록(멀티파트 파싱 자체 실패 처리) **밖에** 있다는 걸 확인할 것 — 이 리팩터링으로 그 catch 블록 범위를 넓히거나 좁히면 안 됨. `readAllBytes()`는 이미 파싱된 `Part`의 스트림을 읽는 것이라 REVIEW-05가 다루는 "파싱 자체 실패"와는 다른 단계다.

### 3.2 `FeedbackService.java`

```java
public List<FeedbackDTO> getComboFeedbackStreaming(
        List<InputStream> inputStreams, List<QuestionDto> questions) {
    ...
    final InputStream is = inputStreams.get(i);
    ...
    byte[] audioBuffer = is.readAllBytes();
    ...
    sttService.sendStreamToStt(new ByteArrayInputStream(audioBuffer), "audio_" + idx + ".webm");
```

→
```java
public List<FeedbackDTO> getComboFeedbackStreaming(
        List<byte[]> audioBuffers, List<QuestionDto> questions) {
    ...
    final byte[] audioBuffer = audioBuffers.get(i);
    ...
    // is.readAllBytes() 줄 삭제 — 이미 byte[]로 받음
    ...
    sttService.sendStreamToStt(audioBuffer, "audio_" + idx + ".webm");
```

- 파라미터명이 바뀌므로 크기 불일치 검증 메시지("음성 파일 수(...) 와 질문 수(...) 가 일치하지 않습니다")도 `inputStreams.size()` → `audioBuffers.size()`로 맞춰 고칠 것.
- `import java.io.ByteArrayInputStream;`, `import java.io.InputStream;` 제거.

### 3.3 `STTService.java`

```java
public String sendStreamToStt(InputStream inputStream, String filename) {
    ...
    InputStreamResource resource = new InputStreamResource(inputStream) {
        @Override public String getFilename() { return filename; }
        @Override public long contentLength() { return -1; }
    };
    ...
}
```

→
```java
public String sendStreamToStt(byte[] audioBytes, String filename) {
    ...
    ByteArrayResource resource = new ByteArrayResource(audioBytes) {
        @Override public String getFilename() { return filename; }
    };
    ...
}
```

- `ByteArrayResource`는 `contentLength()`를 배열 길이로 이미 정확히 구현해서 제공하므로 `-1` 오버라이드가 필요 없다(오히려 정확한 길이를 알려줄 수 있어 청크 인코딩을 강제로 유도하지 않아도 됨 — 이건 의도된 차이이지 실수가 아니다).
- import: `org.springframework.core.io.InputStreamResource` → `org.springframework.core.io.ByteArrayResource`, `java.io.InputStream` 제거(다른 용도로 안 쓰면).
- `enabled=false`(mock) 분기는 그대로 유지 — 그 분기는 파라미터를 안 건드림.

## 4. 회귀 테스트 갱신

아래 테스트들이 `List<InputStream> streams = List.of(new ByteArrayInputStream(bytes))` 형태로 `getComboFeedbackStreaming()`을 직접 호출한다. 시그니처가 바뀌므로 `List<byte[]> streams = List.of(bytes)`로 고쳐야 컴파일된다.

- `src/test/java/com/opicnic/opicnic/service/FeedbackServiceScoreValidationTest.java`
- `src/test/java/com/opicnic/opicnic/service/FeedbackServiceRoleplayMainPointTest.java`
- `src/test/java/com/opicnic/opicnic/service/FeedbackServiceSelfIntroTest.java`

`STTService`를 직접 단위 테스트하는 파일이 있다면 `sendStreamToStt(byte[], String)`으로 호출부를 맞출 것 — 없으면 이 항목은 해당 없음(모두 mock으로 대체돼 있을 가능성이 높음, 먼저 grep으로 확인).

이번 작업은 **순수 리팩터링**이라 새 테스트를 추가할 필요는 없다. 위 기존 테스트들이 시그니처만 고친 채로 **전부 그대로 통과**하는 것 자체가 "동작 변화 없음"의 증명이다.

## 5. 완료 조건

- `PracticeAttemptApiController`, `FeedbackService`, `STTService`에서 `InputStream`/`ByteArrayInputStream`/`InputStreamResource` import가 전부 사라짐. (`Part.getInputStream()` 호출 자체는 `Part` 인터페이스에 `getBytes()`가 없어서 어쩔 수 없이 남는다 — 그 반환값을 받자마자 바로 `.readAllBytes()`로 소진하고 변수에 담지 않는 형태여야 한다.)
- `./gradlew compileJava compileTestJava` 통과.
- `./gradlew test` 실행 시 기존에 이미 알려진 로컬 DB 의존 4개 클래스(`OpicnicApplicationTests`, `QuestionSetCacheBenchmarkTest`, `FullPipelineEndToEndTest`, `ManualSeedRunner`) 외에 새로운 실패가 없어야 한다.
- git diff를 봤을 때, byte[]로의 변환이 **한 곳에서만** 일어나야 한다(컨트롤러에서 1번). 리팩터링 실수로 컨트롤러+서비스 양쪽에서 각각 복사가 일어나게 만들면(예: 서비스 쪽 `readAllBytes()` 삭제를 깜빡함) 오히려 복사 횟수가 늘어나는 회귀이므로, 코드를 직접 읽고 삭제된 줄이 맞는지 확인할 것.
- 실제 동작(재시도 로직, 결과, 성능 특성)은 변화가 없어야 한다 — 이건 기능 추가/버그 수정이 아니라 이미 사실상 사라진 스트리밍 시그니처를 정리하는 것뿐이다.

## 6. 검증 방법

```bash
./gradlew compileJava compileTestJava -q
./gradlew test -q
```

테스트 실행 전 `.env`를 셸에 로드해야 로컬 MySQL 관련 클래스가 "DB_USERNAME 리터럴" 에러가 아니라 정상적으로 시도라도 되는 상태가 된다(`export $(grep -v '^#' .env | xargs)`) — 다만 Docker/MySQL이 안 떠있으면 위 4개 클래스는 실패하는 게 정상이며 이번 작업과 무관하다.

커밋은 하지 말 것 — 작업 완료 후 결과만 보고한다.
