# 멀티모달(음성을 LLM에 바로) vs 지금 방식(Whisper STT → 텍스트 채점) 비교. 검증용 기준 답(anchors-test.json)을 macOS TTS로 읽혀
# 음성을 만들고 세 가지로 채점한다. 운영 프롬프트(prompts/scoring.md)와 스키마, 등급 규칙(run.py의 --prompt-file 경로)을 그대로 쓴다.
#   text : 원문 텍스트 그대로 채점(기준선)
#   stt  : TTS 음성 → Groq whisper-large-v3 → 텍스트 채점 (지금 운영 경로)
#   audio: TTS 음성을 Gemini에 직접 넣고, 들린 그대로 받아쓴 transcript와 채점을 한 번에 받는다
# 정답 받아쓰기는 원문이라 문법 오류가 보존되는지를 단어 오류율(WER)로 잰다. TTS는 발음이 깨끗하므로 억양·말더듬 강건성은 재지 않는다.
# 사용: set -a; . ../../../.env; set +a; python3 multimodal_check.py  → results-multimodal-test.jsonl (이어 돌리면 끝난 건 건너뜀)
import base64, json, os, re, subprocess, sys, tempfile, time, urllib.request, urllib.error, uuid
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from run import HERE, ROOT, preset, fluency, grade, word_cap, ORDER

OUT = os.path.join(HERE, "results-multimodal-test.jsonl")
AUDIO_DIR = os.environ.get("AUDIO_DIR") or tempfile.mkdtemp(prefix="mm-audio-")
GAP = 5   # 호출 간격(초). Gemini 무료 분당 15
AUDIO_NOTE = ("\n\n# 음성 입력\n사용자 응답은 음성으로 주어진다. 먼저 transcript에 들린 그대로 받아 적어라. 문법 오류, 어색한 표현, 반복, 머뭇거림을 "
              "고치거나 다듬지 말고 말한 그대로 적는다. 그 transcript를 사용자 응답으로 보고 위 기준대로 채점한다.")


def wer(ref, hyp):
    norm = lambda s: re.sub(r"[^a-z0-9' ]", " ", s.lower()).split()
    r, h = norm(ref), norm(hyp)
    d = list(range(len(h) + 1))
    for i in range(1, len(r) + 1):
        prev, d[0] = d[0], i
        for j in range(1, len(h) + 1):
            cur = min(d[j] + 1, d[j - 1] + 1, prev + (r[i - 1] != h[j - 1]))
            prev, d[j] = d[j], cur
    return round(d[len(h)] / max(1, len(r)), 3)


def tts(key, text):
    wav = os.path.join(AUDIO_DIR, key + ".wav")
    if not os.path.exists(wav):
        aiff = wav[:-4] + ".aiff"
        subprocess.run(["say", "-v", "Samantha", "-o", aiff, text], check=True)
        subprocess.run(["afconvert", "-f", "WAVE", "-d", "LEI16@16000", "-c", "1", aiff, wav], check=True)
        os.remove(aiff)
    return wav


def post(req):
    while True:
        try:
            with urllib.request.urlopen(req, timeout=180) as r:
                return json.load(r)
        except urllib.error.HTTPError as e:
            if e.code in (429, 503):
                wait = float(e.headers.get("retry-after") or 30)
                print(f"   {e.code} → {wait:.0f}s 대기", flush=True)
                time.sleep(wait + 1)
                continue
            raise RuntimeError(f"{e.code} {e.read()[:300]}")


def whisper(wav):
    boundary = uuid.uuid4().hex
    parts = [f'--{boundary}\r\nContent-Disposition: form-data; name="model"\r\n\r\nwhisper-large-v3\r\n'.encode(),
             f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="a.wav"\r\nContent-Type: audio/wav\r\n\r\n'.encode()
             + open(wav, "rb").read() + b"\r\n", f"--{boundary}--\r\n".encode()]
    req = urllib.request.Request("https://api.groq.com/openai/v1/audio/transcriptions", b"".join(parts), {
        "Authorization": "Bearer " + os.environ["GROQ_API_KEY"], "Content-Type": f"multipart/form-data; boundary={boundary}",
        "User-Agent": "opicnic-calibration/1.0"})
    return post(req)["text"].strip()


