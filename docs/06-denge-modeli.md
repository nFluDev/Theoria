# 06 — Denge modeli (KİLİTLİ — Faz 2a, 2026-11-10)

Tek kaynak: `tools/balance/tiers.py` (sabitler + formüller) → `data/balance/tiers.csv`, `data/balance/combat.csv`.
Simülasyon ve bant denetimleri: `tools/balance/sim.py` → `data/balance/sim_cases.csv` (oyun-içi testlerin beklenen değerleri).
Bu sayfadaki sayılar oradan üretildi (`tiers.py --md`, `sim.py`); bir sayı değişecekse **önce orada** değişir (§9).

## 1. İlkeler ve bantlar
1. Sayılar büyür ama **hissedilen zorluk sabit**: her kademede "eşit kademe mob'la dövüş", vanilla'nın ilk günü gibidir (çıplak oyuncu + tahta kılıç ↔ zombi: 7 vuruşta ölüm, 5 vuruşta öldürme).
2. Güç kazanmanın üç kanalı: **kademe** (maden/zırh/silah), **katalizör** (Kataliz Masası), **seviye** (Durum ekranı). Hepsi *düz* eklemedir; çarpan yok.
3. Vanilla sistemleri sayı enflasyonuna uyarlanır: zırh formülü (20 tavanı → tavansız, azalan getirili), büyü koruması, iyileşme, mob ölçekleme.
4. Altın "vanilla'daki gibi kırılgan değil": Altın kademesi demirin **üstünde** (idx 6 vs 4).

Üç oyuncu arketipi (`tiers.loadout`): **ref** = o kademede beklenen bütçe (§3) · **min** = yalnızca kademe takımı, can bütçesinin yarısı, büyü yok · **max** = zırh/hasar bonusu %100, +8 sertlik, can ×1,5, EPF 20.
"Zombi sınıfı" = vanilla zombi (20 can, 3 hasar, 2 zırh) × kademe çarpanı; zorluk Normal; HTD = ölüm vuruşu, HTK = öldürme vuruşu (kritiksiz, tam şarj).

| Bant (`sim.py` denetimi) | Hedef | Ölçülen (14 kademe) |
|---|---|---|
| ref HTD, eşit kademe | 6 – 8 | 6,7 – 7,6 |
| ref HTK, eşit kademe | 4,5 – 7 | 4,5 – 5,7 |
| min HTD | ≥ 2 | 2,2 – 7,2 |
| max HTD | ≤ 30 | 19 – 28 |
| ref, güçlü mob (enderman sınıfı, ×2,33 hasar) | ≥ 1,5 (asla tek atmaz) | 2,0 – 3,0 |
| ref, 2 kademe **üstü** mob | 2,5 – 5 | 3,5 – 4,1 |
| ref, 2 kademe **altı** mob | ≥ 10 | 12 – 17 |
| ref, Zor zorluk (×1,5) | ≥ 3,5 | 3,7 – 4,7 |
| ref düello bedeli (yerinde durup 1 mob öldürmek) | canın ≤ %60'ı | %26 – %45 |
| bir üst takımı giymenin değeri (bonussuz) | ≥ +%4 dayanma | +%5 – +%17 |
| göğüslük ömrü (eşit kademe vuruş) | ≥ 80 (vanilla deri) | 80 → 623 |

Eski "6-10 vuruşta öldürme" hedefi kaldırıldı: T0'da vanilla (tahta kılıç + zombi = 5 vuruş) ile çelişiyordu ve geç oyunda düello bedelini yükseltiyordu. Daha süngerimsi mob istenirse tek ayar `G_MOB_HP` (1,33 → 1,35 ≈ 5-7 vuruş) — `docs/10` Q20.

## 2. Kademe merdiveni ve türetilmiş sayılar
Göğüslük dizisi (kullanıcı): 1-3-6-9-14-24-32-46-59-67-82 → **genişletme**: 98-118-142 (son adımların ≈ +%20 büyümesi sürer; eski 98-112-124 ile T13 ölüm vuruşu 6,3'e, son yükseltmenin değeri +%9'a düşüyordu). Parça payları vanilla elmas oranı (kask %15 · göğüs %40 · pantolon %30 · bot %15); her parça en az 1 zırh verir; **takım = parçaların toplamı**.
Formüller: `kılıç = 4·1,30^idx` · `alet dayan. = zırh dayan. (göğüs) = 80·1,5^idx` · `takım sertliği = 2·(idx−8)` (titanyumdan itibaren) · `mob can × = 1,33^idx` · `mob hasar × = 1,31^idx` · `delici hasar × = ref can / 20` · `ref can = 20·1,2^idx`.

