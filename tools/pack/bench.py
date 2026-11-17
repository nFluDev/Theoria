#!/usr/bin/env python3
"""Faz 5 FPS taban ölçümü: tam paket, Vulkan (D19: OpenGL/Iris YOK). Gerçek bir oyun PENCERESİ açar — ekran kilitliyken çalışmaz.

    python3 -I tools/pack/bench.py sweep-full            # tam ekran 1080p, shader AÇIK, RD 12,16,24,32 (20 sn örnek)
    python3 -I tools/pack/bench.py sweep-noshader        # tam ekran 1080p, shader KAPALI, aynı RD'ler
    python3 -I tools/pack/bench.py windowed              # 1600x900 pencere, shader açık, RD 16,32
    python3 -I tools/pack/bench.py sweep-user            # S1 ile karşılaştırılabilir: 'Vulkan Optimization'ın grafik ayarları, RD 12,16,24
    python3 -I tools/pack/bench.py endurance --rd 24     # tam ekran, shader açık, 10 dk (heap/RSS/dakika-başı FPS)
    python3 -I tools/pack/bench.py status                # kilit durumu + son sonuçlar

Her koşu: make_instance.py (instance'ı yeniden yazar) → `prismlauncher --launch Theoria` (oyun kendi kapanır) → rapor
mods-src/theoria/build/dev-out/pack/fps_<etiket>.txt (RESULT/MEMORY satırları) + log özeti (backend, Vitrail/Complementary, ERROR sayısı).
Tam ekran koşuları ekranı işgal eder: kullanıcı başındayken ve istediğinde başlat. Kilitliyse (CGSSessionScreenIsLocked) hiç başlamaz.
"""
import argparse, json, pathlib, re, shutil, subprocess, sys, time

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / "mods-src/theoria/build/dev-out/pack"
INSTANCE = pathlib.Path.home() / "Library/Application Support/PrismLauncher/instances/Theoria/minecraft"
MAKE = ROOT / "tools/pack/make_instance.py"


def screen_locked():
    out = subprocess.run(["ioreg", "-n", "Root", "-d1", "-a"], capture_output=True, text=True).stdout
    return "CGSSessionScreenIsLocked" in out


def run(label, rd, shader, fullscreen, size="1600x900", sample=20, settle=300, extra=None, user_options=None):
    if screen_locked():
        raise SystemExit("Ekran kilitli — oyun penceresi sunulamaz (SDL_GL_SwapWindow takılır). Kilidi aç, tekrar çalıştır.")
    report = OUT / f"fps{label}.txt"
    report.unlink(missing_ok=True)
    cmd = [sys.executable, "-I", str(MAKE), "--scenario", "fps", "--label", label, "--rd", rd, "--sample", str(sample),
           "--settle", str(settle), "--shader", shader, "--size", size]
    if fullscreen:
        cmd.append("--fullscreen")
    if extra:
        cmd += ["--with", extra]
    if user_options:
        cmd += ["--user-options", user_options]
    subprocess.run(cmd, check=True, stdout=subprocess.DEVNULL)
    n = len(rd.split(","))
    budget = int(120 + n * (settle / 20 + sample + 10) + 60)
    print(f"▶ {label}: RD {rd}, shader {shader}, {'tam ekran 1080p' if fullscreen else size} — en çok {budget} sn", flush=True)
    t0 = time.time()
    try:
        subprocess.run(["prismlauncher", "--launch", "Theoria"], timeout=budget + 400, capture_output=True)
    except subprocess.TimeoutExpired:
        subprocess.run(["pkill", "-f", "java-runtime-epsilon"])
        print("  ZAMAN AŞIMI — oyun kapatıldı")
    took = time.time() - t0
    log = INSTANCE / "logs/latest.log"
    (OUT / "logs").mkdir(parents=True, exist_ok=True)
    text = log.read_text(errors="replace") if log.exists() else ""
    (OUT / "logs" / f"fps{label}.log").write_text(text)
    summary = {
        "label": label, "seconds": round(took),
        "vulkan": bool(re.search(r"Using graphics backend Vulkan", text)),
        "opengl": bool(re.search(r"Using graphics backend OpenGL", text)),
        "vitrail": bool(re.search(r"Vitrail \S+ starting", text)),
        "drawing": (re.search(r"Drawing (\S+) from", text) or [None, None])[1],
        "errors": len(re.findall(r"\] \[[^\]]+/ERROR\]", text)),
    }
    lines = report.read_text().splitlines() if report.exists() else []
    summary["results"] = [l for l in lines if l.startswith(("RESULT", "MEMORY", "backend="))]
    print("  " + json.dumps(summary, ensure_ascii=False))
    for l in summary["results"]:
        print("    " + l)
    if not report.exists() or "DONE" not in lines:
        print("  UYARI: rapor tamamlanmadı (DONE yok)")
    return summary


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("mode", choices=["sweep-full", "sweep-noshader", "sweep-user", "windowed", "endurance", "status"])
    ap.add_argument("--rd", default=None)
    ap.add_argument("--minutes", type=int, default=10)
    ap.add_argument("--with", dest="extra", default=None, help="opsiyonel modları aç (ör. distanthorizons)")
    a = ap.parse_args()
    if a.mode == "status":
        print("ekran kilitli" if screen_locked() else "ekran açık")
        for f in sorted(OUT.glob("fps*.txt")):
            print(f.name, [l for l in f.read_text().splitlines() if l.startswith("RESULT")])
        return
    results = []
    if a.mode == "sweep-full":
        results.append(run("_vk_full", a.rd or "12,16,24,32", "on", True, extra=a.extra))
    elif a.mode == "sweep-noshader":
        results.append(run("_vk_noshader", a.rd or "12,16,24,32", "off", True, extra=a.extra))
    elif a.mode == "sweep-user":
        results.append(run("_vk_useropts", a.rd or "12,16,24", "on", True, user_options="Vulkan Optimization"))
        # Kullanıcının grafik anahtarları instance'ın options.txt'sinde kalmasın: silinir, sonraki make_instance paket varsayılanını geri kurar.
        (INSTANCE / "options.txt").unlink(missing_ok=True)
    elif a.mode == "windowed":
        results.append(run("_vk_windowed", a.rd or "16,32", "on", False))
    elif a.mode == "endurance":
        rd = a.rd or "24"
        results.append(run(f"_vk_endurance_rd{rd}", rd, "on", True, sample=a.minutes * 60))
    (OUT / "bench_summary.jsonl").open("a").write(json.dumps(results[-1], ensure_ascii=False) + "\n")


if __name__ == "__main__":
    main()
