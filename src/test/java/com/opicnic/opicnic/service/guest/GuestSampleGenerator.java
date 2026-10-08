package com.opicnic.opicnic.service.guest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.opicnic.opicnic.domain.CoachingReport;
import com.opicnic.opicnic.domain.FeedbackResult;
import com.opicnic.opicnic.domain.Member;
import com.opicnic.opicnic.domain.enums.Role;
import com.opicnic.opicnic.domain.enums.PracticeMode;
import com.opicnic.opicnic.domain.job.ScoringJob;
import com.opicnic.opicnic.dto.FeedbackDTO;
import com.opicnic.opicnic.repository.FeedbackResultRepository;
import com.opicnic.opicnic.repository.FeedbackTagRepository;
import com.opicnic.opicnic.repository.MemberRepository;
import com.opicnic.opicnic.service.CoachingService;
import com.opicnic.opicnic.service.FeedbackService;
import com.opicnic.opicnic.service.attempt.FeedbackPersistenceService;
import com.opicnic.opicnic.service.attempt.PracticeAttemptService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// classpath:guest/sample.json을 만드는 1회성 도구. 대본 답변을 실제 채점 파이프라인(LLM 채점 + 태그 + 코칭 리포트)에
// 그대로 통과시켜, 게스트가 보는 예시가 진짜 서비스 출력과 같게 한다. 외부 LLM을 부르므로 평소 테스트에서는 꺼져 있다.
// 실행: GENERATE_GUEST_SAMPLE=true LLM_PROVIDER=gemini + DB 접속 환경변수와 함께
//   ./gradlew test --tests '*GuestSampleGenerator*'
// 이야기: 3주 동안 콤보 4번, IM1 → IM2 → IM3 → IH로 올라가는 학습자. 질문 id는 운영과 같은 question 테이블 기준.
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "GENERATE_GUEST_SAMPLE", matches = "true")
class GuestSampleGenerator {

    @Autowired private MemberRepository memberRepository;
    @Autowired private PracticeAttemptService attemptService;
    @Autowired private FeedbackService feedbackService;
    @Autowired private FeedbackPersistenceService persistence;
    @Autowired private FeedbackResultRepository feedbackResultRepository;
    @Autowired private FeedbackTagRepository feedbackTagRepository;
    @Autowired private CoachingService coachingService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TransactionTemplate tx;

    private record Answer(long questionId, String text) {}

    private record Combo(int daysAgo, String patternKey, String category, List<Answer> answers) {}

    private static List<Combo> script() {
        return List.of(
            new Combo(20, "TYPE_1,TYPE_2,TYPE_3", "C1", List.of(   // 공원 — IM1: 짧은 단문, 현재 시제 위주
                new Answer(51, "There is a park near my house. It is big. There are many trees and a small lake. There is a bench and a playground. I like this park. I go there on weekend. It is quiet and the air is good. So I like it."),
                new Answer(52, "I usually go to the park on Saturday. I walk with my dog. I walk about one hour. Sometimes I sit on the bench and listen to music. I go alone or with my sister. After walk, I buy coffee. It is my routine."),
                new Answer(53, "I started going to the park when I was university student. I was stressed. My friend said walking is good. So I started. Before, I go one time a week. Now I go two or three times. Because I have a dog now. It is good for health."))),
            new Combo(13, "TYPE_1,TYPE_3,TYPE_4", "C2", List.of(   // 영화 — IM2: 단문을 이어 충실히, because/so 연결
                new Answer(31, "I like action movies and comedy movies. Action movies are exciting because there are many fight scenes and car chases. When I watch them, I feel my stress is going away. Comedy movies are also good because I can laugh a lot. I don't like horror movies because they are too scary for me and I can't sleep after watching. So usually I choose action or comedy when I go to the theater with my friends."),
                new Answer(33, "I became interested in movies when I was in middle school. My father liked movies, so we watched movies together every Friday night. At that time I liked animation movies like Disney movies. But when I became a high school student, I started to like action movies, like Marvel movies. Now I am an adult, and I also watch drama movies sometimes. My taste changed a lot, but I still love watching movies."),
                new Answer(34, "The most memorable movie is Avengers Endgame. I watched it in 2019 with my best friend. We went to the theater on the first day, so there were many people. The movie was about the heroes fighting Thanos to save the world. The ending was very sad because Iron Man died. I cried a lot and my friend also cried. It was memorable because I watched Marvel movies for ten years and it was the final."))),
            new Combo(6, "TYPE_1,TYPE_2,TYPE_3", "C1", List.of(    // 카페 — IM3: 길고 구체적, 복문 가끔, 흐름은 문장 단위
                new Answer(91, "My favorite coffee shop is a small cafe called Daily Beans, which is about five minutes from my apartment. When you walk in, you can smell the coffee beans right away, and the interior is really cozy with wooden tables and warm lights. There is a big window, so I can see people walking outside. What I like most is that it is quiet, so I can focus when I study or read a book. Also the owner remembers my order, which makes me feel like a regular. The latte there is not too sweet and really smooth. I think it is the best cafe in my neighborhood."),
                new Answer(92, "I go to coffee shops almost every day, usually after work around seven. Most of the time I go to the cafe near my apartment, because it is close and the seats are comfortable. I usually order an iced americano and then I study English or check my emails on my laptop. I stay there for about two hours. On weekends, I go to bigger cafes with my friends and we talk about our week and eat some desserts like cheesecake. Going to cafe is kind of my way to relax after a busy day, so I really look forward to it."),
                new Answer(93, "I first became interested in coffee shops when I was a university student. At first I went there just to study, because the library was too quiet and I felt sleepy. But I gradually started to enjoy the atmosphere and the coffee itself. Back then I only drank sweet drinks like caramel macchiato. After I started working, I needed more caffeine, so I switched to americano. These days I also like to visit new cafes on weekends, because there are many unique cafes in Seoul. So my habit changed from studying in cafes to exploring them as a hobby."))),
            new Combo(1, "TYPE_1,TYPE_3,TYPE_4", "C2", List.of(    // 국내여행 — IH: 문단 수준, 시제 운용, 가끔 단순해짐
                new Answer(211, "Honestly, my favorite place to travel in Korea is Gangneung, and I try to go there at least twice a year because it's the place where I can really recharge. It's on the east coast, so the first thing you notice is the ocean. The water is so clear and blue that it almost looks like a postcard. Along the beach there are lots of coffee shops, and Anmok Beach is actually famous for its coffee street, so I usually grab a latte and just walk along the sand for hours. Another thing I love is the food. You can eat fresh raw fish and a local tofu dish called sundubu, which is soft and really comforting. Whenever I feel burned out from work, I just take a train from Seoul, and in less than two hours I'm sitting by the sea. That's why Gangneung is my go-to place."),
                new Answer(213, "When I was about ten years old, my family went on a trip to Gyeongju, which is a historic city in the southeast of Korea. I still remember it pretty clearly because it was the first time we stayed at a hotel. We visited Bulguksa Temple and Seokguram, and my dad kept explaining the history, although honestly I was more interested in buying snacks back then. What I remember most is riding bikes around the old tombs in the evening. The weather was perfect and the sky turned orange, and my little brother and I raced each other while my parents laughed behind us. At that time I didn't really appreciate the culture, but looking back, it was a really special trip, and I think that's why I still love traveling around Korea today."),
                new Answer(214, "The most memorable trip I've ever had was a trip to Jeju Island with my two college friends last spring. We had been planning it for months, so we were really excited. On the second day, we decided to hike Hallasan, which is the highest mountain in Korea. Halfway up, it suddenly started raining heavily and we almost gave up, but we kept going because we didn't want to regret it. When we finally reached the top, the clouds cleared for just a few minutes and we could see the crater lake. We were soaked and exhausted, but we were screaming and hugging each other. That moment taught me that the best memories often come from the hardest moments. Even now, whenever we meet, we still talk about that day, and it always makes me smile.")))
        );
    }