| idx | Kademe | Kask | Göğüs | Pantolon | Bot | Takım | Sertlik | Zırh dayan. (göğüs) | Kılıç | Alet dayan. | Mob can × | Mob hasar × | Delici × | Zombi sınıfı can / hasar | Ref can | Ref azaltma | Ölüm vuruşu | Öldürme vuruşu |
|--:|---|--:|--:|--:|--:|--:|--:|--:|--:|--:|--:|--:|--:|--:|--:|--:|--:|--:|
| 0 | Deri | 1 | 1 | 1 | 1 | 4 | 0 | 80 | 4.0 | 80 | 1.00 | 1.00 | 1.00 | 20 / 3.0 | 20 | %7 | 7.2 | 5.2 |
| 1 | Kalay | 1 | 3 | 2 | 1 | 7 | 0 | 120 | 5.2 | 120 | 1.33 | 1.31 | 1.20 | 27 / 3.9 | 24 | %10 | 6.8 | 4.9 |
| 2 | Bakır | 2 | 6 | 4 | 2 | 14 | 0 | 180 | 6.8 | 180 | 1.77 | 1.72 | 1.45 | 35 / 5.1 | 29 | %17 | 6.8 | 4.8 |
| 3 | Bronz | 3 | 9 | 7 | 3 | 22 | 0 | 270 | 8.8 | 270 | 2.35 | 2.25 | 1.75 | 47 / 6.7 | 35 | %24 | 6.8 | 4.7 |
| 4 | Demir | 5 | 14 | 10 | 5 | 34 | 0 | 405 | 11.4 | 405 | 3.13 | 2.94 | 2.05 | 63 / 8.8 | 41 | %29 | 6.9 | 4.5 |
| 5 | Çelik | 9 | 24 | 18 | 9 | 60 | 0 | 608 | 14.9 | 608 | 4.16 | 3.86 | 2.50 | 83 / 11.6 | 50 | %38 | 7.6 | 4.6 |
| 6 | Altın | 12 | 32 | 24 | 12 | 80 | 0 | 911 | 19.3 | 911 | 5.53 | 5.05 | 3.00 | 111 / 15.2 | 60 | %40 | 7.5 | 4.8 |
| 7 | Kobalt | 17 | 46 | 34 | 17 | 114 | 0 | 1367 | 25.1 | 1367 | 7.36 | 6.62 | 3.60 | 147 / 19.9 | 72 | %43 | 7.6 | 4.9 |
| 8 | Elmas | 22 | 59 | 44 | 22 | 147 | 0 | 2050 | 32.6 | 2050 | 9.79 | 8.67 | 4.30 | 196 / 26.0 | 86 | %43 | 7.3 | 5.0 |
| 9 | Titanyum | 25 | 67 | 50 | 25 | 167 | 2 | 3075 | 42.4 | 3075 | 13.02 | 11.36 | 5.15 | 260 / 34.1 | 103 | %46 | 7.3 | 5.2 |
| 10 | Lonsdaleit | 31 | 82 | 62 | 31 | 206 | 4 | 4613 | 55.1 | 4613 | 17.32 | 14.88 | 6.20 | 346 / 44.7 | 124 | %49 | 7.6 | 5.3 |
| 11 | Mitril | 37 | 98 | 74 | 37 | 246 | 6 | 6920 | 71.7 | 6920 | 23.03 | 19.50 | 7.45 | 461 / 58.5 | 149 | %51 | 7.6 | 5.4 |
| 12 | Orikalkum | 44 | 118 | 88 | 44 | 294 | 8 | 10380 | 93.2 | 10380 | 30.64 | 25.54 | 8.90 | 613 / 76.6 | 178 | %52 | 7.1 | 5.5 |
| 13 | Platin | 53 | 142 | 106 | 53 | 354 | 10 | 15570 | 121.2 | 15570 | 40.74 | 33.46 | 10.70 | 815 / 100.4 | 214 | %53 | 6.7 | 5.7 |

