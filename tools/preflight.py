#!/usr/bin/env python3
"""Theoria preflight: "çalışıyor" demeden önce koşulan tek komut.

    python3 -I tools/preflight.py            # veri denetimleri + derleme + başsız GameTest'ler
    python3 -I tools/preflight.py --client   # + gözetimsiz istemci duman testi, HUD ve kanat senaryoları (her biri ≈1 dk; bir oyun PENCERESİ açar ve kendi kapanır)
    python3 -I tools/preflight.py --fast     # yalnızca veri denetimleri + derleme

Çıkış kodu 0 = hepsi yeşil. Gradle, daemon JVM'ini gradle/gradle-daemon-jvm.properties ile kendisi seçer (JDK 25);
kabuktaki JAVA_HOME'un 21 olması sorun değildir.
"""
import pathlib, re, subprocess, sys, time

ROOT = pathlib.Path(__file__).resolve().parents[1]
MOD = ROOT / "mods-src/theoria"
OUT = MOD / "build/dev-out"
args = set(sys.argv[1:])
results = []


def step(name, cmd, cwd=ROOT, expect=None, log=None):
    """Runs cmd; passes if exit code is 0 and (optionally) `expect` regex is found in the output."""
    print(f"▶ {name}", flush=True)
    started = time.time()
    proc = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True)
    output = proc.stdout + proc.stderr
    if log:
        log.parent.mkdir(parents=True, exist_ok=True)
        log.write_text(output, encoding="utf-8")
    ok = proc.returncode == 0
    detail = ""
    if expect:
        match = re.search(expect, output)
        ok = ok and match is not None
        detail = match.group(0) if match else f"beklenen çıktı yok: /{expect}/"
    if not ok:
        tail = "\n".join(output.strip().splitlines()[-15:])
        print(tail)
    results.append((name, ok, detail, time.time() - started))
    return ok


py = [sys.executable, "-I"]
step("veri denetimi (check_data.py)", py + ["tools/check_data.py"], expect=r"(?m)^OK$")
step("HUD denetimi (check_hud.py)", py + ["tools/check_hud.py"], expect=r"(?m)^OK$")
step("kanat denetimi (check_wings.py)", py + ["tools/check_wings.py"], expect=r"(?m)^OK$")
step("paket denetimi (check_pack.py)", py + ["tools/check_pack.py"], expect=r"(?m)^OK$")
step("vanilla mob verisi (check_vanilla_mobs.py)", py + ["tools/check_vanilla_mobs.py", str(ROOT / "data/vanilla_mobs.csv")], expect=r"(?m)^OK")
step("derleme (gradlew build)", ["./gradlew", "build", "--console=plain", "-q"], cwd=MOD, log=MOD / "build/preflight-build.log")
step("oynanış instance'ı güncel mi (sync_jar.py)", py + ["tools/pack/sync_jar.py"], expect=r"(?m)^OK")   # Faz 6 dersi: Prism'deki jar eski kalmasın
if "--fast" not in args:
    step("GameTest'ler (gradlew runGameTestServer)", ["./gradlew", "runGameTestServer", "--console=plain"], cwd=MOD,
         expect=r"All \d+ required tests passed", log=MOD / "build/preflight-gametest.log")
if "--client" in args:
    report = OUT / "client_smoke.txt"
    if report.exists():
        report.unlink()
    step("istemci duman testi (gradlew runDevClient)", ["./gradlew", "runDevClient", "--console=plain"], cwd=MOD,
         log=MOD / "build/preflight-client.log")
    text = report.read_text(encoding="utf-8") if report.exists() else ""
    results.append(("istemci raporu (client_smoke.txt)", "RESULT PASS" in text,
                    next((l for l in text.splitlines() if l.startswith("RESULT")), "rapor yazılmadı"), 0.0))
    hud_report = OUT / "client_hud.txt"
    if hud_report.exists():
        hud_report.unlink()
    step("istemci HUD senaryosu (gradlew runDevClient -PdevClient=hud)", ["./gradlew", "runDevClient", "-PdevClient=hud", "--console=plain"], cwd=MOD,
         log=MOD / "build/preflight-client-hud.log")
    text = hud_report.read_text(encoding="utf-8") if hud_report.exists() else ""
    results.append(("HUD senaryo raporu (client_hud.txt)", "RESULT PASS" in text,
                    next((l for l in text.splitlines() if l.startswith("RESULT")), "rapor yazılmadı"), 0.0))

    catalyst_report = OUT / "client_catalyst.txt"
    if catalyst_report.exists():
        catalyst_report.unlink()
    step("istemci kataliz ocağı senaryosu (gradlew runDevClient -PdevClient=catalyst)", ["./gradlew", "runDevClient", "-PdevClient=catalyst", "--console=plain"], cwd=MOD,
         log=MOD / "build/preflight-client-catalyst.log")
    text = catalyst_report.read_text(encoding="utf-8") if catalyst_report.exists() else ""
    results.append(("kataliz ocağı senaryo raporu (client_catalyst.txt)", "RESULT PASS" in text,
                    next((l for l in text.splitlines() if l.startswith("RESULT")), "rapor yazılmadı"), 0.0))

    wings_report = OUT / "client_wings.txt"
    if wings_report.exists():
        wings_report.unlink()
    step("istemci kanat senaryosu (gradlew runDevClient -PdevClient=wings)", ["./gradlew", "runDevClient", "-PdevClient=wings", "--console=plain"], cwd=MOD,
         log=MOD / "build/preflight-client-wings.log")
    text = wings_report.read_text(encoding="utf-8") if wings_report.exists() else ""
    results.append(("kanat senaryo raporu (client_wings.txt)", "RESULT PASS" in text,
                    next((l for l in text.splitlines() if l.startswith("RESULT")), "rapor yazılmadı"), 0.0))

print()
for name, ok, detail, seconds in results:
    print(f"{'✅' if ok else '❌'} {name}" + (f" — {detail}" if detail else "") + (f"  ({seconds:.0f} sn)" if seconds >= 1 else ""))
failed = [name for name, ok, _, _ in results if not ok]
print("\nPREFLIGHT " + ("YEŞİL" if not failed else f"KIRMIZI ({len(failed)} adım)"))
sys.exit(1 if failed else 0)
