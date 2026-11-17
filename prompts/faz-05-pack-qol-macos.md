# Faz 05 — Pack tabanı + QoL + macOS

> **Önerilen:** Sonnet 5.5 · Effort: **high**
> **Önce yap:** yeni oturum (`/clear`) · **Bağlam:** `STATUS/PROGRESS.md` oku, sonra aşağıdaki belgeleri.

## Okunacaklar (yalnızca bunlar)
- `docs/11-mod-matrisi`
- `docs/04-sistem-tasarimi §8`
- `docs/adr/ADR-001`
- `docs/adr/ADR-005`

## Hedef
M1: modlist, config, Mac düzeltmeleri, performans tabanı.

## İşler
1. `pack/modlist.toml` + packwiz (Vitrail + Complementary Reimagined dahil; `docs/12`'ye bak); Prism instance dışa aktarımı.
2. Mac Input Fixes/McMouser doğrulaması; gerekirse `macos` paketi.
3. FPS taban ölçümü tablosu — **yalnızca Vulkan (D19: Iris yok, OpenGL hedeflenmez)**.

## Bitiş kriteri
- M1 kontrol listesi (`docs/07` Faz 5) tamam; FPS tablosu journal'da.

## Aşama sonu protokolü (zorunlu)
1. Doğrulama komutlarını çalıştır, çıktıyı dürüstçe raporla.
2. `docs/journal/f05.md` yaz (yapılan · öğrenilen · kararlar · açık sorunlar).
3. `STATUS/PROGRESS.md` güncelle.
4. Kullanıcıya şu devir bildirimini ver:

```
✅ Faz 05 tamam · <1-2 cümle sonuç> · Doğrulama: <komut → sonuç>
➡ SONRAKİ: Faz 06 · Model: Sonnet 5.5 · Effort: xHigh
   Önce: /clear
   Başlat: "prompts/faz-06-gear.md dosyasını oku ve uygula."
   Neden bu model: İçerik fazı başlıyor; M1 kutlanır.
   Açık sorular: <varsa docs/10'a eklendi>
```

