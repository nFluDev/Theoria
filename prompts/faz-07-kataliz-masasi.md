# Faz 07 — Kataliz Masası

> **Önerilen:** Opus 5.5 (7a) → Sonnet 5.5 (7b) · Effort: **high / xHigh**
> **Önce yap:** yeni oturum (`/clear`) · **Bağlam:** `STATUS/PROGRESS.md` oku, sonra aşağıdaki belgeleri.

## Okunacaklar (yalnızca bunlar)
- `docs/04-sistem-tasarimi §4`
- `docs/05-icerik-tasarimi §3`
- `docs/adr/ADR-004`
- `docs/06-denge-modeli §3` (referans bütçe: katalizörlerin toplam can/zırh/hasar payı) — `python3 -I tools/balance/sim.py --budget`

## Hedef
Katalizör sistemi: masa, ~32 katalizör, Netherite evrensel katalizörü.

## İşler
1. **7a (Opus high):** component şeması, üst sınırlar, tarif maliyetleri, UX; `/clear`.
2. **7b (Sonnet xHigh):** block/menu/screen, item'lar, **Bilgelik (XP) büyüsü + katalizörü**, uygulama mantığı, element efektleri (datapack enchantment), JEI, tooltip.

## Bitiş kriteri
- Hız+Kritik+Buz örneği çalışıyor; Netherite +1 büyü; kayıt/yükle kalıcı.

## Aşama sonu protokolü (zorunlu)
1. Doğrulama komutlarını çalıştır, çıktıyı dürüstçe raporla.
2. `docs/journal/f07.md` yaz (yapılan · öğrenilen · kararlar · açık sorunlar).
3. `STATUS/PROGRESS.md` güncelle.
4. Kullanıcıya şu devir bildirimini ver:

```
✅ Faz 07 tamam · <1-2 cümle sonuç> · Doğrulama: <komut → sonuç>
➡ SONRAKİ: Faz 08 · Model: Sonnet 5.5 · Effort: xHigh
   Önce: /clear
   Başlat: "prompts/faz-08-mobs.md dosyasını oku ve uygula."
   Neden bu model: Mob aileleri: çok dosyalı, tanımı net uygulama.
   Açık sorular: <varsa docs/10'a eklendi>
```

## 7a sonucu (2026-11-11) — 7b buradan başladı (tarihçe; 7b bitti, aşağıdaki "7b sonucu"na bak)
**Tasarım:** `docs/adr/ADR-006-katalizor-sistemi.md` (Karar 1-13 + GameTest listesi + geri dönüş). **Sayılar:** `python3 -I tools/balance/catalysts.py` → `data/balance/catalysts.csv` (32), `catalyst_values.csv` (beklenen değerler), `catalyst_rules.csv`, `enchant_caps.csv`, `innate.csv`, oyun JSON'u `data/theoria/balance/catalysts/default.json`. `--report` bütçe tablosu, `--break` negatif test, `--check` check_data.py içinde.
**7b sırası (Sonnet 5.5 · xHigh · önce `/clear`):**
1. **İlk iş = kanıt GameTest'i `theoria:enchant_effective_level`** (ADR-004 yükümlülüğü + ADR-006 sanal büyü): Keskinlik V + Netherite 1 → 6, hasar +3,5; Servet III+1 → 4; 300 → 255; sanal büyü (katalizörden/doğuştan) event'te görünüyor; örs/kayıt-yükle sonrası. **Kırmızıysa dur, ADR-004/006 "Geri dönüş"e bak, kullanıcıya yaz.**
2. Component + codec + sync (`theoria:catalysts`, `catalyst_level/grade`) → yuva/uygunluk kuralı (saf Java, GameTest) → `ItemAttributeModifierEvent` + özel nitelikler + `CriticalHitEvent`/çalma/diken/doygunluk kancaları.
3. Sanal büyüler: `generator/catalysts.py` (JSON `lookup` tabloları `catalyst_values.csv`'den; `P = 1 + 5g + L−1`), element datapack büyüleri, Onarım, Menzil Gücü; doğuştan tablo (`innate.csv` → gear.json kademesi).
4. Netherite (sayaç + `enchant_caps.csv`; her id'nin kayıt defterinde olduğu test) ve Bilgelik (`theoria:erudition` + XP kancası: toplama, %2400 tavanı, yalnız öldürme/blok).
5. Item'lar (31 + vanilla Netherite), tarifler (merdiven × imza malzeme), `catalyst_dust/essence/core` + geçici loot kaynakları, **Kataliz Ocağı** (blok + BlockEntity-siz menü yeter + ekran + Uygula düğmesi + önizleme), lang (tr/en), JEI, tooltip, `/theoria test catalysts`.
6. DoD: Hız IV+Kritik III+Buz II örneği (DPS ×1,34 T8) ve Netherite Keskinlik V→VI; kayıt/yükle kalıcı. Kullanıcı onayı: ekranın görünüşü ve tooltip (otomatik doğrulanamaz).
**Dikkat:** CLAUDE.md "ekranlar oyunu durdurmamalı"; üçüncü taraf API'ye derleme yok (CLAUDE.md): katalizör tarifleri vanilla şekilsiz tarif olduğundan JEI zaten gösterir; **özel "Kataliz Ocağı" JEI kategorisi JEI plugin API'si = derleme bağımlılığı ister → yapma, `docs/10`'a soru olarak yaz**; sayı gömme yok (hepsi `catalysts/default.json`'dan).

## 7b sonucu (2026-11-11) — Faz 8 buradan devam eder
**Bitti:** ADR-006'nın Karar 2-13'ü uygulandı; sapmalar ve nedenleri **ADR-006 "Faz 7b sonucu"** tablosunda (üretim Ocak'ta · ev sahibi etiketi yok · element etkileri Java'da · nitelik başına tek uygulama). Kanıt: `runGameTestServer` 50/50, `runDevClient -PdevClient=catalyst` PASS, `preflight --client` YEŞİL; ayrıntı `docs/journal/f07.md`.
**Faz 8'e devreden:** `catalyst_essence` ve `catalyst_core` damlalarını mob/boss zincirine bağla (şu an `generator/catalysts.py`'de 20 düşman %12 öz, 4 boss %100 çekirdek, 6 sandık %25 çekirdek, 26 cevher %35 toz — geçici) · Q35 (mob el silahı).
**Kullanıcıda:** Faz 7 görünüş onayı (ocak ekranı, ikonlar, tooltip) ve `docs/10` B10.
