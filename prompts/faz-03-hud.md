# Faz 03 — HUD

> **Önerilen:** Sonnet 5.5 · Effort: **xHigh**
> **Önce yap:** yeni oturum (`/clear`) · **Bağlam:** `STATUS/PROGRESS.md` oku, sonra aşağıdaki belgeleri.

## Okunacaklar (yalnızca bunlar)
- `docs/04-sistem-tasarimi §1`
- `docs/02-teknik-temel §7`
- `docs/adr/ADR-002`
- `docs/10-acik-sorular (Q3, Q4)`

## Hedef
5 kalp/5 zırh/SAT/binek + orta sayılar + XP barı.

## İşler
1. Referans: `/Users/naer/code/LucidRem/mods-src/lucidrealms/.../client/*Overlay.java`, `StatusBars.java`, `generator/hud.py`, `tools/check_hud.py`, `check_hearts.py` — taşıma değil yeniden yazma (26.x render).
2. `generator/hud.py`: nötr sprite + yarım zırh saydamlığı. `tools/check_hud.py`: 21 HP vb.
3. Katman modeli: 5×4 çeyrek, 20 HP/kat, palet tükenince kat büyür.
4. Orta sayılar: ortalama + ölçek + kısaltma. Vanilla katmanları gizle.

## Bitiş kriteri
- check_hud yeşil; kullanıcı 20/21/250 HP, zırh 0/40/310 ekranlarını onayladı; Vulkan ve OpenGL'de aynı.

## Aşama sonu protokolü (zorunlu)
1. Doğrulama komutlarını çalıştır, çıktıyı dürüstçe raporla.
2. `docs/journal/f03.md` yaz (yapılan · öğrenilen · kararlar · açık sorunlar).
3. `STATUS/PROGRESS.md` güncelle.
4. Kullanıcıya şu devir bildirimini ver:

```
✅ Faz 03 tamam · <1-2 cümle sonuç> · Doğrulama: <komut → sonuç>
➡ SONRAKİ: Faz 04 · Model: Sonnet 5.5 · Effort: xHigh
   Önce: /clear
   Başlat: "prompts/faz-04-kanatlar.md dosyasını oku ve uygula."
   Neden bu model: Ekran + paket + kalıcılık uygulaması.
   Açık sorular: <varsa docs/10'a eklendi>
```

