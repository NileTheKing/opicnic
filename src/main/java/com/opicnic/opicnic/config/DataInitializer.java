package com.opicnic.opicnic.config;

import com.opicnic.opicnic.domain.Combo;
import com.opicnic.opicnic.domain.Question;
import com.opicnic.opicnic.domain.QuestionSet;
import com.opicnic.opicnic.domain.enums.QuestionType;
import com.opicnic.opicnic.domain.enums.SurveyTopic;
import com.opicnic.opicnic.repository.QuestionSetRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static com.opicnic.opicnic.domain.enums.QuestionType.*;

@Component
public class DataInitializer implements CommandLineRunner {

    private final QuestionSetRepository questionSetRepository;

    public DataInitializer(QuestionSetRepository questionSetRepository) {
        this.questionSetRepository = questionSetRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (questionSetRepository.count() == 0) {
            populateData();
        }
        if (questionSetRepository.findExistingTopics(
                List.of(SurveyTopic.BANK_VISIT)).isEmpty()) {
            populateSurpriseData();
        }
        if (questionSetRepository.findExistingTopics(
                List.of(SurveyTopic.MOBILE_PHONE)).isEmpty()) {
            populateSurpriseDataV2();
        }
        if (questionSetRepository.findExistingTopics(
                List.of(SurveyTopic.TECHNOLOGY)).isEmpty()) {
            populateSurpriseDataV3();
        }
    }

    private void addStandardCombos(QuestionSet set) {
        set.getCombos().addAll(List.of(
            new Combo("C1", set, List.of(TYPE_1, TYPE_2, TYPE_3)),
            new Combo("C2", set, List.of(TYPE_1, TYPE_3, TYPE_4)),
            new Combo("C_RP", set, List.of(TYPE_6, TYPE_7, TYPE_8)),
            new Combo("C_Comp", set, List.of(TYPE_9, TYPE_10))
        ));
    }

    // 기본 세트 생성 (유형 10개 전부)
    private QuestionSet buildSet(String name, SurveyTopic topic, Map<QuestionType, String> questions) {
        QuestionSet set = new QuestionSet(name, topic);
        for (QuestionType type : QuestionType.values()) {
            set.getQuestions().add(new Question(questions.get(type), type, set));
        }
        addStandardCombos(set);
        return set;
    }

    // 변형 세트 생성 — base에서 overrides에 있는 유형만 교체
    private QuestionSet buildVariantSet(String name, SurveyTopic topic,
            Map<QuestionType, String> base, Map<QuestionType, String> overrides) {
        Map<QuestionType, String> merged = new EnumMap<>(base);
        merged.putAll(overrides);
        return buildSet(name, topic, merged);
    }

    private Map<QuestionType, String> q(
            String q1, String q2, String q3, String q4, String q5,
            String q6, String q7, String q8, String q9, String q10) {
        Map<QuestionType, String> m = new EnumMap<>(QuestionType.class);
        m.put(TYPE_1, q1); m.put(TYPE_2, q2); m.put(TYPE_3, q3); m.put(TYPE_4, q4); m.put(TYPE_5, q5);
        m.put(TYPE_6, q6); m.put(TYPE_7, q7); m.put(TYPE_8, q8); m.put(TYPE_9, q9); m.put(TYPE_10, q10);
        return m;
    }

