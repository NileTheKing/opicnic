package com.opicnic.opicnic.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 화면용으로 피드백 텍스트를 쪼갠다. 저장 형식은 FeedbackService.reassemble이 만든 "진단\n예) '원문' -> '고친 문장'" 한 덩어리라,
// 템플릿(fragments/feedback-parts.html)에서 T(...)로 불러 진단 / 내 문장 / 바꾼 문장을 따로 그린다.
public final class FeedbackText {

    private static final Pattern EXAMPLE = Pattern.compile("(?s)^(.*?)\\s*예\\)\\s*'(.+)'\\s*->\\s*'(.+)'\\s*$");
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!?])\\s+(?=[A-Z\"'])");
    private static final int SENTENCES_PER_PARAGRAPH = 3;

    private FeedbackText() {}

    public record Fix(String diagnosis, String before, String after) {
        public boolean hasExample() { return before != null && after != null; }
    }

    // 예시가 없거나 형식이 다르면 전체를 진단으로 둔다 — 예전 기록도 그대로 보인다
    public static Fix fix(String text) {
        if (text == null || text.isBlank()) return new Fix(null, null, null);
        Matcher m = EXAMPLE.matcher(text.trim());
        if (!m.matches()) return new Fix(text.trim(), null, null);
        String diagnosis = m.group(1).trim();
        return new Fix(diagnosis.isEmpty() ? null : diagnosis, m.group(2).trim(), m.group(3).trim());
    }

    public record Segment(String text, boolean added) {}

    // 바꾼 문장에서 원문에 없던 단어만 표시한다(단어 단위 LCS). 공백은 앞 단어에 붙여 그대로 이어 붙이면 원문이 된다
    public static List<Segment> highlight(String before, String after) {
        if (after == null) return List.of();
        String[] a = norm(before).split(" ");
        String[] words = after.trim().split("\\s+");
        String[] b = new String[words.length];
        for (int i = 0; i < words.length; i++) b[i] = norm(words[i]);

        int[][] lcs = new int[a.length + 1][b.length + 1];
        for (int i = a.length - 1; i >= 0; i--)
            for (int j = b.length - 1; j >= 0; j--)
                lcs[i][j] = !a[i].isEmpty() && a[i].equals(b[j]) ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);

        List<Segment> out = new ArrayList<>();
        int i = 0;
        for (int j = 0; j < b.length; j++) {
            boolean kept = b[j].isEmpty();  // 기호뿐인 토큰(—)은 강조하지 않는다
            if (!kept) {
                while (i < a.length && lcs[i][j] == lcs[i + 1][j]) i++;
                if (i < a.length && !a[i].isEmpty() && a[i].equals(b[j])) { kept = true; i++; }
            }
            String text = words[j] + (j < b.length - 1 ? " " : "");
            Segment last = out.isEmpty() ? null : out.get(out.size() - 1);
            if (last != null && last.added() == !kept) out.set(out.size() - 1, new Segment(last.text() + text, !kept));
            else out.add(new Segment(text, !kept));
        }
        return out;
    }

    private static String norm(String s) {
        return s == null ? "" : s.toLowerCase().replaceAll("[^a-z0-9'\\s]", "").trim().replaceAll("\\s+", " ");
    }

    // 모범 답변 한 덩어리를 세 문장씩 문단으로 나눈다
    public static List<String> paragraphs(String text) {
        if (text == null || text.isBlank()) return List.of();
        String[] sentences = SENTENCE_END.split(text.trim());
        List<String> out = new ArrayList<>();
        for (int i = 0; i < sentences.length; i += SENTENCES_PER_PARAGRAPH) {
            out.add(String.join(" ", List.of(sentences).subList(i, Math.min(i + SENTENCES_PER_PARAGRAPH, sentences.length))));
        }
        return out;
    }
}
