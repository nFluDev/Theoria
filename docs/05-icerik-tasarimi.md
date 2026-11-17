# 05 — İçerik tasarımı

Hepsi **taslak öneri**; isimler/dağılımlar senin onayınla değişir. Hepsi veri-güdümlü: bir satır = bir içerik.

## 1. Kademeler, madenler, öntanımlı özellikler
`idx` zırh merdiveni (14); alet/silah kolonu: ✓ = var. Sayılar `docs/06`.

| idx | Kademe | Kaynak (maden/ham madde) | Alet/Silah | Öntanımlı özellik (taslak) |
|--:|---|---|:-:|---|
| – | Odun / Taş | vanilla | ✓ (alet) | — |
| 0 | Deri | inek/at/tavşan derisi (mob drop) | zırh | Hafif: +%2 hız |
| 1 | **Kalay** | YENİ: Kalay Cevheri (y 40…110, dağ eteği) | zırh (alet opsiyonel) | — |
| 2 | Bakır | vanilla bakır | ✓ | Yıldırım çekme **yok**; ucuz onarım |
| 3 | **Bronz** | Alaşım: Kalay+Bakır (fırın) | zırh (alet opsiyonel) | Kırılmaz kaplama: +dayanıklılık |
| 4 | Demir | vanilla demir | ✓ | — |
| 5 | **Çelik** | Alaşım: Demir+Karbon Tozu (iskelet/kurt drop) | zırh (alet opsiyonel) | Keskinlik I (silah) / Koruma I |
| 6 | Altın | vanilla altın (güçlendirilmiş) | ✓ | Büyü yeteneği ↑ (enchantability 40), Yağma I |
| 7 | **Kobalt** | YENİ: Kobalt Cevheri (deepslate, y −60…−10) | ✓ | Derin Kazı: kazı hızı bonusu |
| 8 | Elmas | vanilla elmas | ✓ | Dayanıklılık II |
| 9 | **Titanyum** | YENİ: Titanyum Cevheri (dağ zirveleri, y>140) | zırh (alet opsiyonel) | Hafif ağır: sürüklenme azaltma |
| 10 | **Lonsdaleit** | YENİ sıkıştırılmış karbon: meteor krater cevheri **+** Elmas+Karbon Tozu sentezi | ✓ | Koruma II / Keskinlik II, kırılma direnci |
| 11 | **Mitril** | YENİ: Mitril Cevheri (derin, y<−40 + ender maden) | ✓ | Hız + hafiflik: koşu hızı +%4, dayanıklılık III |
| 12 | **Orikalkum** | YENİ: yüzen adalar / End dışı nadir damarlar | ✓ | Tamir (otomatik onarım), kademe-1 element dirençleri |
| 13 | **Platin** | YENİ: en derin/nadir; boss kasalarında | ✓ | Tam direnç seti, +1 katalizör slotu |

**Öntanımlı özellikler (Faz 6 + Faz 7a kararı):** yalnız *nitelik tabanlı* olanlar uygulandı (Deri +%2 hız, Mitril +%4 hız, Titanyum geri itme direnci, Altın büyü yeteneği 40, kademeye göre büyü yeteneği — `data/materials.csv`). Büyü/etki gibi davrananlar (Koruma/Keskinlik/Yağma/Dayanıklılık I-III, Tamir, element dirençleri) **Faz 7a'da "doğuştan sanal büyü" olarak tasarlandı (ADR-006 Karar 9, `data/balance/innate.csv`; Platin +1 yuva Karar 4); Bakır "ucuz onarım" karşılıksız bırakıldı**. Gerekçe: ref bütçesi (`docs/06 §3`, `ref_epf`) ve dayanıklılık eğrisi bu özellikleri zaten kapsıyor; varsayılan büyü olarak eklenseler bütçeyi iki kez sayar, taşlama taşıyla silinebilir ve parlama verirdi (`docs/10` Q34).
**Isim notları:** Karbon için *Lonsdaleit* (altıgen elmas); alternatif *Fulleren*. "Orihalcum" doğru yazımı Orichalcum / Orikalkum. Platin = Platinyum.
**Netherite (D6):** zırh/alet/silah **yok** (tarif, loot, smithing şablonu, bastion/ruined portal sandıkları temizlenir). Antik enkaz ve Netherite külçesi korunur → *evrensel katalizör*.
**Zincir (Faz 6, `python3 -I tools/check_gear.py` "tırmanış"):** odun→taş→bakır→demir→**kobalt**→(altın)→elmas→**lonsdaleit**→**mitril**→**orikalkum**→**platin**; her basamağın cevheri bir önceki kazmayı ister. Alaşımlar el tarifidir (`data/alloys.csv`): Bronz = 3 bakır + 1 kalay → 4 · Çelik = 2 demir + 1 karbon tozu → 2 · Lonsdaleit sentezi = 1 elmas + 2 karbon tozu. Karbon tozu iskelet/stray/bogged/wither iskeleti/kurt dropudur (`data/drops.csv`, global loot modifier; Faz 8 mob zinciri yeniden düzenler).
**Vanilla eşyalar (D6):** `iron/gold/diamond/copper/leather/wood/stone` item id'leri korunur, statları component override ile değiştirilir — **yeni id yok** onlar için; yalnızca vanilla'da olmayan kademeler (Kalay, Bronz, Çelik, Kobalt, Titanyum, Lonsdaleit, Mitril, Orikalkum, Platin) yeni item'dır.

