# Devir şablonu

Claude her faz/alt adım sonunda **aynen bu biçimde** yazar. Kullanıcı: önerilen aksiyonu yapar (`/clear` veya `/compact`), `/model` ile modeli ve effort'u ayarlar, "Başlat" satırını yapıştırır.

```
✅ Faz NN tamam · <1-2 cümle sonuç> · Doğrulama: <komut → sonuç>
➡ SONRAKİ: Faz MM (<ad>) · Model: <Sonnet 5.5 | Opus 5.5> · Effort: <high | xHigh | max>
   Önce: </clear | /compact odak: ...>
   Başlat: "prompts/faz-MM-<ad>.md dosyasını oku ve uygula."
   Neden bu model: <tek cümle>
   Açık sorular: <varsa, docs/10'a eklendi>
📊 %X tamamlandı (STATUS/PROGRESS.md "İlerleme" tablosundan; kullanıcı her bitişte ister)
```

## Faz içi ara devir (yarıda `/compact`)
```
⏸ Faz NN yarıda · bağlam dolu · Journal yazıldı (docs/journal/fNN.md).
➡ Önce: /compact odak: "<şu anki alt görev; açık hata; değişen dosyalar; alınan kararlar>"
   Model/effort aynı kalsın: <model> · <effort>
   Devam: "STATUS/PROGRESS.md'deki 'Bir sonraki adım'dan devam et."
```

## Zor çıktı uyarısı
```
⚠ Bu adım Opus 5.5 · xHigh ister (neden: <belirsiz 26.x API / çapraz-kesen tasarım / 2 başarısız deneme>).
   Yapılan: <özet>. Journal'a yazıldı.
   /clear sonrası başlat: "<çok kısa devam prompt'u + ilgili dosya yolları>"
```

## Bağlam kuralları (kısa)
- Model değişimi = `/clear` sınırı.
- Compact'tan önce journal; hata ayıklarken compact yok.
- Büyük çıktıyı bağlama dökme; grep/özetle; geniş keşif = Explore alt-ajanı.
