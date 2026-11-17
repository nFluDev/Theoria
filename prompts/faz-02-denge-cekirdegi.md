# Faz 02 — Denge çekirdeği

> **Önerilen:** Opus 5.5 (2a) → Sonnet 5.5 (2b) · Effort: **xHigh**
> **Önce yap:** yeni oturum (`/clear`) · **Bağlam:** `STATUS/PROGRESS.md` oku, sonra aşağıdaki belgeleri.

## Okunacaklar (yalnızca bunlar)
- `docs/06-denge-modeli`
- `docs/04-sistem-tasarimi §3`
- `docs/adr/ADR-003`
- `docs/adr/ADR-004`

## Hedef
Sayıların oyunda doğru çalışması: hasar azaltma, mob ölçekleme, regen, TierRegistry.

## İşler
1. ✅ **2a (2026-11-10 tamam):** `tools/balance/sim.py` yazıldı; katsayılar kilitlendi; `docs/06` güncel. Özet: `docs/journal/f02.md`.
2. ✅ **2b (2026-11-11 tamam):** `TierRegistry` (datapack'ten), attribute pipeline, hasar azaltma enjeksiyonu (ADR-003), mob ölçekleme, regen.
   Kesin iş listesi: `docs/06 §8` + ADR-003 "Faz 2a sonucu" (1-8) + ADR-003 "Sonraki fazlara yükümlülükler ▸ Faz 2b". Sayılar yalnızca `data/balance/tiers.csv` + `combat.csv`'den; beklenen değerler `data/balance/sim_cases.csv`.
3. ✅ `/theoria test balance`: zırh 300 oyuncu vs. tier-8 mob ±%10 (= `sim_cases.csv` ▸ `armor300_vs_t8`: 26,019 → 11,514).

## Bitiş kriteri
- Oyun-içi ölçüm simülasyonla ±%10 uyumlu; vanilla 20-zırh tavanı kalktı; `docs/06` güncel.

## Aşama sonu protokolü (zorunlu)
1. Doğrulama komutlarını çalıştır, çıktıyı dürüstçe raporla.
2. `docs/journal/f02.md` yaz (yapılan · öğrenilen · kararlar · açık sorunlar).
3. `STATUS/PROGRESS.md` güncelle.
4. Kullanıcıya şu devir bildirimini ver:

```
✅ Faz 02 tamam · <1-2 cümle sonuç> · Doğrulama: <komut → sonuç>
➡ SONRAKİ: Faz 03 · Model: Sonnet 5.5 · Effort: xHigh
   Önce: /clear
   Başlat: "prompts/faz-03-hud.md dosyasını oku ve uygula."
   Neden bu model: HUD net tanımlı, render kancası ADR-002'de çözüldü.
   Açık sorular: <varsa docs/10'a eklendi>
```

