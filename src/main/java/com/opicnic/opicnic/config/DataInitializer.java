package com.opicnic.opicnic.config;

import com.opicnic.opicnic.domain.*;
import com.opicnic.opicnic.domain.enums.QuestionType;
import com.opicnic.opicnic.domain.enums.SurveyDifficulty;
import com.opicnic.opicnic.domain.enums.SurveyTopic;
import com.opicnic.opicnic.repository.QuestionSetRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private final QuestionSetRepository questionSetRepository;

    public DataInitializer(QuestionSetRepository questionSetRepository) {
        this.questionSetRepository = questionSetRepository;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (questionSetRepository.count() == 0) {
            populateData();
        }
    }

    // 콤보 패턴:
    // 패턴A [1,2,3]: 묘사 → 루틴 → 최근경험
    // 패턴B [1,3,4]: 묘사 → 최근경험 → 인상경험

    private Combo comboA(String name, QuestionSet set, int order,
                         String q1, String q2, String q3) {
        Combo combo = new Combo(name, set, order);
        combo.getQuestions().addAll(List.of(
                new Question(q1, 1, QuestionType.TYPE_1, combo),
                new Question(q2, 2, QuestionType.TYPE_2, combo),
                new Question(q3, 3, QuestionType.TYPE_3, combo)
        ));
        return combo;
    }

    private Combo comboB(String name, QuestionSet set, int order,
                         String q1, String q2, String q3) {
        Combo combo = new Combo(name, set, order);
        combo.getQuestions().addAll(List.of(
                new Question(q1, 1, QuestionType.TYPE_1, combo),
                new Question(q2, 2, QuestionType.TYPE_3, combo),
                new Question(q3, 3, QuestionType.TYPE_4, combo)
        ));
        return combo;
    }

    private void populateData() {

        // ── 영화 보기 (MOVIE_WATCHING) ──────────────────────────────────────
        QuestionSet movieL3 = new QuestionSet("Movie - Level 3", SurveyDifficulty.LEVEL_3, SurveyTopic.MOVIE_WATCHING);
        movieL3.getCombos().addAll(List.of(
                comboA("영화 패턴A-1", movieL3, 1,
                        "Describe the type of movies you enjoy watching. What genres do you prefer and why?",
                        "How often do you watch movies and what is your usual routine when you watch one?",
                        "Tell me about a movie you watched recently. What was it about and did you enjoy it?"),
                comboB("영화 패턴B-1", movieL3, 2,
                        "Describe your favorite movie theater or the place where you usually watch movies.",
                        "Tell me about the first time you watched a movie that really impressed you.",
                        "Tell me about the most memorable movie experience you have ever had. What made it so special?"),
                comboA("영화 패턴A-2", movieL3, 3,
                        "What kind of movies are popular in your country right now? Describe them.",
                        "Describe what you usually do before and after watching a movie.",
                        "Have you seen any movies recently that you would recommend? Tell me about it.")
        ));
        questionSetRepository.save(movieL3);

        // ── 음악 감상 (MUSIC_LISTENING) ─────────────────────────────────────
        QuestionSet musicL3 = new QuestionSet("Music - Level 3", SurveyDifficulty.LEVEL_3, SurveyTopic.MUSIC_LISTENING);
        musicL3.getCombos().addAll(List.of(
                comboA("음악 패턴A-1", musicL3, 1,
                        "Describe the type of music you enjoy listening to. What genres do you prefer?",
                        "How do you usually listen to music? Describe your typical music-listening routine.",
                        "Tell me about a song or album you have been listening to lately."),
                comboB("음악 패턴B-1", musicL3, 2,
                        "Describe your favorite singer or band. What do they look like and what kind of music do they make?",
                        "Tell me about the first concert or live performance you attended.",
                        "What is the most memorable music experience you have ever had? Tell me in detail.")
        ));
        questionSetRepository.save(musicL3);

        // ── 공원 가기 (PARK_GOING) ──────────────────────────────────────────
        QuestionSet parkL2 = new QuestionSet("Park - Level 2", SurveyDifficulty.LEVEL_2, SurveyTopic.PARK_GOING);
        parkL2.getCombos().addAll(List.of(
                comboA("공원 패턴A-1", parkL2, 1,
                        "Describe a park you often visit. What does it look like?",
                        "What do you usually do when you go to the park? Describe your typical visit.",
                        "Tell me about a recent trip to the park. What did you do there?"),
                comboB("공원 패턴B-1", parkL2, 2,
                        "Describe your favorite park. Where is it and what makes it special?",
                        "Tell me about the first time you visited a park that you really liked.",
                        "What is the most memorable experience you have had at a park?")
        ));
        questionSetRepository.save(parkL2);

        // ── 여행 (TRAVEL) ───────────────────────────────────────────────────
        QuestionSet travelL3 = new QuestionSet("Travel - Level 3", SurveyDifficulty.LEVEL_3, SurveyTopic.TRAVEL);
        travelL3.getCombos().addAll(List.of(
                comboA("여행 패턴A-1", travelL3, 1,
                        "Describe the kinds of places you like to travel to. What do you look for in a destination?",
                        "What do you usually do to prepare for a trip? Describe your typical routine.",
                        "Tell me about a trip you took recently. Where did you go and what did you do?"),
                comboB("여행 패턴B-1", travelL3, 2,
                        "Describe a place you have visited that you really enjoyed. What was it like?",
                        "Tell me about the first time you traveled somewhere on your own.",
                        "What is the most memorable trip you have ever taken? What made it unforgettable?"),
                comboA("여행 패턴A-2", travelL3, 3,
                        "Describe the most popular travel destinations in your country.",
                        "How do you usually spend your time when you are traveling?",
                        "Tell me about a recent travel experience that was different from what you expected.")
        ));
        questionSetRepository.save(travelL3);

        // ── 조깅 (JOGGING) ──────────────────────────────────────────────────
        QuestionSet joggingL3 = new QuestionSet("Jogging - Level 3", SurveyDifficulty.LEVEL_3, SurveyTopic.JOGGING);
        joggingL3.getCombos().addAll(List.of(
                comboA("조깅 패턴A-1", joggingL3, 1,
                        "Describe where you usually go jogging. What is the area like?",
                        "What is your jogging routine? How often do you go and what do you do before and after?",
                        "Tell me about a jogging session you had recently. How did it go?"),
                comboB("조깅 패턴B-1", joggingL3, 2,
                        "Describe your jogging gear or equipment. What do you wear or bring?",
                        "Tell me about when you first started jogging. What made you start?",
                        "What is the most memorable experience you have had while jogging?")
        ));
        questionSetRepository.save(joggingL3);

        // ── 독서 (READING) ──────────────────────────────────────────────────
        QuestionSet readingL3 = new QuestionSet("Reading - Level 3", SurveyDifficulty.LEVEL_3, SurveyTopic.READING);
        readingL3.getCombos().addAll(List.of(
                comboA("독서 패턴A-1", readingL3, 1,
                        "Describe the types of books you enjoy reading. What genres do you prefer?",
                        "How often do you read and what is your typical reading routine?",
                        "Tell me about a book you have read recently. What was it about?"),
                comboB("독서 패턴B-1", readingL3, 2,
                        "Describe your favorite place to read. What makes it a good reading spot?",
                        "Tell me about the first book you remember really loving. What was it about?",
                        "What is the most memorable book you have ever read and why?")
        ));
        questionSetRepository.save(readingL3);

        // ── 요리 (COOKING) ──────────────────────────────────────────────────
        QuestionSet cookingL3 = new QuestionSet("Cooking - Level 3", SurveyDifficulty.LEVEL_3, SurveyTopic.COOKING);
        cookingL3.getCombos().addAll(List.of(
                comboA("요리 패턴A-1", cookingL3, 1,
                        "Describe the kinds of food you like to cook. What are your favorite dishes to make?",
                        "How often do you cook and what is your typical cooking routine?",
                        "Tell me about something you cooked recently. How did it turn out?"),
                comboB("요리 패턴B-1", cookingL3, 2,
                        "Describe your kitchen. What equipment or tools do you use most often?",
                        "Tell me about the first dish you learned to cook on your own.",
                        "What is the most memorable cooking experience you have had?")
        ));
        questionSetRepository.save(cookingL3);

        // ── 쇼핑 (SHOPPING) ─────────────────────────────────────────────────
        QuestionSet shoppingL3 = new QuestionSet("Shopping - Level 3", SurveyDifficulty.LEVEL_3, SurveyTopic.SHOPPING);
        shoppingL3.getCombos().addAll(List.of(
                comboA("쇼핑 패턴A-1", shoppingL3, 1,
                        "Describe the places where you usually go shopping. What are they like?",
                        "How often do you go shopping and what is your typical shopping routine?",
                        "Tell me about something you bought recently. What was it and why did you buy it?"),
                comboB("쇼핑 패턴B-1", shoppingL3, 2,
                        "Describe your favorite store or shopping mall. What makes it your favorite?",
                        "Tell me about the first time you went shopping by yourself.",
                        "What is the most memorable shopping experience you have ever had?")
        ));
        questionSetRepository.save(shoppingL3);

        // ── 재활용 (RECYCLING) - 돌발 빈출 ────────────────────────────────
        QuestionSet recyclingL3 = new QuestionSet("Recycling - Level 3", SurveyDifficulty.LEVEL_3, SurveyTopic.RECYCLING);
        recyclingL3.getCombos().addAll(List.of(
                comboA("재활용 패턴A-1", recyclingL3, 1,
                        "Describe how people in your country recycle. What kinds of items do they recycle?",
                        "What do you do in your daily life to recycle or reduce waste? Describe your habits.",
                        "Tell me about something you recycled or reused recently."),
                comboB("재활용 패턴B-1", recyclingL3, 2,
                        "Describe the recycling system in your neighborhood. How does it work?",
                        "Tell me about the first time you became aware of the importance of recycling.",
                        "What is the most memorable experience you have had related to recycling or environmental issues?")
        ));
        questionSetRepository.save(recyclingL3);

        // ── 기술 (TECHNOLOGY) - 돌발 빈출 ──────────────────────────────────
        QuestionSet techL3 = new QuestionSet("Technology - Level 3", SurveyDifficulty.LEVEL_3, SurveyTopic.TECHNOLOGY);
        techL3.getCombos().addAll(List.of(
                comboA("기술 패턴A-1", techL3, 1,
                        "Describe a piece of technology you use every day. What does it look like and what do you use it for?",
                        "How do you typically use technology in your daily routine? Describe what you do.",
                        "Tell me about a new piece of technology you started using recently."),
                comboB("기술 패턴B-1", techL3, 2,
                        "Describe how technology has changed the way people communicate in your country.",
                        "Tell me about the first time you used a piece of technology that really impressed you.",
                        "What is the most memorable experience you have had related to technology?")
        ));
        questionSetRepository.save(techL3);

        System.out.println("[DataInitializer] OPIc 콤보 데이터 초기화 완료.");
    }
}