    private void populateData() {

        // ── 가족과 함께 거주 (LIVING_WITH_FAMILY) — 3세트, TYPE_5만 동일 ─────
        String familyType5 = "I also live with my family. Ask me 3 or 4 questions about my home and living situation.";
        Map<QuestionType, String> family1 = q(
            "Describe the home you share with your family. What does it look like and where is it?",
            "What is your typical daily routine at home with your family?",
            "Tell me about a problem that recently occurred at your home. What happened and how was it resolved?",
            "Describe the most memorable experience you have had living with your family.",
            familyType5,
            "You need to buy new furniture for your home. Call the furniture store and ask 3 or 4 questions about what you need.",
            "The furniture you ordered arrived damaged. Explain the problem to the store and suggest ways to resolve it.",
            "Tell me about a time when you bought something and it turned out to have a problem. What happened and how did you deal with it?",
            "Compare your current home to the home you lived in when you were young. How have living spaces changed?",
            "What are some issues or challenges related to housing costs and the real estate market in your country today?"
        );
        Map<QuestionType, String> family2 = q(
            "Describe the types of activities and chores that happen regularly in your home.",
            "How does your family manage household chores? Describe how tasks are divided and handled.",
            "Tell me about the household chores you had to do when you were young. How has that changed over time?",
            "Describe a memorable experience you had while helping your family at home.",
            familyType5,
            "You want to invite a friend over to your home for a gathering. Call them and ask 3 or 4 questions to plan the visit.",
            "Something came up and you cannot host your guest as planned. Explain the situation and suggest some alternatives.",
            "Tell me about a time when you had to change or cancel plans you made with family or friends. What happened?",
            "How have family roles and household responsibilities changed over the past few decades?",
            "What kinds of issues arise in households where multiple generations live together?"
        );
        Map<QuestionType, String> family3 = q(
            "Please describe your room at home. What does it look like and how is it arranged?",
            "Describe your daily routine at home with your family. What activities do you do together?",
            "Tell me about how your home has changed since you were young. How does it compare to where you grew up?",
            "Describe a particularly memorable experience you have had at home with your family.",
            familyType5,
            "A relative has asked you to look after their home while they are away. Call them and ask 3 or 4 questions about what you need to do.",
            "You arrive at your relative's home to look after it but find yourself locked out. Explain the situation and suggest ways to solve the problem.",
            "Tell me about a time when you had difficulty helping out a family member. What happened and how did you handle it?",
            "How have homes and living spaces changed over the past 20 to 30 years? Give specific examples.",
            "What are some current social issues related to how families live and find housing today?"
        );
        questionSetRepository.save(buildSet("가족거주-세트1", SurveyTopic.LIVING_WITH_FAMILY, family1));
        questionSetRepository.save(buildSet("가족거주-세트2", SurveyTopic.LIVING_WITH_FAMILY, family2));
        questionSetRepository.save(buildSet("가족거주-세트3", SurveyTopic.LIVING_WITH_FAMILY, family3));

        // ── 영화 보기 (MOVIE_WATCHING) — 1세트 ───────────────────────────────
        questionSetRepository.save(buildSet("영화보기-세트1", SurveyTopic.MOVIE_WATCHING, q(
            "Describe the type of movies you enjoy watching. What genres do you prefer and why?",
            "How often do you watch movies and what is your typical movie-watching routine?",
            "Tell me about a movie you watched recently. What was it about and how did you like it?",
            "What is the most memorable movie you have ever seen? Why was it so special to you?",
            "I also enjoy watching movies. Ask me 3 or 4 questions about the kinds of movies I like.",
            "Call the theater to ask about movies currently showing and ticket availability. Ask 3 or 4 questions.",
            "You arrive at the theater and find your preferred movie is sold out. Explain the problem and ask about alternatives.",
            "Have you ever had a similar experience where your movie plan did not go as expected? Tell me about it.",
            "How has movie-watching changed over the past 10 to 20 years? Compare the past and the present.",
            "What are some issues or concerns related to the film industry today?"
        )));

        // ── 공연 보기 (PERFORMANCE_WATCHING) — 1세트 ─────────────────────────
        questionSetRepository.save(buildSet("공연보기-세트1", SurveyTopic.PERFORMANCE_WATCHING, q(
            "Describe the type of performances you enjoy watching, such as plays or musicals.",
            "How often do you attend performances and what does your typical experience involve?",
            "Tell me about a performance you attended recently. What was it like?",
            "Describe the most unforgettable performance you have ever seen.",
            "Imagine you want to attend a performance and need to get more information.",
            "Call the venue to ask about an upcoming performance. Ask 3-4 questions.",
            "You find out the show is sold out. Explain the situation and ask about other options.",
            "Have you ever been in a similar situation when trying to attend a performance? Tell me about it.",
            "How have live performances and the entertainment industry changed over the years?",
            "What are some challenges or issues facing the live performance industry today?"
        )));


        // ── 공원 가기 (PARK_GOING) — 2세트, Set2는 TYPE_7만 다름 ───────────
        Map<QuestionType, String> park1 = q(
            "Describe a park you often visit. What does it look like and what facilities does it have?",
            "What do you usually do when you visit the park? Describe your typical routine.",
            "Tell me about a recent visit to the park. What did you do there?",
            "What is the most memorable experience you have had at a park?",
            "Imagine you want to plan a park outing with a friend.",
            "Call a friend to discuss plans for visiting a park. Ask 3-4 questions.",
            "It starts raining when you arrive at the park. Suggest two or three alternatives for the day.",
            "Have you ever had a similar situation where outdoor plans were disrupted? Tell me about it.",
            "How have parks and public outdoor spaces changed over the years?",
            "What are some issues or concerns related to public parks and green spaces today?"
        );
        questionSetRepository.save(buildSet("공원-세트1", SurveyTopic.PARK_GOING, park1));
        questionSetRepository.save(buildVariantSet("공원-세트2", SurveyTopic.PARK_GOING, park1, Map.of(
            TYPE_7, "The park is under construction and half the facilities are unavailable. Suggest alternative activities or nearby locations."
        )));

        // ── 해변 가기 (BEACH_GOING) — 1세트 ─────────────────────────────────
        questionSetRepository.save(buildSet("해변-세트1", SurveyTopic.BEACH_GOING, q(
            "Describe a beach you like to visit. What does it look like?",
            "What do you typically do when you go to the beach?",
            "Tell me about a recent trip to the beach. What did you do there?",
            "Describe the most memorable beach experience you have ever had.",
            "Imagine you are planning a beach trip and need to make arrangements.",
            "Call a beach resort or rental shop to ask about availability. Ask 3-4 questions.",
            "You arrive at the beach and find it is too crowded or closed. Suggest alternatives for the day.",
            "Have you ever had a similar situation where a beach trip did not go as planned? Tell me about it.",
            "How has beach tourism and coastal recreation changed over the years?",
            "What are some environmental issues related to beaches and coastal areas today?"
        )));

        // ── 스포츠 관람 (SPORTS_WATCHING) — 1세트 ────────────────────────────
        questionSetRepository.save(buildSet("스포츠관람-세트1", SurveyTopic.SPORTS_WATCHING, q(
            "Describe the type of sports you enjoy watching. What is your favorite sport?",
            "How often do you watch sports and what is your typical routine when watching a game?",
            "Tell me about a sports event you watched recently. What happened?",
            "Describe the most exciting sports event you have ever watched.",
            "Imagine you want to attend a live sports event and need to get tickets.",
            "Call the stadium or ticket office to ask about upcoming events. Ask 3-4 questions.",
            "You cannot get the seats you wanted. Explain the problem and ask about other options.",
            "Have you ever had a similar experience when trying to attend a sports event? Tell me about it.",
            "How has watching sports changed over the past few decades?",
            "What are some issues or controversies related to professional sports today?"
        )));

        // ── 카페/커피전문점 (COFFEE_SHOP_GOING) — 1세트 ─────────────────────
        questionSetRepository.save(buildSet("카페-세트1", SurveyTopic.COFFEE_SHOP_GOING, q(
            "Describe your favorite coffee shop. What does it look like and what do you like about it?",
            "How often do you go to coffee shops and what do you usually do there?",
            "Tell me about a recent visit to a coffee shop. What did you order and do?",
            "Describe the most memorable experience you have had at a coffee shop.",
            "Imagine you are at a coffee shop and want to find out more about their menu or services.",
            "Ask the barista about the menu and any special drinks. Ask 3-4 questions.",
            "Your order was made incorrectly. Explain the problem to the staff and suggest a solution.",
            "Have you ever had a similar experience where something went wrong at a cafe? Tell me about it.",
            "How has coffee shop culture changed in your country over the years?",
            "What are some issues related to the coffee shop industry or coffee culture today?"
        )));

        // ── 쇼핑하기 (SHOPPING) — 1세트 ─────────────────────────────────────
        questionSetRepository.save(buildSet("쇼핑-세트1", SurveyTopic.SHOPPING, q(
            "Describe the places where you usually go shopping. What are they like?",
            "How often do you go shopping and what is your typical shopping routine?",
            "Tell me about something you bought recently. What was it and why did you buy it?",
            "What is the most memorable shopping experience you have ever had?",
            "Imagine you want to buy a specific item and are calling a store to check availability.",
            "Call a store to ask about a product you want to buy. Ask 3-4 questions.",
            "The item you want is out of stock. Explain the situation and ask about alternatives.",
            "Have you ever been in a similar situation when shopping? Tell me about it.",
            "How has shopping changed with the rise of online stores and technology?",
            "What are some issues or concerns related to shopping and consumer culture today?"
        )));

        // ── TV 시청하기 (TV_WATCHING) — 1세트 ────────────────────────────────
        questionSetRepository.save(buildSet("TV시청-세트1", SurveyTopic.TV_WATCHING, q(
            "Describe the types of TV shows or programs you enjoy watching.",
            "How often do you watch TV and what is your typical TV-watching routine?",
            "Tell me about a TV show or program you watched recently.",
            "Describe the most memorable TV show or episode you have ever watched.",
            "Imagine you want to find out more about a TV subscription service.",
            "Call a TV service provider to ask about their plans and channels. Ask 3-4 questions.",
            "You have a problem with your TV service. Explain the issue and ask how to fix it.",
            "Have you ever had a similar experience with a TV or streaming service? Tell me about it.",
            "How has television and media consumption changed over the past 20 years?",
            "What are some issues related to TV content, streaming services, or media today?"
        )));

        // ── 음악 감상하기 (MUSIC_LISTENING) — 1세트 ─────────────────────────
        questionSetRepository.save(buildSet("음악감상-세트1", SurveyTopic.MUSIC_LISTENING, q(
            "Describe the type of music you enjoy listening to. What artists or genres do you prefer?",
            "How do you usually listen to music? Describe your typical listening habits and routine.",
            "Tell me about how you first became interested in music. How has your music taste changed over time?",
            "Describe the most impressive live music performance or concert you have ever experienced.",
            "I also enjoy listening to music. Ask me 3 or 4 questions about my music preferences and listening habits.",
            "You want to buy a new device or choose a streaming service for music. Ask a friend or salesperson 3 or 4 questions to help you decide.",
            "Your music device suddenly stopped working right before an important moment. Explain the problem and suggest some solutions.",
            "Tell me about a time when your device or equipment broke down at an important moment. What happened and how did you deal with it?",
            "How has the way people listen to music changed over the past 10 to 20 years? Compare the past and the present.",
            "What are some issues related to music streaming, digital devices, or the music industry today?"
        )));

        // ── 혼자 노래 부르거나 합창하기 (SINGING) — 1세트 ───────────────────
        questionSetRepository.save(buildSet("노래부르기-세트1", SurveyTopic.SINGING, q(
            "Describe when and where you usually sing. Do you sing alone or with others?",
            "How often do you sing and what is your typical singing routine?",
            "Tell me about a recent time you sang. What did you sing and how did it go?",
            "Describe the most memorable singing experience you have ever had.",
            "Imagine you want to join a singing group or choir and need more information.",
            "Call a choir or singing class to ask about joining. Ask 3-4 questions.",
            "The class you want to join is full. Explain the problem and ask about alternatives.",
            "Have you ever been in a situation where you had trouble joining a group activity? Tell me about it.",
            "How has singing culture changed compared to the past?",
            "What are some social benefits or issues related to group singing and choirs today?"
        )));

        // ── 악기 연주하기 (INSTRUMENT_PLAYING) — 1세트 ───────────────────────
        questionSetRepository.save(buildSet("악기연주-세트1", SurveyTopic.INSTRUMENT_PLAYING, q(
            "Describe the instrument you play or would like to play. What is it like?",
            "How often do you play an instrument and what is your practice routine?",
            "Tell me about a recent time you played an instrument. How did the session go?",
            "What is the most memorable experience you have had playing or performing music?",
            "Imagine you want to take music lessons and are calling a music school.",
            "Call a music school to ask about available lessons and schedules. Ask 3-4 questions.",
            "The lesson schedule conflicts with your availability. Explain and ask about other options.",
            "Have you ever had a similar experience when trying to learn a new skill? Tell me about it.",
            "How has music education and learning instruments changed over the years?",
            "What are some challenges or issues related to learning music in modern times?"
        )));

        // ── 요리하기 (COOKING) — 1세트 ───────────────────────────────────────
        questionSetRepository.save(buildSet("요리-세트1", SurveyTopic.COOKING, q(
            "Describe the types of food you like to cook. What are your favorite dishes?",
            "How often do you cook and what is your typical cooking routine?",
            "Tell me about something you cooked recently. How did it turn out?",
            "What is the most memorable cooking experience you have ever had?",
            "Imagine you need to buy ingredients for a special dish and are calling a grocery store.",
            "Call the store to ask if specific ingredients are available. Ask 3-4 questions.",
            "The main ingredient you need is out of stock. Suggest two or three substitute options.",
            "Have you ever been in a situation where you had to improvise while cooking? Tell me about it.",
            "How has cooking and home food culture changed over the past few decades?",
            "What are some issues related to food, nutrition, or cooking habits in society today?"
        )));

        // ── 독서 (READING) — 1세트 ───────────────────────────────────────────
        questionSetRepository.save(buildSet("독서-세트1", SurveyTopic.READING, q(
            "Describe the types of books you enjoy reading. What genres do you prefer?",
            "How often do you read and what is your typical reading routine?",
            "Tell me about a book you read recently. What was it about?",
            "What is the most memorable book you have ever read and why?",
            "Imagine you are looking for a specific book at a bookstore.",
            "Call the bookstore to ask if a book is available. Ask 3-4 questions.",
            "The book is not in stock. Explain the situation and ask about alternatives.",
            "Have you ever had a similar experience when looking for a book? Tell me about it.",
            "How has reading culture changed with the rise of e-books and digital content?",
            "What are some issues or concerns related to reading habits in society today?"
        )));

        // ── 걷기 (WALKING) — 2세트, Set2는 TYPE_6,7,8만 다름 ─────────────────
        Map<QuestionType, String> walk1 = q(
            "Describe where you usually go for walks. What is the area like?",
            "How often do you walk and what is your typical walking routine?",
            "Tell me about a walk you took recently. Where did you go and what did you see?",
            "What is the most memorable walking experience you have ever had?",
            "Imagine you want to join a walking club and need more information.",
            "Call a walking club to ask about membership and routes. Ask 3-4 questions.",
            "The walk you wanted to join is already full. Suggest two or three alternative options.",
            "Have you ever been in a similar situation when joining an outdoor activity? Tell me about it.",
            "How have walking habits and outdoor activity culture changed over the years?",
            "What are some benefits or issues related to walking as a form of exercise in modern life?"
        );
        questionSetRepository.save(buildSet("걷기-세트1", SurveyTopic.WALKING, walk1));
        questionSetRepository.save(buildVariantSet("걷기-세트2", SurveyTopic.WALKING, walk1, Map.of(
            TYPE_6, "Call a local park or trail office to report a safety issue on the path. Ask 3-4 questions.",
            TYPE_7, "The trail you want to walk is temporarily closed. Suggest alternative routes or activities.",
            TYPE_8, "Have you ever had a similar experience where a planned outdoor activity was disrupted? Tell me about it."
        )));

        // ── 조깅 (JOGGING) — 1세트 ───────────────────────────────────────────
        questionSetRepository.save(buildSet("조깅-세트1", SurveyTopic.JOGGING, q(
            "Where do you usually go jogging? Describe the place in as much detail as possible.",
            "What is your jogging routine? How often do you go and what do you do before and after?",
            "Tell me about how you first got into jogging. How has your jogging routine changed since then?",
            "Describe the most memorable experience you have had while jogging.",
            "I also enjoy jogging. Ask me 3 or 4 questions about my jogging habits and favorite places to run.",
            "A friend has suggested going jogging together. Call your friend and ask 3 or 4 questions to plan your jogging session.",
            "Something unexpected came up and you cannot go jogging with your friend as planned. Explain the situation and suggest some alternatives.",
            "Have you ever had a memorable or unexpected experience while jogging? Tell me what happened.",
            "How has jogging or running culture changed in your country over the years? Compare the past and the present.",
            "What are some common jogging-related injuries and what can people do to prevent them?"
        )));

        // ── 헬스/피트니스 (FITNESS_GYM) — 1세트 ─────────────────────────────
        questionSetRepository.save(buildSet("헬스-세트1", SurveyTopic.FITNESS_GYM, q(
            "Describe the gym or fitness center you use. What facilities does it have?",
            "How often do you go to the gym and what does your workout routine look like?",
            "Tell me about your most recent workout session. What did you do?",
            "What is the most memorable experience you have had at the gym?",
            "Imagine you want to sign up for a gym membership.",
            "Call a gym to ask about membership options and facilities. Ask 3-4 questions.",
            "The membership fee is higher than expected. Explain your concern and ask about deals.",
            "Have you ever been in a similar situation when signing up for a fitness service? Tell me about it.",
            "How has gym culture and fitness trends changed over the years?",
            "What are some issues related to health, fitness facilities, or exercise culture today?"
        )));

        // ── 국내 여행 (DOMESTIC_TRAVEL) — 2세트, Set2는 TYPE_7만 다름 ─────────
        Map<QuestionType, String> domestic1 = q(
            "What are your favorite domestic travel destinations? Describe one place you enjoy visiting.",
            "How do you typically plan and prepare for a domestic trip? Describe the steps you take.",
            "Tell me about a trip you took when you were young. Where did you go and what was it like?",
            "Describe the most memorable domestic travel experience you have ever had.",
            "I also enjoy traveling within the country. Ask me 3 or 4 questions about my domestic travel experiences and preferences.",
            "You want to plan a domestic trip. Call a travel agency and ask 3 or 4 questions about destinations, schedules, and costs.",
            "You bought a non-refundable ticket for a trip but something came up and you cannot use it. Explain the situation and suggest ways to resolve it.",
            "Tell me about a time when something went wrong while planning or going on a vacation. What happened and how did you handle it?",
            "How has domestic travel changed in your country over the past few decades? Compare the past and the present.",
            "What are some problems or challenges related to travel today? What solutions would you suggest?"
        );
        questionSetRepository.save(buildSet("국내여행-세트1", SurveyTopic.DOMESTIC_TRAVEL, domestic1));
        questionSetRepository.save(buildVariantSet("국내여행-세트2", SurveyTopic.DOMESTIC_TRAVEL, domestic1, Map.of(
            TYPE_7, "You planned a trip for a specific date but found out you cannot travel on that day. Explain the situation and find an alternative solution."
        )));

        // ── 집에서 보내는 휴가 (STAYCATION) — 1세트 ─────────────────────────
        questionSetRepository.save(buildSet("스테이케이션-세트1", SurveyTopic.STAYCATION, q(
            "Describe what your home is like when you spend a vacation there.",
            "What do you usually do when you spend your vacation at home?",
            "Tell me about a recent vacation you had at home. What did you do?",
            "What is the most enjoyable staycation experience you have ever had?",
            "Imagine you are planning to stay at a hotel in your city for a staycation.",
            "Call the hotel to ask about amenities and packages for a local stay. Ask 3-4 questions.",
            "The amenities you wanted are not available. Suggest alternatives for your staycation.",
            "Have you ever had a similar experience when planning a staycation? Tell me about it.",
            "How has the concept of staying home for vacation changed in recent years?",
            "What are some benefits or drawbacks of staycations compared to traveling abroad?"
        )));

        // ── 독신 (LIVING_ALONE) — 2세트 base, 롤플레이 콤보 4세트 ──────────────
        String aloneType5 = "I also live alone. Ask me 3 or 4 questions about my home and daily life.";
        Map<QuestionType, String> alone1 = q(
            "Describe the home where you live alone. What does it look like and where is it located?",
            "What is your typical daily routine at home? How do you manage your household on your own?",
            "Tell me about a problem that recently occurred at your home. What happened and how did you resolve it?",
            "Describe the most memorable experience you have had living on your own.",
            aloneType5,
            "You need to buy new appliances for your home. Call the store and ask 3 or 4 questions about what you need.",
            "The appliance you ordered was delivered damaged. Explain the problem and suggest ways to resolve it.",
            "Tell me about a time when something you purchased had a problem. What happened and how did you handle it?",
            "Compare your current life living alone to when you lived with others. What are the main differences?",
            "What are some social issues related to the growing trend of people living alone today?"
        );
        Map<QuestionType, String> alone2 = q(
            "Describe the neighborhood where you live alone. What is it like and what facilities are nearby?",
            "How do you manage household chores and meals when living by yourself? Describe your routine.",
            "Tell me about a challenge you faced when you first started living alone. How did you overcome it?",
            "Describe a particularly memorable moment from your life living independently.",
            aloneType5,
            "You want to invite friends over for a gathering at your place. Call them and ask 3 or 4 questions to plan the visit.",
            "Something came up and you cannot host your guests as planned. Explain the situation and suggest alternatives.",
            "Tell me about a time when you had to change or cancel plans with friends. What happened and how did you handle it?",
            "How has the concept of living alone changed in your society over the past few decades?",
            "What are some advantages and disadvantages of living alone compared to living with others?"
        );
        questionSetRepository.save(buildSet("독신-세트1", SurveyTopic.LIVING_ALONE, alone1));
        questionSetRepository.save(buildSet("독신-세트2", SurveyTopic.LIVING_ALONE, alone2));
        questionSetRepository.save(buildVariantSet("독신-세트1-RP2", SurveyTopic.LIVING_ALONE, alone1, Map.of(
            TYPE_6, "Your home needs repairs. Call a repair service and ask 3 or 4 questions about availability and costs.",
            TYPE_7, "The repair technician cannot come on the scheduled day. Explain the situation and suggest alternative arrangements.",
            TYPE_8, "Tell me about a time when you had to deal with a home repair or maintenance issue on your own."
        )));
        questionSetRepository.save(buildVariantSet("독신-세트2-RP2", SurveyTopic.LIVING_ALONE, alone2, Map.of(
            TYPE_6, "You are looking for a new apartment. Call a real estate agent and ask 3 or 4 questions about available properties.",
            TYPE_7, "The apartment you wanted to rent was already taken. Explain the situation and ask about other options.",
            TYPE_8, "Tell me about a time when you had difficulty finding or moving into a new place. What happened?"
        )));

        // ── 콘서트 보기 (CONCERT_WATCHING) — 2세트 ──────────────────────────────
        Map<QuestionType, String> concert1 = q(
            "Describe the type of concerts you enjoy attending. What genres or artists do you prefer?",
            "How often do you go to concerts and what does your typical concert experience look like?",
            "Tell me about a concert you attended recently. What was it like?",
            "Describe the most unforgettable concert you have ever been to.",
            "I also enjoy going to concerts. Ask me 3 or 4 questions about my concert experiences and preferences.",
            "You want to buy tickets for an upcoming concert. Call the ticketing office and ask 3 or 4 questions.",
            "The concert tickets you wanted are sold out. Explain the situation and ask about alternative options.",
            "Tell me about a time when you had trouble getting tickets to an event. What happened and how did you deal with it?",
            "How has the concert experience changed over the past 10 to 20 years? Compare the past and the present.",
            "What are some issues or concerns related to the live music and concert industry today?"
        );
        questionSetRepository.save(buildSet("콘서트-세트1", SurveyTopic.CONCERT_WATCHING, concert1));
        questionSetRepository.save(buildVariantSet("콘서트-세트2", SurveyTopic.CONCERT_WATCHING, concert1, Map.of(
            TYPE_6, "You want to find out about a concert venue's facilities and services. Call the venue and ask 3 or 4 questions.",
            TYPE_7, "The concert you planned to attend was suddenly cancelled. Explain the problem and suggest alternative plans.",
            TYPE_8, "Tell me about a time when an event or plan you were looking forward to was unexpectedly cancelled. How did you handle it?"
        )));

        // ── 해외 여행 (INTERNATIONAL_TRAVEL) — 2세트, Set2는 TYPE_6,7,8만 다름 ──
        Map<QuestionType, String> international1 = q(
            "Describe a foreign country you have visited or would like to visit. What is it like?",
            "How do you typically plan and prepare for an international trip? Describe the process.",
            "Tell me about an international trip you took in the past. Where did you go and what was it like?",
            "Describe the most memorable experience you have had while traveling abroad.",
            "I also enjoy traveling internationally. Ask me 3 or 4 questions about my travel experiences and destinations.",
            "You are planning an international trip. Call a travel agency and ask 3 or 4 questions about packages, schedules, and costs.",
            "You arrive at your destination and find that your hotel reservation was not properly made. Explain the situation and suggest ways to resolve it.",
            "Tell me about a time when something went wrong while traveling abroad. What happened and how did you handle it?",
            "How has international travel changed over the past few decades? Compare the past and the present.",
            "What are some challenges or issues related to international travel today?"
        );
        questionSetRepository.save(buildSet("해외여행-세트1", SurveyTopic.INTERNATIONAL_TRAVEL, international1));
        questionSetRepository.save(buildVariantSet("해외여행-세트2", SurveyTopic.INTERNATIONAL_TRAVEL, international1, Map.of(
            TYPE_6, "You need to change your flight schedule. Call the airline and ask 3 or 4 questions about rebooking options.",
            TYPE_7, "Your flight was cancelled due to bad weather. Explain the situation to the airline staff and suggest alternatives.",
            TYPE_8, "Tell me about a time when your travel plans were disrupted due to unexpected circumstances. What happened and how did you cope?"
        )));

        System.out.println("[DataInitializer] 23개 주제 OPIc 데이터 초기화 완료.");
    }

