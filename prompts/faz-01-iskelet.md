# Faz 01 — İskelet

> **Önerilen:** Sonnet 5.5 · Effort: **high**
> **Önce yap:** yeni oturum (`/clear`) · **Bağlam:** `STATUS/PROGRESS.md` oku, sonra aşağıdaki belgeleri.

## Okunacaklar (yalnızca bunlar)
- `docs/03-mimari`
- `docs/02-teknik-temel §6-7`
- `docs/adr/*`

## Hedef
Boş ama sağlam mod iskeleti: kayıtlar, config, ağ, attachment, lang, komut kökü, preflight.

## İşler
1. `core` paketi: sıralı kayıtlar (statik init dersi), config, `Payload` yardımcıları, attachment sarmalayıcı (`copyOnDeath` desteği).
2. `/theoria version` ve `/theoria test <alan>` komut iskeleti.
3. `lang/tr_tr.json` + `en_us.json` altyapısı.
4. `tools/check_data.py` (CSV şeması, lang eşleşmesi) ve `tools/preflight.py` (derle + check).
5. .gitignore, README bağlantıları.

## Bitiş kriteri
- Mod açılıyor; `/theoria version` çalışıyor; preflight yeşil; journal+PROGRESS güncel.

## Aşama sonu protokolü (zorunlu)
1. Doğrulama komutlarını çalıştır, çıktıyı dürüstçe raporla.
2. `docs/journal/f01.md` yaz (yapılan · öğrenilen · kararlar · açık sorunlar).
3. `STATUS/PROGRESS.md` güncelle.
4. Kullanıcıya şu devir bildirimini ver:

```
✅ Faz 01 tamam · <1-2 cümle sonuç> · Doğrulama: <komut → sonuç>
➡ SONRAKİ: Faz 02 · Model: Opus 5.5 · Effort: xHigh
   Önce: /clear
   Başlat: "prompts/faz-02-denge-cekirdegi.md dosyasını oku ve uygula."
   Neden bu model: 2a: denge modellemesi ve enjeksiyon noktası belirsizlik içeriyor.
   Açık sorular: <varsa docs/10'a eklendi>
```

