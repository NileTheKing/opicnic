# results-*.jsonl을 같은 기준으로 요약한다: 등급 일치 / ±1단계 / 2단계 이상 빗나감 / 순서 뒤집힘 / JSON 실패 / 지어낸 인용 / 응답 시간
import json, os, re, glob, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ORDER = ["NH", "IL", "IM1", "IM2", "IM3", "IH", "AL"]
ANCHORS = [a for f in ("anchors.json", "anchors-test.json") for a in json.load(open(os.path.join(HERE, f)))["anchors"]]
A = {(a["q"], a["level"]): a["text"] for a in ANCHORS}
# level은 작성 당시 의도(기준점 id), label은 실제 OPIc 기준으로 다시 붙인 정답(2026-10-08). --label이면 label과 비교
USE_LABEL = "--label" in sys.argv
TARGET = {(a["q"], a["level"]): a.get("label", a["level"]) if USE_LABEL else a["level"] for a in ANCHORS}
norm = lambda s: re.sub(r"[^a-z0-9 ]", "", (s or "").lower().replace("...", " ")).split()


def grounded(quote, text):
    # 인용이 "…"로 잘린 경우가 있어 앞 6단어가 원문에 있으면 인정
    q, t = norm(quote)[:6], " ".join(norm(text))
    return " ".join(q) in t


for path in sorted(glob.glob(os.path.join(HERE, "results-*.jsonl"))):
    if len(sys.argv) > 1 and not any(t in os.path.basename(path) for t in sys.argv[1:] if not t.startswith("--")):
        continue
    rows = [json.loads(l) for l in open(path)]
    for model in sorted({r["model"] for r in rows}):
        rs = [r for r in rows if r["model"] == model]
        ok = [r for r in rs if r.get("grade")]
        diff = [ORDER.index(r["grade"]) - ORDER.index(TARGET[(r["q"], r["level"])]) for r in ok]
        inversions = 0
        for q in {r["q"] for r in ok}:
            seq = sorted((ORDER.index(TARGET[(r["q"], r["level"])]), ORDER.index(r["grade"])) for r in ok if r["q"] == q)
            inversions += sum(1 for (_, a), (_, b) in zip(seq, seq[1:]) if b < a)
        quotes = bad = 0
        for r in ok:
            fb = json.loads(r["raw"])
            for k in ("mainPointQuote", "expressionQuote", "accuracyQuote", "contentQuote", "improvementsQuote"):
                if fb.get(k):
                    quotes += 1
                    bad += not grounded(fb[k], A[(r["q"], r["level"])])
        print(f"\n## {os.path.basename(path)} · {model}  ({len(rs)}건)")
        print(f"정확히 일치 {sum(d == 0 for d in diff)}/{len(ok)} · ±1단계 {sum(abs(d) <= 1 for d in diff)}/{len(ok)} · "
              f"2단계 이상 빗나감 {sum(abs(d) >= 2 for d in diff)} · 평균 치우침 {sum(diff) / len(diff):+.2f}단계")
        print(f"순서 뒤집힘 {inversions} · JSON/호출 실패 {len(rs) - len(ok)} · 원문에 없는 인용 {bad}/{quotes} · "
              f"평균 {sum(r.get('secs', 0) for r in ok) / len(ok):.1f}s")
        print("| 의도 | " + " | ".join(sorted({r['q'] for r in ok})) + " |")
        for lv in ORDER:
            cells = [next((f"{r['grade']} ({r['avg']})" + (f" ←{TARGET[(q, lv)]}" if TARGET[(q, lv)] != lv else "") for r in ok if r["q"] == q and r["level"] == lv), "-") for q in sorted({r['q'] for r in ok})]
            print(f"| {lv} | " + " | ".join(cells) + " |")