    private void populateSurpriseData() {

        // ── 은행 (BANK_VISIT) ────────────────────────────────────────────────
        questionSetRepository.save(buildSet("은행-세트1", SurveyTopic.BANK_VISIT, q(
            "Describe the bank you usually use. Where is it located and what does it look like?",
            "How often do you visit the bank and what kinds of tasks do you usually handle there?",
            "Tell me about the last time you visited a bank. What did you go there for?",
            "Describe the most memorable experience you have had at a bank.",
            "I also use a bank regularly. Ask me 3 or 4 questions about my banking habits.",
            "You need to open a new account. Call the bank and ask 3 or 4 questions about the process and requirements.",
            "You go to the bank but the system is down and you cannot complete your transaction. Explain the situation and suggest alternatives.",
            "Have you ever had a problem at a bank or with a financial service? Tell me what happened and how you resolved it.",
            "How has banking changed over the past 10 to 20 years with the rise of online and mobile banking?",
            "What are some issues or concerns related to personal finance and banking services today?"
        )));

        // ── 도서관 (LIBRARY_VISIT) ───────────────────────────────────────────
        questionSetRepository.save(buildSet("도서관-세트1", SurveyTopic.LIBRARY_VISIT, q(
            "Describe the library you use or have used. What does it look like and what facilities does it have?",
            "How often do you go to the library and what do you usually do there?",
            "Tell me about your most recent visit to a library. What did you do?",
            "Describe the most memorable experience you have had at a library.",
            "I also enjoy using libraries. Ask me 3 or 4 questions about my library habits.",
            "You want to borrow a specific book. Call the library and ask 3 or 4 questions about availability and borrowing rules.",
            "The book you need is already checked out. Explain the situation and ask about alternatives.",
            "Have you ever had a problem at a library? Tell me what happened and how you dealt with it.",
            "How have libraries changed with the rise of digital books and online resources?",
            "What are some challenges facing public libraries in modern times?"
        )));

        // ── 호텔 (HOTEL_STAY) ────────────────────────────────────────────────
        questionSetRepository.save(buildSet("호텔-세트1", SurveyTopic.HOTEL_STAY, q(
            "Describe a hotel you have stayed at. What was it like?",
            "What do you usually look for when choosing a hotel? Describe your preferences.",
            "Tell me about your most recent hotel stay. What was your experience like?",
            "Describe the most memorable hotel experience you have ever had.",
            "I also stay at hotels when I travel. Ask me 3 or 4 questions about my hotel preferences.",
            "You want to make a hotel reservation. Call the hotel and ask 3 or 4 questions about rooms, prices, and amenities.",
            "You check in and find that your room is not what you reserved. Explain the problem to the staff and suggest solutions.",
            "Have you ever had a problem during a hotel stay? Tell me what happened and how it was resolved.",
            "How has the hotel industry changed with the rise of online booking and platforms like Airbnb?",
            "What are some issues or concerns related to hotels and the accommodation industry today?"
        )));

        // ── 식당 (RESTAURANT_VISIT) ──────────────────────────────────────────
        questionSetRepository.save(buildSet("식당-세트1", SurveyTopic.RESTAURANT_VISIT, q(
            "Describe a restaurant you enjoy going to. What is it like and what kind of food do they serve?",
            "How often do you eat out and what is your typical restaurant experience?",
            "Tell me about a restaurant you visited recently. What did you order and how was the food?",
            "Describe the most memorable dining experience you have ever had.",
            "I also enjoy eating out. Ask me 3 or 4 questions about my restaurant preferences.",
            "You want to make a reservation at a restaurant. Call and ask 3 or 4 questions about the menu and booking.",
            "You arrive at the restaurant but your reservation cannot be found. Explain the problem and suggest alternatives.",
            "Have you ever had a bad experience at a restaurant? Tell me what happened and how you handled it.",
            "How has the restaurant industry changed over the past few decades?",
            "What are some issues or concerns related to the food service industry today?"
        )));

        // ── 대중교통 (PUBLIC_TRANSPORTATION) ─────────────────────────────────
        questionSetRepository.save(buildSet("대중교통-세트1", SurveyTopic.PUBLIC_TRANSPORTATION, q(
            "Describe the public transportation system in your city. What options are available?",
            "How often do you use public transportation and what is your typical commute like?",
            "Tell me about your most recent experience using public transportation. How was it?",
            "Describe the most memorable or unusual experience you have had on public transportation.",
            "I also use public transportation regularly. Ask me 3 or 4 questions about my commuting habits.",
            "You need to find out about bus or subway routes for a trip. Call the transportation office and ask 3 or 4 questions.",
            "Your train or bus is delayed and you will be late for an important appointment. Explain the situation and suggest alternatives.",
            "Have you ever had a problem or unexpected experience while using public transportation? Tell me about it.",
            "How has public transportation changed in your city over the past few decades?",
            "What are some issues or challenges related to public transportation in your country today?"
        )));

        // ── 날씨 (WEATHER) ───────────────────────────────────────────────────
        questionSetRepository.save(buildSet("날씨-세트1", SurveyTopic.WEATHER, q(
            "Describe the climate in your area. What are the seasons like?",
            "How does the weather affect your daily routine and activities?",
            "Tell me about an extreme weather event you have experienced recently.",
            "Describe the most memorable weather-related experience you have ever had.",
            "I also pay close attention to the weather. Ask me 3 or 4 questions about how weather affects my life.",
            "You are planning an outdoor event but the forecast looks bad. Call a venue and ask 3 or 4 questions about indoor alternatives.",
            "Your outdoor event is ruined by unexpected rain. Explain the situation and suggest ways to handle it.",
            "Have you ever had to cancel or change plans because of bad weather? Tell me about it.",
            "How has the weather or climate in your region changed over the past few decades?",
            "What are some concerns related to climate change and extreme weather events today?"
        )));

        // ── 명절/휴일 (HOLIDAY_FESTIVAL) ────────────────────────────────────
        questionSetRepository.save(buildSet("명절-세트1", SurveyTopic.HOLIDAY_FESTIVAL, q(
            "Describe an important holiday or festival in your country. What is it like?",
            "What do you usually do during major holidays? Describe your typical routine.",
            "Tell me about how you spent a recent holiday. What did you do?",
            "Describe the most memorable holiday celebration you have ever experienced.",
            "I also celebrate holidays. Ask me 3 or 4 questions about my holiday traditions.",
            "You are planning a holiday gathering. Call a venue or restaurant and ask 3 or 4 questions about arrangements.",
            "Something goes wrong with your holiday plans at the last minute. Explain the situation and suggest solutions.",
            "Have you ever had a holiday experience that did not go as planned? Tell me about it.",
            "How have holiday traditions and celebrations changed over the past few decades?",
            "What are some issues or concerns related to national holidays or cultural festivals today?"
        )));

        // ── 패션/의류 (FASHION) ──────────────────────────────────────────────
        questionSetRepository.save(buildSet("패션-세트1", SurveyTopic.FASHION, q(
            "Describe your personal style. What kinds of clothes do you prefer to wear?",
            "How often do you shop for clothes and what is your typical shopping routine?",
            "Tell me about something you bought recently to wear. What was it and why did you choose it?",
            "Describe the most memorable fashion-related experience you have ever had.",
            "I also pay attention to fashion and clothing. Ask me 3 or 4 questions about my style.",
            "You are looking for a specific item of clothing. Call a store and ask 3 or 4 questions about what is available.",
            "The item you wanted is not in your size or is out of stock. Explain the situation and ask about alternatives.",
            "Have you ever had a problem related to buying or wearing clothes? Tell me about it.",
            "How has fashion and clothing culture changed over the past few decades?",
            "What are some issues related to fast fashion, sustainability, or the clothing industry today?"
        )));

        // ── 동네/이웃 (NEIGHBORHOOD) ─────────────────────────────────────────
        questionSetRepository.save(buildSet("동네-세트1", SurveyTopic.NEIGHBORHOOD, q(
            "Describe the neighborhood where you live. What is it like and what makes it special?",
            "What do you usually do in your neighborhood? Describe your typical activities nearby.",
            "Tell me about something that happened recently in your neighborhood.",
            "Describe the most memorable experience you have had in your neighborhood.",
            "I also have a neighborhood I enjoy. Ask me 3 or 4 questions about where I live.",
            "You want to report a problem in your neighborhood. Call the local office and ask 3 or 4 questions about the process.",
            "A neighbor is causing a disturbance. Explain the situation and suggest ways to resolve it.",
            "Have you ever had a conflict or issue with a neighbor or in your community? Tell me about it.",
            "How has your neighborhood changed over the years? Compare the past and the present.",
            "What are some issues or concerns related to urban neighborhoods and communities today?"
        )));

        // ── 인터넷/기술 (TECHNOLOGY_INTERNET) ───────────────────────────────
        questionSetRepository.save(buildSet("인터넷기술-세트1", SurveyTopic.TECHNOLOGY_INTERNET, q(
            "Describe how you use the internet in your daily life. What do you mainly use it for?",
            "How much time do you spend online each day and what are your typical online activities?",
            "Tell me about a useful or interesting thing you did online recently.",
            "Describe the most memorable experience you have had related to technology or the internet.",
            "I also use the internet a lot. Ask me 3 or 4 questions about my online habits.",
            "Your internet service is not working properly. Call the provider and ask 3 or 4 questions to get it fixed.",
            "Your device crashes and you lose important data. Explain the situation and suggest ways to recover.",
            "Have you ever had a serious problem with a device or internet service? Tell me what happened.",
            "How has the internet changed the way people live and work over the past 20 years?",
            "What are some concerns related to internet use, privacy, or technology dependence today?"
        )));

        System.out.println("[DataInitializer] 돌발 주제 10개 데이터 초기화 완료.");
    }