## 2. Aletler ve silahlar
- Silah türleri v1: kılıç, balta (savaş), mızrak (yeni vanilla spear varsa), yay/arbalet için kademe çarpanı (ayrı eşya değil, damage bonusu).
- Alet: kazma, balta, kürek, çapa.
- Kademe → `harvest tag` (hangi blokları kazar), `mining speed`, `durability`, `enchantability`, `repair ingredient` (CSV'den).
- Altının vanilla zayıflığı kaldırılır; sadece "çok kırılgan" değil, *hızlı ve şanslı* (büyü yeteneği yüksek) karakteri korunur.

## 3. Katalizörler (v1: 32)  ✅ Faz 7a: sayılar `data/balance/catalysts.csv`, kurallar ADR-006
> Faz 7a kararları bu bölümü şöyle günceller: Tür A 17 katalizör (Güç, Çeviklik, Kritik Şansı/Hasarı, Yaşam Çalma, Zırh Plakası, Sertlik, Kalp, Diken, Sabitlik, Koşu, Sıçrayış, Tüy Düşüş, Doygunluk, Erişim, Kazı, Şans; "XP kazanımı" Bilgelik'e katıldı, "yük taşıma" çıkarıldı) · Tür B 11 element + **Onarım** ("dayanıklılık (onarım)") · **Menzil Gücü** (Q37) · Bilgelik · Netherite = 32. Yuva = `min(4, 1 + ⌊kademe/4⌋)`, Platin +1.
**Tür A — Nitelik (düz değer; seviye I-V, kademeye göre artar):**
saldırı hızı · düz zırh · zırh sertliği · kritik hasar · kritik şans · azami HP (Kalp) · satürasyon · koşu hızı · atlama · erişim · kazı hızı · şans · yaşam çalma · diken · yük taşıma(?) · düşme hasarı azaltma · knockback direnci · XP kazanımı · dayanıklılık (onarım).
**Tür B — Element/etki (datapack enchantment efekti, seviye I-V):**
Buz (yavaşlatma + buz hasarı) · Ateş (yakma) · Yıldırım (zincir) · Zehir · Solma · AoE (yarım daire) · Kanama · Işık (hayalet mob'lara bonus) · Gölge (arkadan bonus) · Fırtına (uçarken bonus; Kanat sinerjisi) · Gravite (çekim).
**Tür D — Bilgelik (XP) büyüsü + katalizörü:** `theoria:erudition` büyüsü, her alet/silah/zırhta; V seviye = **+%100 / +300 / +600 / +1000 / +1600** (vanilla XP orb'una ve mob XP'sine çarpan, Durum ekranı ömür-boyu sayacı dahil). Kaynak: boss/zindan loot'u, Kataliz Masası ("Bilgelik Katalizörü"), nadir kitap. Netherite +1 seviye ekler (teknik tavan S4; formül seviye > V için `+%1600 + 400·(L−5)`). Birden çok parçada (mainhand + 4 zırh) **toplanır** ama toplam tavan %2400 (taslak, Q43) ki 17× tırmanışı patlamasın. Etki **Java kancasıyla** (JSON efektleri parçalar arasında çarpılır; ADR-006 Karar 8).
**Tür C — Evrensel:** **Netherite**: eşyanın mevcut *her* büyüsünü +1 seviye artırır. Kural: yalnızca büyüsü olan eşyada çalışır; çiftleme için pahalı; sınır teknik tavana (255) kadar, azalan getiri.
**Elde edilme:** madenden *toz/kristal* + mob drop'larından *öz* + boss'tan *çekirdek* → Kataliz Ocağı'nda katalizör itemine dönüşür (hedef yuvası boşken; Faz 7b).

## 4. Mob'lar
### 4.1 Vanilla referans (oyundan dump edilecek — Faz 0 S5; aşağıdakiler yaklaşık, Normal zorluk)
| Mob | HP | Vuruş | Mob | HP | Vuruş |
|---|--:|---|---|--:|---|
| Zombi/Husk/Boğulmuş | 20 | 3 | Ravager | 100 | 12 |
| İskelet/Stray | 20 | ok ~2-5 | Warden | 500 | 30 (sonik 10) |
| Creeper | 20 | patlama ≤ ~43 | Wither | 300 | kafa 8 |
| Örümcek / Mağara ör. | 16 / 12 | 2 | Ender Ejderhası | 200 | ~6-10 |
| Enderman | 40 | 7 | Demir golem | 100 | 7-21 |
| Cadı | 26 | iksir ~6 | Blaze | 20 | ateş topu 5 / yakın 6 |
| Vindicator | 24 | 13 | Piglin Brute | 50 | 13 |
| Evoker | 24 | diş 6 | Hoglin/Zoglin | 40 | 6-8 |
| Guardian / Elder | 30 / 80 | 6 / 8 | Wither İskeleti | 20 | 8 + solma |
| Phantom | 20 | ~6 | Breeze | 30 | rüzgar topu |
| Creaking | koruyucu | — | (26.x yeni: Camel Husk, Parched — teyit edilecek) | | |
**Kural:** vanilla mob'lar bölgeye göre ölçeklenir (docs/06 §4), yeni mob'lar kendi kademesinde doğar.

### 4.2 Yeni mob ailesi (v1 hedef ≥ 40 tür; Faz 8 ilk 30)
Kategoriler, her biri kademe 1-13 aralığında 2-4 tür; drop'u merdivenin bir basamağını besler:
| Aile | Örnek türler | Kritik drop |
|---|---|---|
| Bataklık/Toprak | Çamur Gezgini, Kalay Kemirgen, Sazlık Yılanı | Kalay Tozu, Deri parçası |
| Yaban | Çelik Kurt, Boynuzlu Geyik (nötr), Dağ Ayısı | Deri, Karbon Tozu |
| Yeraltı | Kobalt Örümceği, Kaya Kurdu, Kristal Böcek | Kobalt Kristali |
| Dağ/Gökyüzü | Fırtına Kartalı, Rüzgâr Ruhu, Titan Boğası | Titanyum Parçası, Fırtına Özü |
| Alev/Kül | Köz Hayaleti, Kül Golemi | Ateş Özü |
| Buz | Kırağı Yırtıcı, Buzul Devi | Buz Özü |
| Gölge/Ruh | Soluk Hayalet, Gece Koşucusu | Gölge Özü |
| Kadim | Taş Muhafız, Orikalkum Bekçisi, Platin Kuklası | Mitril/Orikalkum/Platin Çekirdeği |
| Boss'lar (8) | her 2 kademede bir kapı boss'u | Kalp Kristali + katalizör çekirdeği |
**Teknik:** 12 arketip entity sınıfı (yırtıcı, nişancı, büyücü, sürü, uçan, dev, ejder, yılan, insansı…) + `species` data'sı (HP/hasar/boyut/davranış/ses/model parametresi). Model ve animasyonlar: geometrik/blocky stil, **tek tint'li doku** (LucidRem greyscale+tint yaklaşımı) → sanat işi küçük.

## 5. Biyomlar
- **Tabaka 1 (hazır):** Terralith (+TerraBlender). Regions Unexplored 26.x gelince eklenir.
- **Tabaka 2 (bizim, v1 ≥ 20):** Kalay Bataklığı, Çelik Bozkırı, Kobalt Kanyonu, Titan Zirveleri, Kül Çölü, Buz Mezarlığı, Fırtına Adaları (yüzen), Kristal Mağaraları, Gölge Ormanı, Kadim Harabeler, Meteor Krateri (Lonsdaleit), Işıldayan Vadi (Haven biyomu), Mitril Yarığı vb.
- Yerleşim: TerraBlender bölgesi, düşük ağırlık (~%25 yüzey), yüksek kademe biyomları uzak/derin/dikey (Kanatla ulaşılır). **Eşik kuralı:** Faz 9'a kadar ana dünyayı başlatma.

## 6. Yapılar ve Haven'lar
| Sınıf | Büyüklük | Mob | Zorluk | Loot |
|---|---|---|---|---|
| **Haven (Sığınak)** | Küçük (8×8) → Orta (30×30) → Büyük (80×80 malikâne/kale/gölde köşk) | **Yok** (spawn engelli, ışıklı, terk edilmiş estetik) | Kolay | Sandık odası; bazı sandıklar iyi loot (Lootr) |
| Harabe | Orta | Seyrek, kademe mob | Orta | Orta |
| Zindan | Orta-Büyük (yer altı, çok katlı) | Yoğun + mini boss | Zor | İyi + katalizör |
| Kale/Tapınak | Çok büyük | Boss | Çok zor | Efsanevi + Relic |
- **Haven kuralları:** survival'e uygun (yatak, ocak, depo odası hazır), mob spawn `structure_spawn_overrides` ile boş, çevre dekoratif (asma bahçe, gölet, fener). Hedef "burası artık benim" duygusu; yapı ücretsiz eşya vermez ama kısmen dolu sandıklarla yolculuğu ödüllendirir.
- **Üretim:** LucidRem'deki kod-üretimli yapı kütüphanesi yaklaşımı (palet × arketip); NBT'ler `generator/structures.py` ile üretilir → elle inşa gerekmez. Hazır paketten **Dungeons and Taverns 6.1.0** ek çeşitlilik.
- **OP itemler:** boss kasalarından ve Zindan'dan; Relics (Faz 12): gece görüşü yüzüğü, sonsuz yemek (tükenmeyen elma), vb.

## 7. Kazı kapıları (karar D6: "hepsi elmas kazmayla" yok)
Her blok/cevher bir **gereken alet kademesi** ister; daha düşük kademeyle kazılırsa **drop vermez** (vanilla davranışı) ve çok yavaş kırılır. Cevherin sertliği kademeyle artar, aletin kazı hızı da `2,0·1,28^idx` ile. Kaynak: `tools/balance/tiers.py --ores`; uygulama = `incorrect_for_*_tool` tag'leri (veri-güdümlü, Faz 6).
| Blok | Gereken alet idx | Sertlik | En düşük alet |
|---|--:|--:|---|
| Kömür | 0 | 3,0 | odun |
| Kalay | 1 | 3,0 | taş |
| Bakır | 1 | 3,3 | taş |
| Demir / Kırmızıtaş / Lapis | 2 | 3,6 | bakır |
| Altın | 4 | 3,9 | demir |
| Kobalt | 4 | 4,5 | demir |
| Elmas | 7 | 5,4 | kobalt |
| Obsidyen / Antik Enkaz | 8 | **vanilla (50 / 30)** — 9,0 kaldırıldı (Q36) | elmas |
| Titanyum | 8 | 6,6 | elmas |
| Lonsdaleit | 8 | 7,8 | elmas |
| Mitril | 10 | 9,6 | lonsdaleit |
| Orikalkum | 11 | 11,4 | mitril |
| Platin | 12 | 13,5 | orikalkum |
Alet idx'i: odun 0 · taş 1 · bakır 2 · demir 4 · altın 6 · kobalt 7 · elmas 8 · lonsdaleit 10 · mitril 11 · orikalkum 12 · platin 13. Kazı hızları: 2,0 · 2,6 · 3,3 · 5,4 · 8,8 · 11,3 · 14,4 · 23,6 · 30,2 · 38,7 · 49,5.
**Uygulama (Faz 6):** kapılar `incorrect_for_*_tool` etiketleridir (generator/gear.py ← `data/balance/ores.csv`), sertlik blok durumlarına yazılır; ayrıntı, vanilla etiketlerle birleşim ve doğrulama `docs/06 §10`. Deepslate varyantı yalnız taş ikizi varsa ×1,5; kobalt/mitril/platin yalnız deepslate'te bulunduğundan tablo değeri (4,5 / 9,6 / 13,5) bloğun kendisinindir.
**Sonuç:** elmasa kobalt, platine orikalkum kazma lazım; her kademe bir sonrakini kazmak için gereken anahtar. Yapılar (bedrock, reinforced deepslate vb.) vanilla kuralıyla kalır. Kritik yapı blokları (obsidyen portalı) elmas ister → erken oyunda yanlışlıkla kilitlenme yok.
