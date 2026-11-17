# Faz 10 — Durum ekranı + seviye

> **Önerilen:** Sonnet 5.5 · Effort: **xHigh**
> **Önce yap:** yeni oturum (`/clear`) · **Bağlam:** `STATUS/PROGRESS.md` oku, sonra aşağıdaki belgeleri.

## Okunacaklar (yalnızca bunlar)
- `docs/04-sistem-tasarimi §5`
- `docs/06-denge-modeli §5` + `§3` (referans bütçe: taslak puan değerleri çok cömert — `python3 -I tools/balance/sim.py --budget`)
- `docs/10-acik-sorular (Q7-Q9)`

## Hedef
Anime tarzı durum ekranı, ömür boyu XP seviyesi, 4 puan/seviye, düz bonuslar.

## İşler
1. `stats` attachment (XP `long`, tavan **9999**), XP→seviye (iki parçalı eğri `docs/06 §5`), yumuşak tavanlı stat'lar (Q3), paketler, ekran (3B oyuncu), efektler.
2. Respec item (kararlaştıysa), HUD seviyesi, mob XP ayarı.

## Bitiş kriteri
- L2=55 ham XP; stat etkileri tek tek doğrulandı; harcanan XP seviyeyi düşürmüyor.

## Aşama sonu protokolü (zorunlu)
1. Doğrulama komutlarını çalıştır, çıktıyı dürüstçe raporla.
2. `docs/journal/f10.md` yaz (yapılan · öğrenilen · kararlar · açık sorunlar).
3. `STATUS/PROGRESS.md` güncelle.
4. Kullanıcıya şu devir bildirimini ver:

```
✅ Faz 10 tamam · <1-2 cümle sonuç> · Doğrulama: <komut → sonuç>
➡ SONRAKİ: Faz 11 · Model: Opus 5.5 · Effort: xHigh
   Önce: /clear
   Başlat: "prompts/faz-11-curios-kozmetik.md dosyasını oku ve uygula."
   Neden bu model: 11a render kancası belirsiz.
   Açık sorular: <varsa docs/10'a eklendi>
```