    private void populateSurpriseDataV2() {

        // ── 핸드폰 (MOBILE_PHONE) ─────────────────────────────────────────────
        questionSetRepository.save(buildSet("핸드폰-세트1", SurveyTopic.MOBILE_PHONE, q(
            "Describe the mobile phone you currently use. What model is it and what features do you like most?",
            "How do you use your mobile phone on a daily basis? What do you mainly use it for?",
            "Tell me about a recent experience you had with your mobile phone. Was there anything memorable?",
            "Describe the most memorable experience you have had related to a mobile phone.",
            "I also use a smartphone regularly. Ask me 3 or 4 questions about how I use my phone.",
            "Your mobile phone is broken and you need to get it repaired. Call the service center and ask 3 or 4 questions about the repair process.",
            "You find out the repair will take longer than expected and you need your phone urgently. Explain the situation and suggest alternatives.",
            "Have you ever had a problem with your mobile phone or had to get it repaired? Tell me about it.",
            "How have mobile phones changed over the past 10 to 20 years? Compare the past and the present.",
            "What are some issues or concerns related to mobile phone use and smartphone culture today?"
        )));

        // ── 음식 (FOOD) ───────────────────────────────────────────────────────
        questionSetRepository.save(buildSet("음식-세트1", SurveyTopic.FOOD, q(
            "Describe your favorite type of food. What do you enjoy eating and why?",
            "What do you usually eat on a typical day? Describe your eating habits and routine.",
            "Tell me about a meal you had recently that was particularly good or interesting.",
            "Describe the most memorable food or dining experience you have ever had.",
            "I also enjoy good food. Ask me 3 or 4 questions about my food preferences and eating habits.",
            "You want to order a special meal for a gathering. Call a restaurant and ask 3 or 4 questions about the menu and service.",
            "The food you ordered arrived incorrectly or was not what you expected. Explain the problem and suggest a solution.",
            "Have you ever had a bad or unexpected experience related to food? Tell me about it.",
            "How has food culture and eating habits changed in your country over the past few decades?",
            "What are some issues related to food safety, nutrition, or dietary trends today?"
        )));

        // ── 가구 (FURNITURE) ──────────────────────────────────────────────────
        questionSetRepository.save(buildSet("가구-세트1", SurveyTopic.FURNITURE, q(
            "Describe the furniture in your home. What pieces do you have and what do you like about them?",
            "How do you usually choose furniture for your home? Describe your preferences and process.",
            "Tell me about a piece of furniture you bought or changed recently. Why did you make that change?",
            "Describe the most memorable experience you have had related to buying or using furniture.",
            "I also recently got new furniture. Ask me 3 or 4 questions about my home and furniture choices.",
            "You want to buy new furniture. Call a furniture store and ask 3 or 4 questions about available items and delivery.",
            "The furniture you ordered arrived damaged or was the wrong item. Explain the problem and suggest ways to resolve it.",
            "Have you ever had a problem when buying furniture or home items? Tell me about it.",
            "How have furniture styles and home interior trends changed over the years?",
            "What are some issues related to furniture, interior design, or sustainable home products today?"
        )));

        // ── 지형 (GEOGRAPHY) ─────────────────────────────────────────────────
        questionSetRepository.save(buildSet("지형-세트1", SurveyTopic.GEOGRAPHY, q(
            "Describe the geography or landscape of the area where you live. What is it like?",
            "How does the geography of your region affect your daily life and activities?",
            "Tell me about a place with interesting or beautiful geography that you have visited recently.",
            "Describe the most memorable geographical feature or landscape you have ever seen.",
            "I am also curious about geography. Ask me 3 or 4 questions about the geography of my area.",
            "You are planning a trip to a region with unique geography. Call a travel agency and ask 3 or 4 questions about the area.",
            "You arrive at your destination but find the conditions are very different from what you expected. Explain and suggest alternatives.",
            "Have you ever been surprised by the geography or landscape of a place you visited? Tell me about it.",
            "How has the geography or environment of your area changed over time due to development or climate?",
            "What are some issues related to geography, land use, or environmental changes in your country today?"
        )));

        // ── 약속 (APPOINTMENT) ────────────────────────────────────────────────
        questionSetRepository.save(buildSet("약속-세트1", SurveyTopic.APPOINTMENT, q(
            "Describe how you typically manage your schedule and appointments. What tools or methods do you use?",
            "How often do you make plans with friends or colleagues? What kinds of appointments do you usually have?",
            "Tell me about a recent plan or appointment you had. How did it go?",
            "Describe the most memorable appointment or gathering you have ever attended.",
            "I also manage a busy schedule. Ask me 3 or 4 questions about how I handle appointments and plans.",
            "You need to schedule a meeting with someone. Call them and ask 3 or 4 questions to arrange a suitable time and place.",
            "Something came up and you need to cancel or change your plans at the last minute. Explain the situation and suggest alternatives.",
            "Have you ever had to cancel an important appointment? Tell me what happened and how you dealt with it.",
            "How has the way people make and manage appointments changed with technology?",
            "What are some challenges related to time management and keeping commitments in modern life?"
        )));

        // ── 파티 (PARTY) ──────────────────────────────────────────────────────
        questionSetRepository.save(buildSet("파티-세트1", SurveyTopic.PARTY, q(
            "Describe the kinds of parties or gatherings you enjoy attending. What are they like?",
            "How often do you attend parties or social gatherings? What is your usual experience?",
            "Tell me about a party or gathering you attended recently. What was it like?",
            "Describe the most memorable party or celebration you have ever been to.",
            "I also enjoy attending parties. Ask me 3 or 4 questions about my social life and gatherings.",
            "You are organizing a party. Call a venue or catering service and ask 3 or 4 questions about arrangements.",
            "Something goes wrong with your party plans at the last minute, such as a venue cancellation. Explain and suggest solutions.",
            "Have you ever had a problem organizing or attending a party? Tell me about it.",
            "How have parties and social gatherings changed over the past few decades?",
            "What are some concerns related to social events or party culture in modern society?"
        )));

        // ── 다이어트 (DIET) ───────────────────────────────────────────────────
        questionSetRepository.save(buildSet("다이어트-세트1", SurveyTopic.DIET, q(
            "Describe your current diet or eating habits. What do you usually eat to stay healthy?",
            "How do you manage your diet on a daily basis? Do you follow any specific guidelines?",
            "Tell me about a recent change you made to your diet or health routine. How did it go?",
            "Describe the most memorable experience you have had related to dieting or changing your eating habits.",
            "I am also trying to maintain a healthy diet. Ask me 3 or 4 questions about my habits.",
            "You want to consult a nutritionist or dietitian. Call and ask 3 or 4 questions about their services and advice.",
            "You follow a diet plan but find it very difficult to stick to. Explain the challenges and suggest ways to overcome them.",
            "Have you ever tried a specific diet or made a major change to your eating habits? Tell me about your experience.",
            "How have attitudes toward dieting and healthy eating changed over the years?",
            "What are some issues related to diet culture, body image, or food trends in society today?"
        )));

        // ── 가전제품 (HOME_APPLIANCE) ─────────────────────────────────────────
        questionSetRepository.save(buildSet("가전제품-세트1", SurveyTopic.HOME_APPLIANCE, q(
            "Describe the home appliances you use most often. What are they and why are they important to you?",
            "How do you use home appliances in your daily routine? Describe how they help you.",
            "Tell me about a home appliance you bought or started using recently. How has it been?",
            "Describe the most memorable experience you have had related to a home appliance.",
            "I also rely on home appliances every day. Ask me 3 or 4 questions about the appliances I use.",
            "One of your home appliances has broken down. Call a repair service and ask 3 or 4 questions about getting it fixed.",
            "The technician says the appliance cannot be repaired and needs to be replaced. Explain the situation and suggest solutions.",
            "Have you ever had a major home appliance break down at an inconvenient time? Tell me about it.",
            "How have home appliances changed and improved over the past few decades?",
            "What are some issues related to energy use, smart appliances, or home technology today?"
        )));

        // ── 여가활동 (LEISURE_GENERAL) ────────────────────────────────────────
        questionSetRepository.save(buildSet("여가활동-세트1", SurveyTopic.LEISURE_GENERAL, q(
            "Describe how you typically spend your free time. What leisure activities do you enjoy?",
            "How often do you have free time and what do you usually do to relax or have fun?",
            "Tell me about a leisure activity you enjoyed recently. What did you do?",
            "Describe the most memorable leisure experience you have ever had.",
            "I also enjoy spending my free time doing various activities. Ask me 3 or 4 questions about my hobbies.",
            "You want to join a leisure club or activity group. Call and ask 3 or 4 questions about how to get involved.",
            "The activity you planned for your day off was cancelled or unavailable. Explain the situation and suggest alternatives.",
            "Have you ever had a free time plan fall through? Tell me what happened and what you did instead.",
            "How have people's leisure activities and hobbies changed over the past few decades?",
            "What are some issues related to work-life balance and the importance of leisure in modern life?"
        )));

        System.out.println("[DataInitializer] 돌발 주제 추가 9개 초기화 완료.");
    }

