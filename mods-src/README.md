# mods-src

`theoria/` — NeoForge 26.3 ModDevGradle projesi (mod id `theoria`, paket `dev.theoria`). Paket yapısı: `docs/03-mimari.md §2`.

```
cd mods-src/theoria
./gradlew build                 # derle → build/libs/theoria-<sürüm>.jar
./gradlew runGameTestServer     # başsız GameTest'ler → build/dev-out/*.txt
./gradlew runDevClient          # gözetimsiz istemci duman testi (pencere açar, kendi kapanır); -PdevClient=fps → FPS ölçümü; -PdevClient=hud → HUD senaryosu; -PdevClient=wings → Kanatlar senaryosu
./gradlew runClient             # normal geliştirme istemcisi (run/)
```
Denge çekirdeği oyun içinde: `/theoria test balance` (güvenli; ±%10), `/theoria tier <hedef> <kademe>` (mob'a kademe ver, 0 = vanilla).
Hepsini sırayla koşan tek komut (repo kökünden): `python3 -I tools/preflight.py [--client | --fast]`.
Gradle daemon'u JDK 25'i kendisi seçer (`gradle/gradle-daemon-jvm.properties`); kabuktaki `JAVA_HOME` 21 kalabilir.
HUD (Faz 3): `/theoria test hud` (model ≡ Python), `python3 -I tools/check_hud.py`, `python3 -I tools/hud_preview.py` (çevrimdışı önizleme → `build/hud-preview/`). Sabitler tek yerde: `data/hud/hud.json`.
Kanatlar (Faz 4): `/theoria test wings`, `python3 -I tools/check_wings.py`, `./gradlew runDevClient -PdevClient=wings`; FW yalnızca `localRuntime` (kayıt defteri + `/wings` komutu düzeyi). Dünyada: ESC ▸ Kanatlar.
Paket (Faz 5): `pack/README.md` — `python3 -I tools/check_pack.py` (modlist ≡ packwiz, D19, ADR-005), `python3 -I tools/pack/make_instance.py [--scenario hud|wings|fps|smoke]` (tam paketi Prism instance'ı `Theoria` olarak kurar), `python3 -I tools/pack/bench.py sweep-full|sweep-noshader|windowed|endurance` (FPS; tam ekran, **ekran kilitliyken çalışmaz**). Gözetimsiz senaryolar dev istemcisinde de, paket instance'ında da aynı: DevClient "render hazır" kapısı (yükleme ekranı kapandı + kare/sunucu-tick hızı kararlı + Vitrail varsa 15 sn) bekler.
