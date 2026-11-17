# 08 — Model, effort ve bağlam rehberi

Mevcut seçenekler: **Sonnet 5.5** ve **Opus 5.5**; effort: **high · xHigh · max**. Model ve effort'u siz seçersiniz (`/model` ve effort ayarı); Claude kendi kendine model değiştiremez. Bu yüzden her aşama sonunda **devir bildirimi** gelir.

## 1. Seçim kuralları
| Durum | Seçim |
|---|---|
| Tanımı net, çok dosyalı uygulama (ekran, entity, generator, paket) | **Sonnet 5.5 · xHigh** — varsayılan çalışma atı |
| Mekanik/veri/doküman/pack listesi, küçük düzeltme, log toplama | **Sonnet 5.5 · high** |
| Belirsiz tasarım, 26.x'te değişmiş/bilinmeyen API, çapraz-kesen mimari, denge modelleme, nedeni belli olmayan render/ağ/çökme hatası | **Opus 5.5 · xHigh** |
| Tasarım *incelemesi*, kod incelemesi, orta zorlukta karar | **Opus 5.5 · high** |
| Geri dönüşü pahalı, tek seferlik kararlar (ADR kapanışı, sürüm göçü) veya xHigh'ta **iki başarısız denemeden sonra** | **Opus 5.5 · max** |
| `max` ile Sonnet | kullanma: iki kez deneyip çözülmüyorsa Opus'a geç |

**Maliyet notu:** max en pahalıdır; planın toplam max kullanımı 3 kısa turla sınırlı (F0 ADR kapanışı, gerekirse F11 render kancası, F13 son inceleme).

## 2. Faz başına özet
| Faz | Birincil | Yedek/İkincil |
|---|---|---|
| 0 Spike | Opus xHigh | ADR kapanışı Opus max |
| 1 İskelet | Sonnet high | — |
| 2 Denge | 2a Opus xHigh → 2b Sonnet xHigh | hata ayıklamada Opus xHigh |
| 3 HUD | Sonnet xHigh | render API tuhaflıkta Opus xHigh |
| 4 Kanatlar | Sonnet xHigh | FW iç yapısı Opus high |
| 5 Pack/QoL | Sonnet high | — |
| 6 Gear | Sonnet xHigh | — |
| 7 Katalizör | 7a Opus high → 7b Sonnet xHigh | — |
| 8 Mob'lar | Sonnet xHigh | boss AI Opus high |
| 9 Dünya | 9a Opus high → 9b Sonnet xHigh | worldgen hata: Opus xHigh |
| 10 Durum | Sonnet xHigh | — |
| 11 Curios/Kozmetik | 11a Opus xHigh → 11b Sonnet xHigh | render çıkmazı: Opus max |
| 12 Relics | Sonnet high | — |
| 13 Yayın | Opus high inceleme + Sonnet high | son tur Opus max (opsiyonel) |

## 3. `/clear` ve `/compact` politikası
**`/clear` (yeni oturum) şu anlarda:**
- Her faz sınırında ve alt adım (2a→2b, 7a→7b…) sınırında. Önce `STATUS/PROGRESS.md` + `docs/journal/fNN.md` güncel olsun.
- **Model/effort değişirken** (önbellek zaten gidiyor; temiz başlamak ucuz).
- Bir hata ayıklama döngüsü çözüldü ve yeni iş başlayacaksa.

**`/compact` şu anlarda:**
- Aynı fazın içinde bağlam ≈ %60-70 dolduğunda ve iş yarıda ise.
- Mutlaka odak ver: `/compact odak: <şu anki alt görev, açık hata, değişen dosyalar, karar listesi>`.
- Compact'tan **önce** bulguları journal'a yaz (compact özeti kayıplıdır, journal değil).

**Yapma:**
- Hata ayıklamanın ortasında compact (hipotez listesi kaybolur) — önce journal.
- Aynı oturumda model değiştirmek (önbellek kaybı + bağlam uyumsuzluğu).
- Büyük dosya/log'u bağlama dökmek; `grep`/özet araçlarıyla oku. Geniş keşif için Explore alt-ajanı (bağlamı temiz tutar).

## 4. Devir bildirimi (her aşama sonunda Claude yazar)
```
✅ Faz NN tamam · <1-2 cümle sonuç> · Doğrulama: <komut → sonuç>
➡ SONRAKİ: Faz MM (<ad>) · Model: Sonnet 5.5 · Effort: xHigh
   Önce: /clear
   Başlat: "prompts/faz-MM-<ad>.md dosyasını oku ve uygula."
   Neden bu model: <tek cümle>
   Açık sorular: <varsa, docs/10'a eklendi>
```
Örnek: `➡ SONRAKİ: Faz 4 (Kanatlar) · Model: Sonnet 5.5 · Effort: xHigh · Önce: /clear — HUD bitti, kanat ekranı tanımı net, çok dosyalı uygulama.`

## 5. Faz içi tek-satır kural
Bir adım önerilen modelden zor çıkarsa Claude durur ve şunu yazar: `⚠ Bu adım Opus 5.5 · xHigh ister (neden: …). /clear sonrası şu prompt ile devam: …` — tahminle ilerlemez.