def chat(conf, system, content, schema):
    body = {"model": conf["model"], "temperature": 0.0, "max_tokens": 4000,
            "response_format": {"type": "json_schema", "json_schema": {"name": "scoring", "strict": True, "schema": schema}},
            "messages": [{"role": "system", "content": system}, {"role": "user", "content": content}]}
    req = urllib.request.Request(conf["url"], json.dumps(body).encode(), {
        "Authorization": "Bearer " + conf["key"], "Content-Type": "application/json", "User-Agent": "opicnic-calibration/1.0"})
    t0 = time.time()
    resp = post(req)
    return json.loads(resp["choices"][0]["message"]["content"]), resp.get("usage"), round(time.time() - t0, 1)


def scored(fb, answer_text):
    sc = {k: fb.get(k + "Score") for k in ("mainPoint", "expression", "accuracy", "content")}
    sc = {k: int(v) if str(v).strip().lstrip("-").isdigit() else None for k, v in sc.items()}
    g, avg = grade([sc["mainPoint"], sc["expression"], sc["accuracy"], fluency(answer_text), sc["content"]])
    llm = str(fb.get("level", "")).strip().upper()
    words = len(answer_text.split())
    return {"scores": sc, "score_grade": g, "avg": round(avg, 2), "llm_level": llm,
            "grade": word_cap(llm, words) if llm in ORDER else None, "words": words}


def main():
    conf = preset("gemini")
    data = json.load(open(os.path.join(HERE, "anchors-test.json")))
    system = open(os.path.join(ROOT, "src/main/resources/prompts/scoring.md")).read()
    schema = json.load(open(os.path.join(ROOT, "src/main/resources/prompts/scoring-schema.json")))
    audio_schema = json.loads(json.dumps(schema))
    audio_schema["properties"] = {"transcript": {"type": "string"}, **audio_schema["properties"]}
    audio_schema["required"] = ["transcript"] + audio_schema["required"]
    done = set()
    if os.path.exists(OUT):
        done = {(r["q"], r["level"], r["mode"]) for r in map(json.loads, open(OUT)) if "error" not in r}
    for a in data["anchors"]:
        q = data["questions"][a["q"]]
        head = f"문제 유형: {q['type']}\n질문: {q['content']}\n"
        key = f"{a['q']}-{a['level']}"
        for mode in ("text", "stt", "audio"):
            if (a["q"], a["level"], mode) in done:
                continue
            rec = {"q": a["q"], "level": a["level"], "label": a["label"], "mode": mode, "model": conf["model"]}
            try:
                if mode == "text":
                    transcript = a["text"]
                    fb, usage, secs = chat(conf, system, head + "사용자 응답: " + transcript, schema)
                elif mode == "stt":
                    transcript = whisper(tts(key, a["text"]))
                    fb, usage, secs = chat(conf, system, head + "사용자 응답: " + transcript, schema)
                else:
                    audio = base64.b64encode(open(tts(key, a["text"]), "rb").read()).decode()
                    fb, usage, secs = chat(conf, system + AUDIO_NOTE, [
                        {"type": "text", "text": head + "사용자 응답: (음성)"},
                        {"type": "input_audio", "input_audio": {"data": audio, "format": "wav"}}], audio_schema)
                    transcript = str(fb.get("transcript", ""))
                rec |= {"transcript": transcript, "wer": wer(a["text"], transcript), "usage": usage, "secs": secs} | scored(fb, transcript)
            except Exception as e:
                rec |= {"error": str(e)[:300]}
            with open(OUT, "a") as f:
                f.write(json.dumps(rec, ensure_ascii=False) + "\n")
            print(f"{key:12} {mode:5} → {rec.get('grade', rec.get('error'))} wer={rec.get('wer')} {rec.get('secs', '')}s", flush=True)
            time.sleep(GAP)


if __name__ == "__main__":
    main()
