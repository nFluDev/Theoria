# Faz 08 — Mob'lar ve drop zinciri

> **Önerilen:** Sonnet 5.5 · Effort: **xHigh (boss AI: Opus 5.5 high)**
> **Önce yap:** yeni oturum (`/clear`) · **Bağlam:** `STATUS/PROGRESS.md` oku, sonra aşağıdaki belgeleri.

## Okunacaklar (yalnızca bunlar)
- `docs/05-icerik-tasarimi §4`
- `docs/06-denge-modeli §4` (+ `python3 -I tools/balance/sim.py --mobs`: mob başına ölüm/öldürme vuruşu)
- `data/vanilla_mobs.csv`

## Hedef
M2: 30+ mob, drop'lar merdiveni besliyor, 2+ boss.

## İşler
1. 12 arketip + `data/mobs.csv`; aile aile ilerle (bataklık, yaban, yeraltı, dağ, alev, buz, gölge, kadim).
2. Spawn kuralları (biyom+kademe), drop → materyal/öz/çekirdek, XP ölçeği, spawn egg, JEI.
3. `tools/check_drops.py`: her merdiven öğesinin en az bir kaynağı var.

## Bitiş kriteri
- M2 kontrolü: 30+ mob, drop matrisi yeşil, TTK docs/06 hedefinde.

## Aşama sonu protokolü (zorunlu)
1. Doğrulama komutlarını çalıştır, çıktıyı dürüstçe raporla.
2. `docs/journal/f08.md` yaz (yapılan · öğrenilen · kararlar · açık sorunlar).
3. `STATUS/PROGRESS.md` güncelle.
4. Kullanıcıya şu devir bildirimini ver:

```
✅ Faz 08 tamam · <1-2 cümle sonuç> · Doğrulama: <komut → sonuç>
➡ SONRAKİ: Faz 09 · Model: Opus 5.5 · Effort: high
   Önce: /clear
   Başlat: "prompts/faz-09-dunya.md dosyasını oku ve uygula."
   Neden bu model: 9a worldgen tasarımı.
   Açık sorular: <varsa docs/10'a eklendi>
```

