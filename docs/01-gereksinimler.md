# 01 — Gereksinimler (isteğin madde madde hali)

Her madde: **ne istendi → nasıl yorumladım → faz**. `⚠` = belirsiz, `docs/10-acik-sorular.md`'de soru var.

## A. Platform
| # | İstek | Yorum | Faz |
|---|---|---|---|
| A1 | Minecraft 26.2 veya 26.3 | **26.3** önerilir (mod erişimi 26.2'den iyi, bkz. docs/11). Faz 0 kesinleştirir ⚠ | 0 |
| A2 | Vulkan alt yapısıyla oynanacak; shaderpack desteklesin (Vulkan'da çalışan) | ✅ Karar D7: Vulkan çalışıyor. Shader = **Vitrail Shaders** + Complementary Reimagined. Kodumuz arka uç-bağımsız | 0, 5 |
| A3 | Normal, vanilla bir başlangıç | Yeni dünyada vanilla ilk 10 dk hissi: araçlar yavaş yavaş, tuhaf HUD dışında şaşırtma yok | 5 |
| A4 | Fantastic Wings, JEI, QoL, hedefe uygun modlar | Hazır modlar: `docs/11` | 5 |

## B. Arayüz
| # | İstek | Yorum | Faz |
|---|---|---|---|
| B1 | ESC menüsünde "Wings" butonu | Pause ekranına buton; LucidRem `PauseMenuHook` taşınır | 4 |
| B2 | Wings ekranı: dinamik kanat listesi, solda 3B animasyonlu oyuncu, oyun durmadan ekle/kaldır | Fantastic Wings kataloğunu çalışma anında okur; ekran oyunu durdurmaz; model sürüklenip döner. LucidRem §45.5 dersleri uygulanır | 4 |
| B3 | Kanatlar ölümden sonra kalıcı | Oyuncuya kalıcı attachment + `copyOnDeath` | 4 |
| B4 | HUD: 5 kalp + 5 zırh + sağda SAT ve binek sağlığı | Düzen `docs/04 §1`. ✅ Karar D1/D2: 5 kalp, kalp başı 4 çeyrek, 1 çeyrek = 1 HP; açlık da 5 sembol | 3 |
| B5 | Katmanlı renk: 21 HP'de ilk çeyrek yeni renk | LucidRem katman modeli (her 20 HP bir kat, tamamlanan kat zemin, yeni kat soldan dolar); "partial hearts" = çeyrek adım | 3 |
| B6 | Orta alanda ortalanmış sayısal zırh/can değeri | Her satır kendi boşluğunun ortasına hizalı; büyük sayı kısaltma (1,2K / 43,2B) | 3 |
| B7 | Durum ekranı (anime status): strength, saturation, hız, güç, dayanıklılık… | Seviye başına 4 puan; ekran `docs/04 §5` | 10 |
| B8 | Seviye eğrisi: L2 = vanilla 5. XP seviyesinin ham XP'si (55); **tavan 9999**; XP artıran büyü (+%1600'e kadar) | Harcanmayan "ömür boyu XP" (long) sayacı; eğri `docs/06 §5`; **"Bilgelik" büyüsü** `docs/05 §3` | 7, 10 |
| B9 | Curios + "maksimum" hali (envanterde buton → büyük alan) | Curios API + kendi geniş ekranımız + çok sayıda slot | 11 |
| B10 | Kozmetik giysi (güçsüz ama güzel zırhı görünüm olarak giy) + kapatma seçeneği + zırhlar da gizlenebilsin | Kozmetik slotları + istemci ayarı "kozmetiği göster / zırhı göster" | 11 |

## C. Oynanış ve İçerik
| # | İstek | Yorum | Faz |
|---|---|---|---|
| C1 | Kanatla özgürce uçmak | Fantastic Wings uçuşu; denge: uçuş dünyayı "küçültmesin" diye mob/yapı yerleşimi dikey düşünülür | 5, 9 |
| C2 | Bolca mob; kesince materyal düşer; vanilla can/güç öğrenilecek | Vanilla attribute dump (Faz 0) → `docs/05 §4`; mob roster + drop zinciri | 0, 8 |
| C3 | Zırh: deri, ara, bakır, ara, demir, ara, altın(demirden iyi), ara, elmas, ara, karbon, mitril, orikalkum, platin | 14 kademe, `docs/05 §1` | 6 |
| C4a | **Kazı kapıları:** madenler artık hep elmas kazma gerektirmez; her cevher kademeye göre zorlaşır (D6) | Cevher→gereken alet kademesi + sertlik, `docs/05 §7` | 6 |
| C4b | Alet+silah: odun, taş, bakır, demir, altın, ara, elmas, ara/karbon, mitril, orikalkum, platin | 11 kademe (+ veri satırıyla genişletilebilir) | 6 |
| C5 | Karbon için bilimsel ad | **Lonsdaleit** (altıgen elmas, meteor çarpmasında grafitten oluşur, elmastan sert) — alternatif: Fulleren ⚠ | 6 |
| C6 | Güçler vanilla'dan farklı/yüksek; chestplate dizisi 1-3-6-9-14-24-32-46-59-67-82… | Dizi aynen kullanıldı, 3 değerle genişletildi (98-118-142; Faz 2a simülasyonu, `docs/06 §2`) | 2, 6 |
| C7 | Kademeye göre öntanımlı büyü/geliştirme | Her kademede 1-2 doğal büyü/özellik `docs/05 §1` | 6 |
| C8 | Netherite zırh/alet/silah olmayacak; katalizör | ✅ D6: netherite eşyaları kaldırılır (tarif+loot+smithing); netherite = evrensel katalizör (+1 seviye tüm büyülere; teknik tavan S4'te ölçülür) | 7 |
| C9 | Kendi masamız: eşya + katalizör yerleştirilir, güç artar (Apotheosis tarzı) | **Kataliz Masası** `docs/04 §4`; tarif kolay ama bedava değil | 7 |
| C10 | Çok katalizör: saldırı hızı, koruma, kritik, HP, satürasyon, koşu hızı…; bazıları büyü verir: buz, AoE, ateş, yıldırım… | ~30 katalizör `docs/05 §3` | 7 |
| C11 | Mümkün olduğunca bol biyom | Terralith + TerraBlender + kendi 20-30 biyomumuz | 9 |
| C12 | Yeni dungeon, yapı, OP itemler | Kendi yapı üreticimiz + Dungeons & Taverns; OP itemler = Relics (v1.1) ve boss ödülleri | 9, 12 |
| C13 | "Burası artık benim" yerleri: terk edilmiş, güzel, mob çıkmayan, sandık odalı | **Haven** yapı ailesi: küçük/orta/büyük; mob yok; kilitsiz; ödül sandıkları | 9 |
| C14 | Büyük ama zorlu, küçük ama kolay yerler | Zorluk bandı: Haven (kolay) → Harabe → Zindan → Kale (zor) | 9 |

## D. QoL / macOS
| # | İstek | Yorum | Faz |
|---|---|---|---|
| D1 | Ctrl+Sol tık = sağ tık sorunu | **McMouser** (26.1/26.2 neoforge var, 26.3 yok) veya **Mac Input Fixes (NeoForged)** (26.3 var) | 5 |
| D2 | Ctrl+Q çalışmıyor | Mac Input Fixes ailesi; 26.3'te çalıştığı doğrulanmazsa kendi `theoria-macos` modülümüz (küçük mixin) | 5 |

## E. Süreç
| # | İstek | Yorum |
|---|---|---|
| E1 | Hangi aşamada hangi model/effort | `docs/08` + her `prompts/faz-*.md` başlığı |
| E2 | `/clear` / `/compact` gereken yerlerde "sonraki aşamada Sonnet 5.5 xHigh kullanın" diyen ön prompt | `CLAUDE.md` protokolü + `prompts/_devir-sablonu.md` |
