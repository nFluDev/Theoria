# Faz 04 — Kanatlar + ESC menüsü

> **Önerilen:** Sonnet 5.5 · Effort: **xHigh**
> **Önce yap:** yeni oturum (`/clear`) · **Bağlam:** `STATUS/PROGRESS.md` oku, sonra aşağıdaki belgeleri.

## Okunacaklar (yalnızca bunlar)
- `docs/04-sistem-tasarimi §2`
- `docs/02-teknik-temel §7`
- `docs/adr/ADR-002`

## Hedef
ESC'ye 'Kanatlar' butonu, dinamik kanat ekranı, 3B oyuncu, kalıcı kanat.

## İşler
1. Referans: LucidRem `wings/*`, `ui/PauseMenuHook.java`, project.md §45.5.
2. `isPauseScreen()=false`; FW animatörü tick; `renderEntityInInventory…` ham fare koordinatı.
3. Katalog çalışma anında okunur; paket ile tak/çıkar; `copyOnDeath` + respawn.

## Bitiş kriteri
- Kanat tak/çıkar anında görünür; ölüm sonrası kalıcı; pause ekranı açıkken animasyon akıyor.

## Aşama sonu protokolü (zorunlu)
1. Doğrulama komutlarını çalıştır, çıktıyı dürüstçe raporla.
2. `docs/journal/f04.md` yaz (yapılan · öğrenilen · kararlar · açık sorunlar).
3. `STATUS/PROGRESS.md` güncelle.
4. Kullanıcıya şu devir bildirimini ver:

```
✅ Faz 04 tamam · <1-2 cümle sonuç> · Doğrulama: <komut → sonuç>
➡ SONRAKİ: Faz 05 · Model: Sonnet 5.5 · Effort: high
   Önce: /clear
   Başlat: "prompts/faz-05-pack-qol-macos.md dosyasını oku ve uygula."
   Neden bu model: Pack/liste/config işi.
   Açık sorular: <varsa docs/10'a eklendi>
```