**Okuma:** "Ref azaltma / Ölüm vuruşu / Öldürme vuruşu" ref oyuncunun eşit kademe zombi sınıfına karşı değerleridir. Zırhın payı kademeyle büyür (%7 → %53): mob hasarı (1,31^idx) can bütçesinden (1,2^idx) hızlı büyür, aradaki farkı zırh kapatır. Takım sertliği (parça başına takımın 1/4'ü) kullanıcının dizisindeki son kademelerin yavaşlayan zırh artışını telafi eder.

## 3. Referans bütçe — Faz 7 (katalizör) ve Faz 10 (seviye) bunu tutturmalı
Ref oyuncu kademe `idx`'te şunlara sahiptir (`tiers.csv`: `exphp`, `ref_bonus`, `ref_epf`):
- **Can** `20·1,2^idx` (20 → 214). Önerilen kaynak dağılımı (T13'te +194): seviye ≈ +80 · "Kalp" katalizörleri ≈ +70 · kalp ödül eşyaları ≈ +44.
- **Düz zırh bonusu** = takımın `ref_bonus`'u, **düz hasar bonusu** = kılıcın `ref_bonus`'u: T0'da 0, T4'ten itibaren **%25** (T13: +88 zırh, +30 hasar). Öneri: yarısı seviye, yarısı katalizör.
- **Büyü koruması** `EPF = 2·(idx−3)`, en çok 16 (T11+ = Koruma IV × 4).
- min ↔ max arası (bonus %0 – %100, can ×0,5 – ×1,5) tasarlanmış yatırım aralığıdır; HTD 2 – 30 bandı bunun içindir.

⚠ **Faz 10 girdisi** (`sim.py --budget`): `docs/04 §5`'teki taslak puan değerleri (Vitalite +4 can, Dayanıklılık +2 zırh, Güç +0,5 hasar; 4 puan/seviye) bu bütçeye göre çok cömert — T13 bütçesinin tamamı yalnızca seviye puanıyla **L40**'ta dolar; L50'de bütün puanlar Vitalite'ye giderse ref HTD 6,7 → 31. Puan değerleri/yumuşak tavanlar bu bütçeye göre ayarlanmalı (D9); erken oyunda tek puanın bir takım kadar zırh vermesi de (T1 takımı 7 zırh ↔ 1 puan +2) aynı sorunun parçası.
HUD: renk mutlak HP'dir (kat = 20 HP, 9 renkli palet, son renk beyaz kalıcı); 214 can = 180'in ötesi → tam beyaz (`docs/04 §1`, D16).

## 4. Mob ölçekleme kuralı
Mob'un *kademesi* (spawn bölgesi/derinlik — atama kuralı Faz 8/9) üç çarpan seçer (`tiers.csv`):
- `mob_hp_mult` (1,33^idx) → azami can.
- `mob_dmg_mult` (1,31^idx) → mob'un **neden olduğu, zırhla azaltılabilen her hasar** (yakın dövüş, ok, patlama) ve mob'un kendi zırhı. Yalnızca `attack_damage` attribute'unu çarpmak yetmez: iskelet oku, creeper patlaması, blaze ateş topu attribute'tan gelmez.
- `mob_bypass_mult` (= ref can / 20; 1,2^idx) → zırhı **delen** hasar (`#bypasses_armor`: cadı iksiri, evoker dişleri, sonik patlama). Zırh bu hasarı kesemediği için can bütçesiyle aynı hızda büyür; hasar çarpanıyla büyüseydi T13 cadısının tek iksiri ref canın %64'ünü alırdı (vanilla ve bu kuralla: ≈ %20-30).
Vanilla mob'lar taban değerlerini korur (`data/vanilla_mobs.csv`), kademe çarpanı üstüne gelir → T0 bölgesi = vanilla. Kanatla uzağa gitmek ödüllü ve tehlikeli: bölge bandı yatay + dikey.

Faz 8 için (`sim.py --mobs`): 
- Ref oyuncuyu tam candan tek atanlar yalnızca tasarım gereği ağır olanlar: warden, giant, T8+ demir golem. Ravager (×4) T13'te sınırda (HTD 1,02) → elit mob'lara kademe bandı sınırı ya da çarpan indirimi düşün.
- Kendi mob'larımız için kural: taban hasar ≤ 7 (enderman sınıfı) "güçlü", üstü "elit/boss"; ref HTD ≥ 2 kalmalı.
- Elde silah taşıyan mob'lar (vindicator baltası, piglin kılıcı…): D6 ile vanilla silah statları değişince taban hasarları da değişir; ölçek çarpanı silah hasarını da çarpacağından Faz 6/8'de `sim.py --mobs` yeniden koşulmalı.
- Mob zırhı yeni formülde: vanilla zombi (2 zırh) tahta kılıca karşı %3; kademe çarpanıyla T13 zombi (66 zırh) platin kılıca karşı ≈ %5 → mob zırhı öldürme vuruşunu bozmaz; kabuklu/zırhlı mob'lar (shulker 20+) anlamlı kalır.
- Zorluk: vanilla Zor ×1,5 hasar aynen kalır (ref HTD 3,7 – 4,7); paketin referans zorluğu Normal.

## 5. XP eğrisi (Durum ekranı) — tavan 9999
`R(L)` = L'ye ulaşmak için gereken **ömür boyu ham XP** (`long`). L ≤ 200: `55·(L−1)^2.2`. L > 200: seviye başı maliyet L ile doğrusal artar (`R(L)=R(200)+c200/400·(L²−200²)`; c200 = L199→200 maliyeti ≈ 70K). Üretim: `python3 -I tools/balance/tiers.py --xp`.
| L | gereken ham XP | toplam beceri puanı (4/seviye) |
|--:|--:|--:|
| 2 | 55 | 4 |
| 3 | 253 | 8 |
| 5 | 1.161 | 16 |
| 10 | 6.913 | 36 |
| 20 | 35.778 | 76 |
| 30 | 90.706 | 116 |
| 50 | 287.603 | 196 |
| 100 | 1.351.326 | 396 |
| 200 | 6.278.262 | 796 |
| 300 | 14.980.420 | 1.196 |
| 500 | 42.827.327 | 1.996 |
| 1000 | 173.359.699 | 3.996 |
| 2500 | 1.087.086.306 | 9.996 |
| 5000 | 4.350.395.618 | 19.996 |
| 9999 | 17.400.152.176 | 39.992 |
Vanilla 5. XP seviyesi = 5²+6·5 = **55** ham XP (✓ L2 eşiği). **Bilgelik büyüsü** (+%1600 → 17×) ve mob XP'sinin kademeyle ölçeklenmesi bu sayıları erişilebilir kılar: ör. T13 bir mob'dan 50K XP × 17 = 850K → L9999 ≈ 20K öldürme. Hedef: L50 ≈ 30-40 saat, L200 ≈ 150 saat, L9999 uzun vadeli "sonsuz" hedef ⚠ Faz 10 sim ile ayarlanır. `long` şart (17,4 milyar > int).
Beceri puanı enflasyonu (39.992 puan) için her stat'ın yumuşak tavanı + azalan getirisi (Q3); stat bonuslarının kademe başına bütçesi §3.

## 6. Hasar azaltma (kilitli) — uygulama ADR-003
**Zırh** (`Reduction.ARMOR` modifier'ı; `#bypasses_armor` kaynaklarda uygulanmaz):
```
tau  = 8 / (8 + T)                         T = armor_toughness
oran = min(0,90, A / (A + 30 + 8·H·tau))   A = getArmorValue() (aşağı yuvarlanmış), H = zırh öncesi hasar (ADR-003 tanımı)
oran = clamp(silahın armor_effectiveness etkisi(oran), 0, 1)        # vanilla ile aynı adım (Breach)
```
- Akılda tutma kuralı: **zırhın, yediğin vuruşun 8 katıysa azaltma ≈ %50** (küçük vuruşlarda 30 sabiti bunu biraz düşürür). Küçük vuruş çok, büyük vuruş az azaltılır; hasar yine de vuruşla tekdüze artar (büyük vuruş asla daha az vurmaz).
- **Sertlik** büyük-vuruş terimini küçültür: T = 8 yarılar (vanilla'daki `2 + T/4` ile aynı biçim). Örnek: 300 zırh, 200'lük vuruş → T 0: %16 · T 8: %27 · T 20: %38. Sertlik sonsuz olsa bile oran `A/(A+30)`'u ve %90'ı geçemez.
- **Tavan %90** yalnızca çok alt kademe mob'larda görülür (T13 ref ↔ T0 zombi: %90); eşit kademede %90 için 9 × (30 + 8·H·tau) zırh gerekir (T13, takım sertliğiyle: ≈ 3.500).
- Taslaktan farkı (`sim.py --fit`, 1536 noktalı ızgara, 46'sı bütün bantları geçiyor): `60 + 4·H` ve mob hasarı 1,28^idx, ref bütçeyle geç oyunda HTD 11'e çıkıyordu; `30 + 8·H`, 1,31^idx ve sertlik terimiyle bant 6,7 – 7,6. Yeşil bölge dar bir sırt (düşük k1 / yüksek k2); seçilen nokta en sağlam gruptan, yuvarlak sayılı olan.

**Büyü koruması** (`Reduction.ENCHANTMENTS` modifier'ı): vanilla azaltmasının **yarısı** (`ench_scale = 0,5`) → EPF puanı başına %2, en çok %40 (vanilla %4 / %80). Koruma IV × 4 = %32; Netherite ile V × 4 = %40. Neden: vanilla ölçeğinde büyülü/büyüsüz fark 2,8 kat olur, büyü zorunlu hale gelir, eşit kademe mob ya büyüsüz oyuncuyu ezer ya büyülüye dokunamaz. Yan etki: özel korumalar da yarılanır (Tüy Düşüşü IV %48 → %24) — düşme hasarı sabit kaldığı ve can büyüdüğü için net etki yine vanilla'dan hafif.
**Direnç efekti**: vanilla (%20/seviye). **Ayrı bir toplam tavan yok**: ekipmandan en fazla zırh %90 × büyü %40 → hasarın en az %6'sı geçer; Direnç geçici olduğu için serbest (işaret ışığı II ile %3,6).
Azaltmalar çarpımsaldır: `son = H · (1 − zırh) · (1 − 0,2·Direnç) · (1 − 0,02·min(EPF, 20))`.

## 7. İyileşme ve zırh aşınması
- **İyileşme can yüzdesiyle ölçeklenir:** her iyileşme miktarı `azami can / taban can (20)` ile çarpılır (`LivingHealEvent`); vanilla zamanlaması ve açlık maliyeti aynı kalır. Sonuç her kademede vanilla'nın 20 candaki hissi: yavaş regen (açlık ≥ 18) %1,25/sn → sıfırdan tam can 80 sn; doygun hızlı regen %10/sn; 1 satürasyon puanı ≈ azami canın %3,3'ü; Şifa II iksiri ≈ %40. Ölçeksiz kalsaydı T13'te tam can 14 dk sürerdi. (Eski taslak "%0,5/sn" kaldırıldı — vanilla'dan 2,5 kat yavaştı.)
- **Zırh aşınması vanilla kuralında kalır** (parça başı `max(1, H/4)`; `ArmorHurtEvent` kancası yok). Dengeyi dayanıklılık eğrisi sağlar: `80·1,5^idx` ile göğüslük eşit kademe vuruşla 80 (deri) → 623 (platin ≈ vanilla netherite 592) vuruş dayanır; eski `1,33^idx` ile geç oyunda ≈ 130'da kalıyordu. Elit vuruşlar 4 kat hızlı yıpratır (platin: 156 vuruş).
- Ölçeklenmeyenler (bilinçli, v1): emilim kalpleri (altın elma +4), açlıktan ölme, **çevre hasarı** (düşme, lav, boğulma sabit → yüksek canlı oyuncuya göreli olarak hafifler; `docs/10` Q19).

## 8. Kilitli parametreler ve Faz 2b girdileri
| Parametre | Değer | Nerede |
|---|---|---|
| `armor_k1` / `armor_k2` / `armor_cap` | 30 / 8 / 0,90 | `combat.csv` |
| `toughness_half` | 8 | `combat.csv` |
| `ench_scale` | 0,5 | `combat.csv` |
| attribute teknik tavanları (denge değeri değil; jar'a gömülü okunur) | zırh 1.000.000 · sertlik 1.000 · azami can 1.000.000 · saldırı hasarı 1.000.000 | `combat.csv` (`attr_max_*`) |
| kademe çarpanları, takım sertliği, dayanıklılık, ref bütçe | §2 tablosu | `tiers.csv` |

**Faz 2b yaptı (2026-11-11; kanıt ve ayrıntı ADR-003 "Faz 2b sonucu", `docs/journal/f02.md`):**
1. `tiers.csv` + `combat.csv` → datapack JSON → `TierRegistry`; `attr_max_*` jar'a gömülü, mod kurucusunda uygulanır.
2. `LivingIncomingDamageEvent`: zırh modifier'ı (§6 formülü) + büyü modifier'ı (`vanilla × ench_scale`).
3. Mob ölçekleme (§4 üç çarpan) ve iyileşme ölçeği (§7).
4. Testler `data/balance/sim_cases.csv`'yi beklenen değer olarak kullanır: 26 senaryo (zırh/sertlik/EPF/Direnç/delici + 14 ref satırı). `/theoria test balance` = `armor300_vs_t8` satırı: 300 zırhlı kurban, kademe 8 zombi sınıfı vuruşu 26,02 → **11,51** (±%10).

**Ölçülen (oyun içi, gerçek `hurtServer`):** 26 satırın hepsi simülasyonla ≤ %0,025 (yalnızca float32 yuvarlaması; `armor300_vs_t8` → 11,5143 / 11,5144) — mob ve oyuncu kurbanında; 14 kademede ref oyuncunun eşit kademe zombiyi öldürme vuruşu `tiers.csv` `kills` sütunuyla birebir (4,5 – 5,7). Mob hasar çarpanı Kolay zorlukta oyuncu ölçeğinden **sonra** uygulanır (taban 3 → 2,5 × 8,673 = 21,68; sırası ters olsaydı 14,01). Doğal rejenerasyon 214 canda 10 tickte +10,7 (hızlı) / 80 tickte +10,7 (yavaş) = vanilla'nın 20 candaki %5'i.

| Gömülü teknik dosya | İçerik | Kaynak |
|---|---|---|
| `data/theoria/balance/combat/default.json` | `armor_k1/k2/cap`, `toughness_half`, `ench_scale` (datapack; sunucu `/reload` ile yeniler) | `tiers.py` ← `combat.csv` |
| `data/theoria/balance/tier/<id>.json` | kademe başına bir dosya (parçalar, sertlik, dayanıklılık, kılıç, üç mob çarpanı, ref bütçe) | `tiers.py` ← `tiers.csv` |
| `theoria/technical_caps.json` | attribute teknik tavanları (jar'dan, mod kurucusunda) | `tiers.py` ← `combat.csv` `attr_max_*` |

## 9. Yeniden ayarlama
`tiers.py`'de sabiti değiştir → `python3 -I tools/balance/tiers.py` → `python3 -I tools/balance/sim.py --cases` → `python3 -I tools/balance/sim.py` (bantlar yeşil mi; `--fit` komşuları gösterir) → bu sayfanın §1-§2 tablolarını `--md` çıktısıyla güncelle. `tools/check_data.py` (preflight) bayat CSV'yi ve kırmızı bandı yakalar.
Açık kalanlar: ~~katalizör başına stat değerleri~~ (Faz 7a ✅ → §11) · stat puan değerleri ve yumuşak tavanlar (Faz 10, bütçe §3) · mob kademesi atama kuralı ve elit mob'lar (Faz 8/9) · mob XP ölçeği (Faz 8/10).

## 10. Gear tabloları — alet/silah türleri (Faz 6, `tools.csv`)
Kılıç dizisi (`4·1,30^idx`, §2) tek referanstır; öteki türler ondan **oranla** türer (`tools/balance/tiers.py` `TOOL_KINDS`; vanilla elmas/demir setinden okundu). Hasar = tooltip'te **görünen toplam** (oyuncunun taban 1'i dahil → item niteliği `hasar − 1`); hız = saniyede vuruş (taban 4 → nitelik `hız − 4`); kazı hızı `2,0·1,28^idx` (yalnız kazma/balta/kürek/çapa); dayanıklılık `80·1,5^idx` (tüm türlerde aynı).
| Tür | Hasar oranı | Vuruş hızı | Not |
|---|--:|--:|---|
| kılıç | ×1,00 | 1,6 | referans |
| balta | ×1,50 | 1,0 | yavaş ama ağır (vanilla demir: 9 vs 6); kalkan devre dışı bırakma vanilla'da |
| kazma | ×0,65 | 1,2 | |
| kürek | ×0,75 | 1,0 | |
| çapa | ×0,25 | 2,0 | neredeyse silah değil (vanilla çapa 1 hasar) |
| mızrak | ×0,60 | `1/saldırı süresi` (0,87–1,54) | vurma/atılma hasarı = taban + `floor(bağıl hız × çarpan)`: ikinci terim vanilla sabiti (≈ 10) kalır, yüksek kademede önemsizleşir; asıl hasarı nitelik taşır |
Sayılar (`python3 -I tools/balance/tiers.py --tools`; hasar @ hız):
| alet | idx | sword | axe | pickaxe | shovel | hoe | spear | dayaniklilik | kazi hizi |
| wood | 0 | 4.0 @1.6 | 6.0 @1.0 | 2.6 @1.2 | 3.0 @1.0 | 1.0 @2.0 | 2.4 @1.538 | 80 | 2.0 |
| stone | 1 | 5.2 @1.6 | 7.8 @1.0 | 3.4 @1.2 | 3.9 @1.0 | 1.3 @2.0 | 3.1 @1.333 | 120 | 2.6 |
| copper | 2 | 6.8 @1.6 | 10.2 @1.0 | 4.4 @1.2 | 5.1 @1.0 | 1.7 @2.0 | 4.1 @1.176 | 180 | 3.3 |
| iron | 4 | 11.4 @1.6 | 17.1 @1.0 | 7.4 @1.2 | 8.6 @1.0 | 2.9 @2.0 | 6.8 @1.053 | 405 | 5.4 |
| gold | 6 | 19.3 @1.6 | 29.0 @1.0 | 12.5 @1.2 | 14.5 @1.0 | 4.8 @2.0 | 11.6 @1.053 | 911 | 8.8 |
| cobalt | 7 | 25.1 @1.6 | 37.7 @1.0 | 16.3 @1.2 | 18.8 @1.0 | 6.3 @2.0 | 15.1 @1.053 | 1367 | 11.3 |
| diamond | 8 | 32.6 @1.6 | 48.9 @1.0 | 21.2 @1.2 | 24.5 @1.0 | 8.2 @2.0 | 19.6 @0.952 | 2050 | 14.4 |
| lonsdaleit | 10 | 55.1 @1.6 | 82.7 @1.0 | 35.8 @1.2 | 41.3 @1.0 | 13.8 @2.0 | 33.1 @0.952 | 4613 | 23.6 |
| mithril | 11 | 71.7 @1.6 | 107.6 @1.0 | 46.6 @1.2 | 53.8 @1.0 | 17.9 @2.0 | 43.0 @0.87 | 6920 | 30.2 |
| orichalcum | 12 | 93.2 @1.6 | 139.8 @1.0 | 60.6 @1.2 | 69.9 @1.0 | 23.3 @2.0 | 55.9 @0.87 | 10380 | 38.7 |
| platinum | 13 | 121.2 @1.6 | 181.8 @1.0 | 78.8 @1.2 | 90.9 @1.0 | 30.3 @2.0 | 72.7 @0.87 | 15570 | 49.5 |

**Kimlerde:** vanilla `wooden/stone/copper/iron/golden/diamond` (D6: aynı id, statlar `ModifyDefaultComponentsEvent` ile) + yeni `cobalt · lonsdaleit · mithril · orichalcum · platinum` (kılıç, balta, kazma, kürek, çapa, mızrak = 66 öğe). Kalay/Bronz/Çelik/Titanyum alet vermez (`materials.csv` `tools=0`; bir satırı `1` yapmak + `tools.csv` satırı eklemek yeter).
**Zırh** `tiers.csv`'den aynen (§2); parça dayanıklılığı = göğüslük × vanilla birim oranı (11/16, 1, 15/16, 13/16; yarım yukarı yuvarlanır); takım sertliği parça başına çeyrek. **Zırhta nitelik tabanlı öntanımlı özellikler** (`materials.csv` `traits`): Deri takımı +%2 hız, Mitril +%4 hız (`ADD_MULTIPLIED_BASE`, parça başına ¼), Titanyum 0,2 geri itme direnci (parça başına 0,05). Büyü gibi davrananlar (Koruma/Keskinlik/Yağma/Dayanıklılık/Tamir/element dirençleri/katalizör yuvası) **Faz 7a'ya** bırakıldı — `docs/10` Q34.

### Kazı kapıları (docs/05 §7'nin uygulaması)
`tiers.py ORES` → `data/balance/ores.csv`. Her cevherin gereken alet idx'i bir **kapı etiketidir** (`theoria:gate/<idx>`); her alet kademesinin `incorrect_for_*_tool` etiketi = (karşılamadığı vanilla `needs_*_tool` seviyeleri) ∪ (kendi idx'inden büyük kapılar). Vanilla araçlar vanilla etiket adlarını (`replace`), yeni araçlar `theoria:incorrect_for_<id>_tool` kullanır; üçüncü taraf modların `needs_iron_tool` gibi etiketlerine saygı gösterilir. Kırmızıtaş vanilla'da `needs_iron_tool`daydı, bizde bakır yeter → etiketten `remove` ile çıkarıldı. Cevher sertliği vanilla blok durumlarına AT (`BlockStateBase.destroySpeed`) ile yazılır; deepslate varyantı yalnız taş ikizi varsa ×1,5 (vanilla oranı), yalnız-deepslate cevherde (kobalt/mitril/platin) tablo değeri bloğun kendisinindir.
**Sapma:** docs/05 §7'deki Obsidyen/Antik Enkaz sertliği 9,0 **kaldırıldı** — vanilla 50/30 kalır (kapı idx 8 aynen): 9,0 elmas kazmayla 1 sn'den kısa sürer, portal/yıkım korumasını yok ederdi (`docs/10` Q36).
**Doğrulama:** `check_gear.py` (kapı çözümlemesi oyundan bağımsız) · GameTest `gear_gate_matrix` (264 çift) · `/theoria test gear` · `gear_mining` (gerçek kırma ilerlemesi: bakır kazma demir cevherini ≈164 tikte, odun kazma 900 tikte — vanilla 30↔100 cezası).

### Elde silah taşıyan mob'lar (Faz 8 sonucu — `docs/10` Q35)
Mob'un vuruşu = kendi `attack_damage` tabanı + elindeki eşyanın niteliği. Vanilla silahların niteliği D6 ile yükseldi; oyuncu eşyası statları korunurken mob'larda `MobHooks` el silahı bonusunu `data/balance/mob_weapons.csv`'deki vanilla toplamına normalize eder:
| Silah (kim taşır) | vanilla toplam | yeni toplam | oran |
|---|--:|--:|--:|
| tahta kılıç | 4 | 4,0 | ×1,0 |
| taş kılıç (wither iskeleti) | 5 | 5,2 | ×1,04 |
| demir kılıç (zombi/iskelet nadiren) | 6 | 11,4 | ×1,9 |
| altın kılıç (piglin) | 4 | 19,3 | ×4,8 |
| demir balta (vindicator) | 9 | 17,1 | ×1,9 |
| altın balta (piglin brute) | 7 | 29,0 | ×4,1 |
| elmas kılıç | 7 | 32,6 | ×4,7 |
Bu tablo oyuncunun D6 sonrası silah değerlerini gösterir; mob'lar bunları almaz. Q35 kararı: mob'un kendi `attack_damage` tabanı aynen kalır, mob elindeki item bonusu vanilla değerine çekilir ve ardından `mob_dmg_mult` hasar hattında bir kez uygulanır. GameTest bunu zombi tabanı 3 için altın kılıçta 6, demir baltada 11, boş elde 3 olarak ölçer. `sim.py --mobs` tablo başlığındaki gibi yalnızca attribute taban hasarını (elde silah/mermi/patlama hariç) raporlar; özel mob TTK bantları `tools/balance/mobs.py` ile denetlenir. Vanilla Ravager T13'te HTD ≈1,02 ile sınırda kalır; kademe/formül tavanı bu fazda değiştirilmedi.


## 11. Katalizör tabloları (Faz 7a, `tools/balance/catalysts.py`)
Değer = `cap × eğri[L] × taban(kademe)`; eğri `0,2 · 0,4 · 0,6 · 0,8 · 1,0`; taban: sabit · kılıç hasarı · parçanın zırhı · ref can açığı (`exphp − 20`) · kazı hızı. Katalizör ref bütçesinin **yarısını** verir (§3: `ref_bonus/2`, Kalp ≈ %36); öbür yarısı Durum ekranıdır.
| Katalizör (V) | cap | taban | ref (IV) |
|---|--:|---|---|
| Zırh Plakası | %16 | parça zırhı | takımda %12,8 (talep %12,5) |
| Güç | %16 | kılıç hasarı | %12,8 (talep %12,5) |
| Kalp | %11,5 | ref can açığı / parça | 4 parçada %37 (talep %36) |
| Çeviklik | +0,40 | saldırı hızı | kılıç DPS ×1,25 (V) |
| Kritik Şansı / Hasarı | 0,25 / 0,50 | sabit | beklenen ×1,25 (ikisi V) |
| Buz, Ateş / Yıldırım, Zehir, Solma / AoE / Kanama | %10 / %12 / %15 / %14 | kılıç hasarı | vuruş başı ek hasar (taslak) |
| Işık, Gölge, Fırtına | %30 | kılıç hasarı | koşullu (sayımda ×0,4) |
`python3 -I tools/balance/catalysts.py --report` her kademe için talep/arz, gereken seviye, **katalizör-maks savunma** (parçada tüm yuvalar dolu: HTD 6,9-9,2, ref'in en çok ×1,24'ü) ve **katalizör-maks silah DPS** (×1,25 T0 → ×2,05 T13) tablolarını basar; bantlar (`≤ HTD 30`, `≤ ×1,6`, DPS `≤ ×2,2`) `--check`'te. Örnek (T8 elmas kılıç 32,6, 3 yuva): Çeviklik IV + Kritik Şansı III + Buz II → DPS ×1,34. Netherite: Koruma IV ×4 + 1 = EPF 20 (tavan); maliyet 1-2-4-6-9 külçe. Bilgelik: V = %1600, VI = %2000, toplam ≤ %2400. Tüm kurallar ADR-006.