    private void populateSurpriseDataV3() {

        // ── 기술 (TECHNOLOGY) ─────────────────────────────────────────────────
        questionSetRepository.save(buildSet("기술-세트1", SurveyTopic.TECHNOLOGY, q(
            "Describe a piece of technology you use regularly. What is it and why is it important to you?",
            "How does technology play a role in your daily life? What do you use most often?",
            "Tell me about a new technology you started using recently. How has it changed things for you?",
            "Describe the most impressive or memorable technology-related experience you have had.",
            "I also use various technologies in my life. Ask me 3 or 4 questions about how I use technology.",
            "You are having a problem with a device or piece of technology. Call the support center and ask 3 or 4 questions to get help.",
            "The technical support cannot solve your problem immediately and it is urgent. Explain the situation and suggest alternatives.",
            "Have you ever had a serious problem with technology that affected your daily life? Tell me about it.",
            "How has technology changed the way people live and work over the past 20 years?",
            "What are some concerns about technology, such as privacy, addiction, or job displacement, that people face today?"
        )));

        // ── 건강 (HEALTH_WELLNESS) ────────────────────────────────────────────
        questionSetRepository.save(buildSet("건강-세트1", SurveyTopic.HEALTH_WELLNESS, q(
            "Describe how you take care of your health. What habits do you follow to stay well?",
            "What does your health routine look like on a typical day? What do you do to maintain good health?",
            "Tell me about a recent experience related to your health or wellness. What happened?",
            "Describe the most memorable experience you have had related to health or taking care of yourself.",
            "I also try to maintain a healthy lifestyle. Ask me 3 or 4 questions about my health habits.",
            "You want to join a health program or consult a specialist. Call and ask 3 or 4 questions about the options available.",
            "You are following a health program but find it difficult to maintain. Explain the challenges and ask for advice.",
            "Have you ever made a significant change to improve your health? Tell me about the experience.",
            "How have people's attitudes toward health and wellness changed over the past few decades?",
            "What are some major health issues or concerns facing society today?"
        )));

        // ── 산업 (INDUSTRY) ───────────────────────────────────────────────────
        questionSetRepository.save(buildSet("산업-세트1", SurveyTopic.INDUSTRY, q(
            "Describe the main industries in your country or region. What do you know about them?",
            "How do the major industries in your country affect everyday life and the economy?",
            "Tell me about a recent development or change in an industry that caught your attention.",
            "Describe the most memorable experience or moment you have had related to work or industry.",
            "I am also curious about industries and the economy. Ask me 3 or 4 questions about my perspective.",
            "You are interested in learning about a particular industry. Call a company and ask 3 or 4 questions about how they operate.",
            "You apply for a job in an industry but find the working conditions are very different from what you expected. Explain and suggest solutions.",
            "Have you ever had an interesting or surprising experience related to a job or industry? Tell me about it.",
            "How have the major industries in your country changed over the past few decades?",
            "What are some challenges or issues facing industries in your country today, such as automation or environmental concerns?"
        )));

        // ── 재활용 (RECYCLING) ────────────────────────────────────────────────
        questionSetRepository.save(buildSet("재활용-세트1", SurveyTopic.RECYCLING, q(
            "Describe how recycling is done in your home or community. What systems are in place?",
            "What do you usually do to recycle or reduce waste in your daily life?",
            "Tell me about a recent experience you had related to recycling or being environmentally conscious.",
            "Describe the most memorable experience you have had related to environmental issues or recycling.",
            "I also try to be environmentally conscious. Ask me 3 or 4 questions about my recycling habits.",
            "You want to find out more about recycling programs in your area. Call the local office and ask 3 or 4 questions.",
            "Your neighborhood does not have a proper recycling system and it is causing problems. Explain the issue and suggest solutions.",
            "Have you ever taken action to help the environment or encountered a major environmental issue? Tell me about it.",
            "How have recycling and environmental awareness changed over the past few decades?",
            "What are some major environmental challenges, such as waste management or climate change, that your country faces today?"
        )));

        System.out.println("[DataInitializer] 돌발 주제 기술/건강/산업/재활용 초기화 완료.");
    }
}
