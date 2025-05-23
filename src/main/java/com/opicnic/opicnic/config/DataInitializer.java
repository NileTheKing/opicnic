package com.opicnic.opicnic.config; // 또는 com.opicnic.opicnic.config 등 적절한 패키지

import com.opicnic.opicnic.domain.*;
import com.opicnic.opicnic.repository.QuestionSetRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional; // 추가

import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private final QuestionSetRepository questionSetRepository;

    public DataInitializer(QuestionSetRepository questionSetRepository) {
        this.questionSetRepository = questionSetRepository;
    }

    @Override
    @Transactional // 데이터 변경 작업이므로 트랜잭션 처리
    public void run(String... args) throws Exception {
        // 데이터가 이미 있는지 확인하는 로직 추가 가능 (예: count 쿼리)
        if (questionSetRepository.count() == 0) {
            populateSampleData();
        }
    }

    private void populateSampleData() {
        // --- 영화 주제 세트 (난이도 3) ---
        QuestionSet movieSetL3 = new QuestionSet("Movie Set - Level 3", SurveyDifficulty.LEVEL_3, SurveyTopic.MOVIE);

        Combo recentMovieCombo = new Combo("Recent Movie Experience Combo", movieSetL3, 1);
        Question q1_1_m_l3 = new Question("What is the most recent movie you watched? Please describe it in detail.", 1, recentMovieCombo);
        Question q1_2_m_l3 = new Question("What was the most impressive part of that movie for you?", 2, recentMovieCombo);
        recentMovieCombo.getQuestions().addAll(List.of(q1_1_m_l3, q1_2_m_l3));

        Combo favoriteGenreCombo = new Combo("Favorite Movie Genre Combo", movieSetL3, 2);
        Question q2_1_m_l3 = new Question("What is your favorite movie genre, and why?", 1, favoriteGenreCombo);
        Question q2_2_m_l3 = new Question("Can you recommend a movie from that genre that you particularly like?", 2, favoriteGenreCombo);
        favoriteGenreCombo.getQuestions().addAll(List.of(q2_1_m_l3, q2_2_m_l3));

        movieSetL3.getCombos().addAll(List.of(recentMovieCombo, favoriteGenreCombo));
        questionSetRepository.save(movieSetL3);

        // --- 음악 주제 세트 (난이도 4) ---
        QuestionSet musicSetL4 = new QuestionSet("Music Set - Level 4", SurveyDifficulty.LEVEL_4, SurveyTopic.MUSIC);

        Combo favArtistCombo = new Combo("Favorite Artist Deep Dive Combo", musicSetL4, 1);
        Question q1_1_mu_l4 = new Question("Who is your favorite singer or band? Tell me about their music in detail.", 1, favArtistCombo);
        Question q1_2_mu_l4 = new Question("What is it about that artist that captivates you (e.g., lyrics, melody, performance)?", 2, favArtistCombo);
        Question q1_3_mu_l4 = new Question("Is there a song by that artist that has special meaning to you? Please introduce it.", 3, favArtistCombo);
        favArtistCombo.getQuestions().addAll(List.of(q1_1_mu_l4, q1_2_mu_l4, q1_3_mu_l4));

        musicSetL4.getCombos().add(favArtistCombo);
        questionSetRepository.save(musicSetL4);

        // --- 공원 주제 세트 (난이도 2) ---
        QuestionSet parkSetL2 = new QuestionSet("Park Set - Level 2", SurveyDifficulty.LEVEL_2, SurveyTopic.PARK);
        Combo frequentParkCombo = new Combo("Frequent Park Visits Combo", parkSetL2, 1);
        Question q1_1_p_l2 = new Question("Describe a park you often go to.", 1, frequentParkCombo);
        Question q1_2_p_l2 = new Question("What do you usually do when you go to that park?", 2, frequentParkCombo);
        frequentParkCombo.getQuestions().addAll(List.of(q1_1_p_l2, q1_2_p_l2));

        parkSetL2.getCombos().add(frequentParkCombo);
        questionSetRepository.save(parkSetL2);

        System.out.println("Sample OPIc question data populated.");
    }
}