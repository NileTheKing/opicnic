import http from 'k6/http';
import { check, sleep } from 'k6';

// 가상 사용자가 1초에 한 번씩 OPIc 풀 사이클을 시뮬레이션합니다.
const binFile = open('test_audio.wav', 'b'); 

export default function () {
    // 1단계: 문제 세트 요청
    let comboUrl = `http://localhost:8080/practice/combo?topic=MOVIE_WATCHING&difficulty=LEVEL_3`;
    let comboRes = http.get(comboUrl);
    
    check(comboRes, {
        'GET questions success (DB hit)': (r) => r.status === 200,
    });

    // 2단계: 피드백 요청 (1MB 파일 사용)
    let feedbackUrl = 'http://localhost:8080/practice/combo/feedback';
    
    let data = {
        'files': http.file(binFile, 'test_audio.wav'),
        'questions[0].id': '1',
        'questions[0].content': 'Tell me about the last movie you watched.',
        'questions[0].topic': 'MOVIE_WATCHING',
        'questions[0].difficulty': 'LEVEL_3',
    };

    let params = {
        redirects: 0,
    };

    let res = http.post(feedbackUrl, data, params);

    check(res, {
        'POST feedback success': (r) => r.status === 200,
        'has feedback content': (r) => r.body && (r.body.includes('feedback') || r.body.includes('AL')),
    });

    sleep(1); 
}
