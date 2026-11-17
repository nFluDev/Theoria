# Faz 06 — Materyal, zırh, alet, silah

> **Önerilen:** Sonnet 5.5 · Effort: **xHigh**
> **Önce yap:** yeni oturum (`/clear`) · **Bağlam:** `STATUS/PROGRESS.md` oku, sonra aşağıdaki belgeleri.

## Okunacaklar (yalnızca bunlar)
- `docs/05-icerik-tasarimi §1-2`
- `docs/06-denge-modeli`
- `docs/03-mimari §3`

## Hedef
14 zırh + 11 alet/silah kademesi, yeni cevherler, alaşımlar, vanilla override, netherite gear kaldırma.

## İşler
1. Kazı kapıları (`docs/05 §7`, `tiers.py --ores`) dahil: cevher→gereken alet, sertlik, `incorrect_for_*_tool` tag'leri.
2. `data/materials.csv` (kaynağı `data/balance/tiers.csv`) + `generator/gear.py`.
3. Equipment asset (zırh dokusu tint'li), item modelleri, tarif/loot/tag/lang.
4. Cevher feature'ları (datapack), alaşım tarifleri, öntanımlı büyüler.
5. JEI uyumu; `tools/check_data.py` genişlet.

## Bitiş kriteri
- Kalay→Platin tırmanışı denendi; tooltip statları tabloyla birebir; check yeşil.

## Aşama sonu protokolü (zorunlu)
1. Doğrulama komutlarını çalıştır, çıktıyı dürüstçe raporla.
2. `docs/journal/f06.md` yaz (yapılan · öğrenilen · kararlar · açık sorunlar).
3. `STATUS/PROGRESS.md` güncelle.
4. Kullanıcıya şu devir bildirimini ver:

```
✅ Faz 06 tamam · <1-2 cümle sonuç> · Doğrulama: <komut → sonuç>
➡ SONRAKİ: Faz 07 · Model: Opus 5.5 · Effort: high
   Önce: /clear
   Başlat: "prompts/faz-07-kataliz-masasi.md dosyasını oku ve uygula."
   Neden bu model: 7a tasarım: component şeması ve kurallar.
   Açık sorular: <varsa docs/10'a eklendi>
```