    @Test
    void generate() throws Exception {
        Thread.sleep(60_000);   // 직전 호출이 남긴 분당 한도가 비워질 때까지
        Member member = memberRepository.save(Member.builder()
                .provider("sample-gen").providerId(UUID.randomUUID().toString()).nickname("예시").role(Role.USER).build());
        LocalDateTime now = LocalDateTime.now().withSecond(0).withNano(0);

        List<GuestSample.Result> results = new ArrayList<>();
        for (Combo combo : script()) {
            ScoringJob job = new ScoringJob(UUID.randomUUID().toString(), member, PracticeMode.COMBO,
                    combo.patternKey(), combo.category());
            LocalDateTime at = now.minusDays(combo.daysAgo()).withHour(21).withMinute(0);
            for (Answer a : combo.answers()) {
                FeedbackDTO fb = feedbackService.gradeWithSpeech(a.text(), attemptService.questionById(a.questionId()));
                if (fb.isFailed()) throw new IllegalStateException("채점 실패 q=" + a.questionId() + ": " + fb.getErrorMessage());
                FeedbackResult saved = persistence.saveOne(fb, job);
                at = at.plusMinutes(2);
                LocalDateTime when = at;
                tx.executeWithoutResult(st -> feedbackResultRepository.updateCreatedAt(saved.getId(), when));
                System.out.printf("[sample] q=%d %s%n", a.questionId(), saved.getOverallGrade());
                List<GuestSample.Tag> tags = feedbackTagRepository.findByFeedbackResultIdIn(List.of(saved.getId())).stream()
                        .map(t -> new GuestSample.Tag(t.getCategory(), t.getTag())).toList();
                FeedbackResult reread = feedbackResultRepository.findById(saved.getId()).orElseThrow();
                results.add(GuestSample.Result.of(reread, tags));
                Thread.sleep(9_000);   // 문항당 LLM 2번(채점+태그) — Gemini 무료 RPM 15 아래로
            }
        }

        Thread.sleep(30_000);   // 코칭 리포트도 여러 번 부른다 — 분당 한도를 비우고 시작
        CoachingReport report = coachingService.generate(member);
        GuestSample sample = new GuestSample(results, new GuestSample.Report(report.getContent(), report.getBasedOnCount()));

        Path out = Path.of("src/main/resources", GuestSampleSource.PATH);
        Files.createDirectories(out.getParent());
        objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT).disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .writeValue(out.toFile(), sample);
        System.out.println("[sample] wrote " + out.toAbsolutePath() + " results=" + results.size());
    }
}
