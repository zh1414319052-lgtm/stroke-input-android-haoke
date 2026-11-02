# Compare offline Chinese ASR models (sherpa-onnx) on FLEURS Mandarin and Cantonese test sentences:
# character error rate after normalisation, real-time factor with 2 threads (phone-like), peak memory.
import csv
import os
import random
import re
import sys
import tarfile
import time
import unicodedata
from pathlib import Path

import numpy as np
import psutil
import sherpa_onnx
import soundfile as sf

HERE = Path(__file__).parent
DL = HERE / "asr-dl"
N_PER_LANGUAGE = int(os.environ.get("N", "100"))
THREADS = 2

try:
    from opencc import OpenCC
    TO_SIMPLIFIED = OpenCC("t2s").convert
except Exception:  # fall back: no conversion
    TO_SIMPLIFIED = lambda s: s

DIGITS = str.maketrans("0123456789", "零一二三四五六七八九")


def normalise(text):
    text = unicodedata.normalize("NFKC", text)
    text = TO_SIMPLIFIED(text).lower()
    text = text.translate(DIGITS)
    text = re.sub(r"[^\w]", "", text)  # drop punctuation and spaces
    return text.replace("_", "")


def edit_distance(a, b):
    previous = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        current = [i]
        for j, cb in enumerate(b, 1):
            current.append(min(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + (ca != cb)))
        previous = current
    return previous[-1]


def load_fleurs(language):
    folder = DL / f"fleurs_{language}"
    audio_dir = folder / "test"
    if not audio_dir.is_dir():
        with tarfile.open(folder / "test.tar.gz") as archive:
            archive.extractall(folder)
    rows = []
    with open(folder / "test.tsv", encoding="utf-8") as handle:
        for row in csv.reader(handle, delimiter="\t", quoting=csv.QUOTE_NONE):
            if len(row) >= 3 and (audio_dir / row[1]).exists():
                rows.append((audio_dir / row[1], row[2]))
    unique = {}
    for path, text in rows:
        unique.setdefault(text, path)  # FLEURS repeats sentences by several speakers
    items = sorted((path, text) for text, path in unique.items())
    random.Random(2026).shuffle(items)
    return items[:N_PER_LANGUAGE]


def extract(name):
    folder = DL / name
    if not folder.is_dir():
        with tarfile.open(DL / f"{name}.tar.bz2") as archive:
            archive.extractall(DL)
    return folder


def build(kind, folder):
    tokens = str(folder / "tokens.txt")
    common = dict(num_threads=THREADS, provider="cpu", debug=False)
    if kind == "sense_voice":
        return sherpa_onnx.OfflineRecognizer.from_sense_voice(
            model=str(next(folder.glob("model*.onnx"))), tokens=tokens, use_itn=True, language="auto", **common)
    if kind == "fire_red_asr2":
        return sherpa_onnx.OfflineRecognizer.from_fire_red_asr(
            encoder=str(next(folder.glob("encoder*.onnx"))), decoder=str(next(folder.glob("decoder*.onnx"))),
            tokens=tokens, **common)
    if kind == "fire_red_asr2_ctc":
        return sherpa_onnx.OfflineRecognizer.from_fire_red_asr_ctc(
            model=str(next(folder.glob("model*.onnx"))), tokens=tokens, **common)
    if kind == "qwen3_asr":
        return sherpa_onnx.OfflineRecognizer.from_qwen3_asr(
            conv_frontend=str(folder / "conv_frontend.onnx"),
            encoder=str(next(folder.glob("encoder*.onnx"))),
            decoder=str(next(folder.glob("decoder*.onnx"))),
            tokenizer=str(folder / "tokenizer"),
            num_threads=THREADS, provider="cpu", debug=False)
    raise ValueError(kind)


def evaluate(label, kind, name, datasets):
    folder = extract(name)
    process = psutil.Process()
    before = process.memory_info().rss
    started = time.perf_counter()
    try:
        recogniser = build(kind, folder)
    except Exception as exception:
        print(f"{label}: cannot load ({exception})")
        return
    load_seconds = time.perf_counter() - started
    load_memory = (process.memory_info().rss - before) / 1e6
    model_size = sum(p.stat().st_size for p in folder.rglob("*") if p.is_file() and "test_wav" not in str(p)) / 1e6
    line = f"{label:22s} files {model_size:6.0f} MB | load {load_seconds:4.1f}s, +{load_memory:5.0f} MB RAM"
    for language, items in datasets.items():
        errors = characters = 0
        audio_seconds = decode_seconds = 0.0
        examples = []
        for path, reference in items:
            samples, rate = sf.read(str(path), dtype="float32")
            if samples.ndim > 1:
                samples = samples.mean(axis=1)
            stream = recogniser.create_stream()
            stream.accept_waveform(rate, samples)
            t = time.perf_counter()
            recogniser.decode_stream(stream)
            decode_seconds += time.perf_counter() - t
            audio_seconds += len(samples) / rate
            hypothesis = stream.result.text
            ref, hyp = normalise(reference), normalise(hypothesis)
            errors += edit_distance(ref, hyp)
            characters += len(ref)
            if len(examples) < 2:
                examples.append(f"{reference[:24]} => {hypothesis[:24]}")
        line += f" | {language}: CER {errors / characters:6.2%} RTF {decode_seconds / audio_seconds:5.2f}"
        print(f"   {label} {language} e.g. {' ; '.join(examples)}")
    peak = (process.memory_info().rss - before) / 1e6
    print(line + f" | RAM after decoding +{peak:5.0f} MB", flush=True)


def main():
    datasets = {"Mandarin": load_fleurs("cmn_hans_cn"), "Cantonese": load_fleurs("yue_hant_hk")}
    print({k: len(v) for k, v in datasets.items()}, "sentences;", "threads", THREADS, flush=True)
    models = [
        ("SenseVoice-Small int8", "sense_voice", "sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2025-09-09"),
        ("FireRedASR2-CTC int8", "fire_red_asr2_ctc", "sherpa-onnx-fire-red-asr2-ctc-zh_en-int8-2026-02-25"),
        ("FireRedASR2-AED int8", "fire_red_asr2", "sherpa-onnx-fire-red-asr2-zh_en-int8-2026-02-26"),
        ("Qwen3-ASR-0.6B int8", "qwen3_asr", "sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25"),
    ]
    only = set(sys.argv[1:])
    for label, kind, name in models:
        if only and kind not in only:
            continue
        if not (DL / f"{name}.tar.bz2").exists() and not (DL / name).is_dir():
            print(f"{label}: not downloaded")
            continue
        evaluate(label, kind, name, datasets)


if __name__ == "__main__":
    main()
