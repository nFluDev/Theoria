# Faz 00b — ADR kapanış turu (kısa, tek oturum)

> **Önerilen:** Opus 5.5 · Effort: **max**
> **Önce yap:** `/clear` · **Bağlam:** yalnızca aşağıdakiler. Kod yazma yok; yalnızca karar belgeleri.

## Okunacaklar
- `STATUS/PROGRESS.md`
- `docs/journal/f00.md` (kanıtlar ve 26.3 API notları)
- `docs/adr/ADR-001..005`
- Gerekirse `docs/06-denge-modeli.md` §1-2 (ADR-003 formülü), `docs/10-acik-sorular.md` B2

## Hedef
ADR-001..005'i son kez eleştirel gözle oku ve kesinleştir. Her ADR için sor:
1. Karar, kanıtla gerçekten destekleniyor mu? (Kanıt satırı log/komut çıktısı mı, yoksa çıkarım mı? Çıkarımsa öyle etiketle.)
2. "Belki" kalan bir şey var mı? Varsa: ya kanıt iste (hangi komut), ya fallback'i karar olarak yaz.
3. Sonraki fazlara düşen yükümlülükler (Faz 2/3/4/5/7/11 DoD'leri) açıkça yazılmış mı?
4. Geri dönüş planı uygulanabilir mi (somut API/komut adıyla)?
5. ADR'ler arası çelişki var mı (ör. ADR-003 attribute tavanı ↔ ADR-002 HUD sayıları; ADR-004 ↔ Faz 7 Netherite kuralı)?

## İşler
- ADR metinlerini düzelt (yalnızca gerekirse); her birine `**Kapanış:** 2026-MM-DD, Opus 5.5 max gözden geçirdi — <değişiklik özeti | değişiklik yok>` satırı ekle.
- Yeni soru çıkarsa `docs/10` B2'ye ekle.
- `docs/journal/f00.md` sonuna "ADR kapanış turu" alt başlığı; `STATUS/PROGRESS.md` güncelle.

## Bitiş
```
✅ Faz 00b tamam · <ADR'lerde ne değişti> · Doğrulama: ADR-001..005 kapanış satırları
➡ SONRAKİ: Faz 01 · Model: Sonnet 5.5 · Effort: high
   Önce: /clear
   Başlat: "prompts/faz-01-iskelet.md dosyasını oku ve uygula."
   Neden bu model: Kararlar kesinleşti; iskelet mekanik iş.
   Açık sorular: <docs/10 B2>
```
