# ADR (mimari karar kayıtları)
Biçim: Bağlam · Karar · Kanıt · Sonuçlar · Sonraki fazlara yükümlülükler · Geri dönüş planı.

**Kanıt etiketleri** (kapanış turu, 2026-11-10): **[log]** komut/log çıktısı — yeniden üretilebilir · **[kaynak]** decompile edilmiş 26.3.0.101 kaynağından okundu, çalıştırılmadı · **[çıkarım]** denenmedi; hangi fazda/hangi testle kanıtlanacağı ADR'nin "yükümlülükler" bölümünde.

| ADR | Konu | Durum | Spike |
|---|---|---|---|
| [ADR-001](ADR-001-surum-ve-arka-uc.md) | Sürüm (26.3 / NeoForge 26.3.0.101), Java 25, Vulkan birincil (**D19: OpenGL yedeği kalktı**, Faz 5) | Kabul 2026-11-09 · kapanış 2026-11-10 | iş 1-3, S1 |
| [ADR-002](ADR-002-render-kancalari.md) | Render kancaları: HUD katmanları, 3B oyuncu (pause'suz ekran, `GuiGraphicsExtractor#entity`), zırh render-state, FW kataloğu + sunucu-kaynaklı `/wings` | Kabul 2026-11-09 · kapanış 2026-11-10 | S2, S3 |
| [ADR-003](ADR-003-hasar-formulu-enjeksiyonu.md) | Zırh formülü: `Reduction.ARMOR` modifier (+ `armor_effectiveness`) + AT ile jar'a gömülü teknik attribute tavanı | Kabul 2026-11-09 · kapanış 2026-11-10 | S6 |
| [ADR-004](ADR-004-enchant-tavani.md) | Büyü seviyesi: vanilla ≤255 + kendi component'imiz + `GetEnchantmentLevelEvent`; 255 üstü v1.0 dışı | Kabul 2026-11-09 · kapanış 2026-11-10 (**Karar 4 değişti**) | S4 |
| [ADR-005](ADR-005-mac-giris.md) | macOS girişi: vanilla 26.3 (SDL3) + yedek Mac Input Fixes; McMouser elendi | Kabul 2026-11-09 · kapanış 2026-11-10 (kullanıcı onayı D12) | S7 |
| [ADR-006](ADR-006-katalizor-sistemi.md) | Katalizör sistemi: `theoria:catalysts` şeması, yuva/seviye/tavanlar, sanal büyü, Netherite, Bilgelik XP kancası, maliyet merdiveni, masa UX | Kabul 2026-11-11 (Faz 7a; sayılar taslak: Q43-Q47) | `tools/balance/catalysts.py` |
| [ADR-007](ADR-007-curios-max-ve-kozmetik.md) | Curios Max + kozmetik zırh: Curios'a tek derleme bağı, kendi menü/ekran (sunucusuz sekme/sayfa), `theoria:cosmetics` attachment, tek saf görünüm kuralı + tek render-state modifier, sunucu tarafı yönlendirme, slot verisi (CSV → JSON) | Kabul 2026-11-20 (Faz 11; sayılar taslak: Q76) | GameTest `cosmetics_*`/`curios_*`, `-PdevClient=curios` |
| [ADR-008](ADR-008-kademe-genisletme-taslagi.md) | Kademe genişletmesi (28 adım, zor üretim, Lootr) — **TASLAK, onay bekliyor** (`docs/10` Q86-Q90) | Taslak 2026-11-20 | — |

**Kanıt dosyaları:** [`kanit/`](kanit/) — spike çıktılarının kalıcı kopyaları (`build/spike-out/` ve Prism logları geçicidir): `s1_fps_*` (ADR-001), `s2_s3_s7_runs.log` (ADR-002/005; üç istemci çalıştırması + McMouser reddi), `s4_enchant_cap.txt` (ADR-004), `s5_mob_dump.txt`, `s6_damage_*.txt` (ADR-003).

**Yeniden üretim** (Faz 1'den beri): `python3 -I tools/preflight.py` → `./gradlew runGameTestServer` içinde `vanilla_enchant_cap` (S4), `vanilla_armor_caps_mob/_player` (S6), `dump_vanilla_mobs` (S5) kalıcı testtir. S2/S3/S7 istemci senaryosu koddan çıkarıldı — kaynağı `kanit/SpikeClient.java.txt`, çıktıları `kanit/s2_s3_s7_runs.log`. S1: `./gradlew runDevClient -PdevClient=fps` (ya da Prism instance'ında `-Dtheoria.dev=true -Dtheoria.dev.client=fps`).
