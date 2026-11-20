# 13 — Yayın (Faz 13): sürüm, kontrol listesi, lisans notları, oyun testi

**Durum (2026-11-20):** sürüm adayı **1.0.0-rc.1** (`mod_version` ve `pack/packwiz/pack.toml` aynı). v1.0 etiketi, aşağıdaki "Kullanıcı kapısı" kapanınca atılır — **etiket, push ve dış yayın kullanıcının kararıdır** (CLAUDE.md: kendiliğinden commit/push yok).

## 1. Sürüm kimliği (tek yer)
| Ne | Nerede | Şimdi |
|---|---|---|
| Mod jar'ı / `/theoria version` | `mods-src/theoria/gradle.properties` `mod_version` | `1.0.0-rc.1` |
| Paket / `.mrpack` adı | `pack/packwiz/pack.toml` `version` | `1.0.0-rc.1` |
| Git etiketi | `git tag v1.0.0` (kullanıcı onayından sonra) | yok |
Sürüm değişince: `./gradlew build` → `python3 -I tools/pack/sync_jar.py --apply` → `python3 -I tools/pack/make_instance.py --export`.

## 2. Yayın akışı (komutlar)
```
python3 -I tools/preflight.py --client          # veri + derleme + istemci senaryoları (bir pencere açar)
./gradlew runGameTestServer --console=plain     # tam GameTest takımı (mods-src/theoria içinden)
python3 -I tools/pack/sync_packwiz.py           # modlist.toml → packwiz (SSL_CERT_FILE=/etc/ssl/cert.pem gerekebilir)
python3 -I tools/check_pack.py --online         # modlist ≡ packwiz ≡ Modrinth
python3 -I tools/pack/sync_jar.py --apply       # oynanan Prism instance'ının jar'ı
python3 -I tools/pack/make_instance.py --export # pack/dist/Theoria-<sürüm>.mrpack
```
**Release jar'ında test kodu yok (Faz 13):** `dev.theoria.devtest` (GameTest, istemci senaryoları, ≈ 50 sınıf) `jar` görevinden dışlanır; `Theoria`/`TheoriaClient` ona yalnız yansımayla (ve yalnız `-Dtheoria.dev=true` ile) ulaşır. Geliştirme koşuları (`runGameTestServer`, `runDevClient`) derlenmiş sınıfları kullandığı için etkilenmez. Paketlenmiş bir instance'ta test koşmak için `./gradlew build -PkeepDevTests`.

## 3. Lisans notları (hukuki görüş değil — yayın öncesi kontrol listesi)
- **Theoria kodu:** `mod_license=All Rights Reserved` (gradle.properties). Başkasına dağıtılacaksa lisans seçimi yayıncının kararı (docs/09 R14).
- **Mojang'dan türetilen dokular:** zırh `humanoid_baby` katmanı (vanilla demir bebek şablonundan boyanmış varyantlar, `docs/10` Q40) ve HUD'un nötr kalp/zırh/yemek sprite'ları. Bunlar Minecraft'ın dokularına dayanır; Minecraft Kullanım Yönergeleri ve EULA geçerlidir. Dağıtıma gidilecekse bu dokuların yeniden çizilmesi (ya da dağıtımda yalnız kod + bağlantı) tercih edilir. *Kullanıcı kararı bekliyor (açık: Q40).*
- **Üçüncü taraf modlar paketin içinde değil:** packwiz yalnızca Modrinth bağlantısı + sha512 tutar (`pack/packwiz/mods/*.pw.toml`); jar'lar kullanıcının makinesinde Modrinth'ten inip kurulur. `.mrpack` da aynı bağlantıları taşır. Her modun kendi lisansı geçerli; Curios (derleme bağı, ADR-007) ve TerraBlender (yansıma) dışında koda bağlı değiliz.
- **Shader paketi:** Complementary Reimagined `shaderpacks/*.pw.toml` ile bağlantı olarak gelir; kendi lisansı.
- **Yalnızca kendin oynuyorsan** bu maddelerin çoğu devreye girmez (R14).

## 4. Bilinen sınırlar (ayrıntı: `STATUS/PROGRESS.md` "Açık sorunlar")
- **Kullanıcı DoD onayları** Faz 3-8, 10, 11, 12 (görünüş/his) hâlâ bekliyor; Vulkan + Vitrail altında görünüş yalnız kullanıcıda doğrulanır.
- **Lootr altında gerçek relic/Kale düşüşü** denenmedi (Q93).
- **GameTest zamanlama kırmızıları (Q58):** `gear_innate` / `catalyst_elements` tam takımda aralıklı kırmızı (zombi zamanlaması); küçük takımlarda yeşil.
- **XP eğrisi L99→L100 %28 düşüş** (610 k → 441 k; kullanıcının L100/L200 bağlı noktaları, `docs/06 §5`). L200 sonrası uçurum düzeltildi (Faz 13 code-review).
- Vanilla Ravager T13 ≈ 1,02 HTD sınır durumu (`docs/06 §4`).
- Kademe genişletmesi (ADR-008) yazılmadı; onay bekliyor (Q86-Q90).

## 5. Kullanıcı kapısı (v1.0 için — kodla kapanmaz)
1. **20+ saatlik oyun testi** (yeni survival dünya; Prism `Theoria` instance'ı). Not tutma biçimi aşağıda.
2. Faz 3-8, 10-12 görünüş/his onayları (`STATUS/PROGRESS.md` "Bir sonraki adım" kontrol listeleri).
3. "Bilinen kritik hata yok" → not tablosunda hiçbir **KRİTİK** satır açık değil.
4. Onaydan sonra: `git tag v1.0.0`, `.mrpack`, yayın yeri kararı (Modrinth / yalnız kendin).

### Oyun testi notları (şablon — kullanıcı doldurur; yeniden dengeye girdi olur)
| Saat | Kademe / seviye | Not (denge · hata · his) | Önem (KRİTİK / orta / küçük) | Durum |
|--:|---|---|---|---|
| | | | | |
Yeniden denge adımı: not → `tools/balance/*.py` sabiti → `python3 -I tools/balance/sim.py` (bantlar) → ilgili `--md` tablosu → `docs/06`. Worldgen donduruldu (R10): dünya notları yalnız yeni seed'lerde denenir.
