# Faz 00 — Ortam ve spike'lar

> **Önerilen:** Opus 5.5 · Effort: **xHigh**
> **Önce yap:** yeni oturum (`/clear`) · **Bağlam:** `STATUS/PROGRESS.md` oku, sonra aşağıdaki belgeleri.

## Okunacaklar (yalnızca bunlar)
- `docs/02-teknik-temel`
- `docs/07-fazlar (Faz 0)`
- `docs/11-mod-matrisi`
- `docs/09-riskler`

## Hedef
Varsayımları makinede kanıtla; ADR-001..005'i yaz.

## İşler
1. JDK 25 + Gradle ≥ 9.1 kur, `java -version` ve `JAVA_HOME` doğrula (mevcut: Java 21 — hiçbir şeyi silme, yanına kur).
2. NeoForge 26.3.0.101 MDK → `mods-src/theoria` (mod id `theoria`, paket `dev.theoria`). `./gradlew build` + `runClient`.
3. `tools/modmatrix/matrix.py`'yi çalıştır, `docs/11`'i güncelle, 26.2/26.3 son kararı ver.
4. S1..S7 spike'larını sırayla yap (`docs/07` Faz 0). **S1 kısa**: Vulkan zaten çalışıyor (D7) — yeni NeoForge instance'ında Sodium+Vitrail+Complementary Reimagined ile tekrar doğrula; `~/Library/Application Support/PrismLauncher/instances/Vulkan Optimization` referans. Her spike: soru → deney → kanıt (log/komut çıktısı) → karar. 'Belki' ile kapatma.
5. Her ADR `docs/adr/ADR-00N-*.md`: bağlam, karar, kanıt, sonuçlar, geri dönüş planı.
6. Kullanıcının makinesinde başka şeyi bozma; pencere/ekran görüntüsü alma.

## Bitiş kriteri
- ADR-001 sürüm+arka uç, 002 render kancaları, 003 hasar formülü enjeksiyonu, 004 enchant tavanı, 005 Mac giriş yazıldı.
- `docs/journal/f00.md` ve `STATUS/PROGRESS.md` güncel.
- `data/vanilla_mobs.csv` üretildi.
- Açık sorular `docs/10`'da güncel.

## Aşama sonu protokolü (zorunlu)
1. Doğrulama komutlarını çalıştır, çıktıyı dürüstçe raporla.
2. `docs/journal/f00.md` yaz (yapılan · öğrenilen · kararlar · açık sorunlar).
3. `STATUS/PROGRESS.md` güncelle.
4. Kullanıcıya şu devir bildirimini ver:

```
✅ Faz 00 tamam · <1-2 cümle sonuç> · Doğrulama: <komut → sonuç>
➡ SONRAKİ: Faz 01 · Model: Sonnet 5.5 · Effort: high
   Önce: /clear
   Başlat: "prompts/faz-01-iskelet.md dosyasını oku ve uygula."
   Neden bu model: Kararlar kesinleşti; iskelet mekanik iş.
   Açık sorular: <varsa docs/10'a eklendi>
```

> **Not:** ADR kapanış turunu (yalnızca ADR-001..005 metinleri) **Opus 5.5 · max** ile ayrı kısa oturumda yaptır; önce `/clear`.

