# 모범 답안이 (1) 위 4개 항목 피드백의 고친 문장(Fix)을 반영하는지 (2) 사용자 답변 내용을 살리는지 (3) 길이 — results-*.jsonl을 읽는다
import json, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
A = {(a["q"], a["level"]): a["text"] for f in ("anchors.json", "anchors-test.json") for a in json.load(open(os.path.join(HERE, f)))["anchors"]}
STOP = set("a an the i my me we our is are was were be been it its this that and or but so to of in on at for with as by from very really just about there their they have has had do did not no yes um uh".split())
words = lambda s: [w for w in re.findall(r"[a-z']+", (s or "").lower()) if w not in STOP and len(w) > 2]


def check(path):
    rows = [r for r in map(json.loads, open(path)) if r.get("json_ok")]
    lens, fix_rates, keep_rates = [], [], []
    for r in rows:
        fb = json.loads(r["raw"]); model = set(words(fb.get("modelAnswer")))
        lens.append(len((fb.get("modelAnswer") or "").split()))
        fixes = [fb.get(k) for k in ("mainPointFix", "expressionFix", "accuracyFix", "contentFix") if fb.get(k)]
        # 고친 문장의 내용어 절반 이상이 모범 답안에 있으면 "반영"
        hit = [len(set(words(f)) & model) / max(len(set(words(f))), 1) >= 0.5 for f in fixes]
        if hit: fix_rates.append(sum(hit) / len(hit))
        user = set(words(A[(r["q"], r["level"])]))
        if user: keep_rates.append(len(user & model) / len(user))
    n = len(rows)
    print(f"{os.path.basename(path)}: {n}건 · 모범답안 평균 {sum(lens)/n:.0f}단어(최소 {min(lens)}, 최대 {max(lens)}) · "
          f"고친 문장 반영 {100*sum(fix_rates)/len(fix_rates):.0f}% · 사용자 내용어 유지 {100*sum(keep_rates)/len(keep_rates):.0f}%")


for p in sys.argv[1:]:
    check(os.path.join(HERE, p))
