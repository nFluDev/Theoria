# ADR-008 — Kademe genişletmesi (28 adım, zor üretim) — **TASLAK, kullanıcı onayı bekliyor**

**Durum:** Taslak · 2026-11-20 · **Etkiler:** Faz 6 (gear), 8 (mob/boss zinciri), 9 (worldgen — **donma kalkıyor**), 10 (stat bütçesi), Lootr. Kod yazılmadı: bu, onaydan sonra uygulanacak işin şartnamesi.

## İstek (kullanıcı, 2026-11-20)
- Mevcut maden/silah/zırh çok az; "3 günde biter". Ya üretim karmaşıklaştırılsın ya da **çok daha fazla ara maden** eklensin; oyun "madencilik simülasyonu" gibi kalmasın.
- Göğüslük zırhı için hedef dizi (28 adım, deri dahil): **2, 4, 7, 10, 13, 16, 20, 24, 29, 36, 41, 47, 51, 54, 59, 66, 72, 83, 99, 113, 124, 138, 149, 154, 164, 173, 183, 191.** Üst uçlara gerçek dünyada **günlerce** oynayarak ulaşılmalı.
- Sıkıştırılmış/"karbonlu" (karbonado benzeri) maden ve alaşımlar artırılmalı. Cevher/külçeler **Lootr sandıklarından** da belli şansla çıkmalı.
- JEI'da malzemenin nerede çıktığı görünsün (Just Enough Resources paketine eklendi).

## Bugün (14 kademe: deri + 13 malzeme)
Göğüslük: deri 1 · kalay 3 · bakır 6 · bronz 9 · demir 14 · çelik 24 · altın 32 · kobalt 46 · elmas 59 · titanyum 67 · lonsdaleit 82 · mitril 98 · orikalkum 118 · platin 142 (`tools/balance/tiers.py` `ARMOR`; zincir zırh şu an tabloda yok, vanilla). Kullanıcının dizisi mevcut kademelerin çoğuyla uyumlu (elmas 59 ✓, titanyum ≈ 66, lonsdaleit ≈ 83, mitril ≈ 99), platinin ötesine **6 yeni adım** (149…191) ve arada **~7 yeni adım** ister.

## Önerilen eşleme (ONAY GEREKLİ — adlar ve sıra benim önerim)
| # | Zırh | Malzeme | Kaynak (öneri) | | # | Zırh | Malzeme | Kaynak (öneri) |
|--:|--:|---|---|---|--:|--:|---|---|
| 1 | 2 | Deri | inek/at derisi | | 15 | 59 | Elmas | vanilla |
| 2 | 4 | Kalay | cevher (var) | | 16 | 66 | Titanyum | cevher (var) |
| 3 | 7 | Bakır | vanilla | | 17 | 72 | **Krom** | yeni cevher (dağ) |
| 4 | 10 | Zincir | vanilla zincir | | 18 | 83 | Lonsdaleit | sıkıştırılmış karbon (var) |
| 5 | 13 | Bronz | alaşım (var) | | 19 | 99 | Mitril | cevher (var) |
| 6 | 16 | Demir | vanilla | | 20 | 113 | Orikalkum | cevher (var) |
| 7 | 20 | Çelik | alaşım (var) | | 21 | 124 | **Adamantit** | yeni cevher (derin) |
| 8 | 24 | Altın | vanilla | | 22 | 138 | Platin | cevher (var) |
| 9 | 29 | **Gümüş** | yeni cevher | | 23 | 149 | **Karbonado çeliği** | alaşım: çelik + karbonado |
| 10 | 36 | Kobalt | cevher (var) | | 24 | 154 | **İridyum** | yeni cevher (boss/Lootr ağırlıklı) |
| 11 | 41 | **Nikel çeliği** | alaşım: nikel + çelik | | 25 | 164 | **Osmiyum** | yeni cevher (çok nadir) |
| 12 | 47 | **Tungsten** | yeni cevher | | 26 | 173 | **Sıkıştırılmış platin** | 9 külçe → sıkıştırma |
| 13 | 51 | **Tungsten karbür** | alaşım: tungsten + karbon | | 27 | 183 | **Nötronyum tozu alaşımı** | boss çekirdeği + osmiyum |
| 14 | 54 | **Vanadyum** | yeni cevher | | 28 | 191 | **Yıldız demiri** | yalnızca boss/Lootr sandıkları |
(Koyu = yeni. 13 yeni adım: 7 yeni cevher/malzeme + 6 alaşım/sıkıştırma. "Karbonlu/sıkıştırılmış" isteği 11, 13, 23, 26, 27'de karşılanıyor.)

## Boru hattının neresi 14'e bağlı (maliyet)
`tools/balance/{tiers,sim,mobs,catalysts,stats}.py` (`M.N`, kademe başına mob HP/hasar/XP çarpanları, ref bütçe, tempo `TIER_HOURS`) · `data/mobs.csv` (35 türün `tier`'ı) · `data/balance/*.csv` (`tiers`, `tools`, `ores`, `mob_*`, `catalyst*`, `pacing`) · `generator/gear*.py` (zırh/alet/doku boyama; yeni malzeme = `materials.csv` + `ores.csv` + `alloys.csv` satırı, doku **boyanmış** türetilir) · biyom/cevher etiketleri (`ores.csv` `biomes`, `generator/biomes.py`) · `docs/05`, `docs/06` tabloları · tüm `tiers`/`T13` sabitli GameTest'ler.
**İki yol:**
- **A (tam):** 28 *savaş kademesi* — mob bölgeleri, XP, bütçe, tempo yeniden kurulur. Temiz ama Faz 2/6/8/10'u yeniden açar (≈ 4-5 oturum).
- **B (önerilen): "yarım kademe".** 14 savaş kademesi kalır (mob/XP/bütçe/bölge aynı); her savaş kademesi **iki gear adımı** taşır (zırh değeri dizideki iki komşu sayı). Mob zorluğu bölgeyle, güç eğrisi gear adımıyla ince taneli büyür. `tiers.py`'ye `GEAR_STEPS` (28 satır) eklenir, mob tarafı `step // 2`'yi okur. Üst uç 142 → 191 olduğundan T13 mob hasarı/HTD bantları **yeniden ayarlanır** (tek sabit ailesi: `G_MOB_DMG`, `ARMOR_K*`).

## Üretim zorluğu (öneri)
Her adım için **zincir**: cevher → ham → külçe (ocak) → *ara ürün* (alaşım karışımı/sıkıştırma, Kataliz Ocağı'nda; 2-3 girdi) → parça. Yüksek adımlar boss çekirdeği/Kalp parçası + karbonado ister. Ara ürünler çoklu kullanım (alet, katalizör, alaşım). Mevcut 3 alaşım (bronz, çelik, + lonsdaleit) 8-10'a çıkar. **Hız hedefi:** adım 20'ye (113) ulaşmak ≈ 30-40 gerçek saat, 28'e (191) ≈ 100+ saat (kullanıcı "günlerce" dedi; tempo modeli `stats.py TIER_HOURS` ile birlikte ayarlanır).

## Dünya ve Lootr
- **Worldgen DONDURMASI (R10) bu iş için gevşetilir:** yeni cevherler yeni dünyada çıkar, **mevcut dünyalar** yeni cevheri kendiliğinden almaz (yalnız yeni chunk'lar; deneme yeni seed'de). `ores.csv` satırları + `generator/biomes.py` etiketleri + Chunky ön üretimi.
- **Lootr/yapı sandıkları:** küresel loot değiştirici (zaten `RemoveItemsLootModifier` var) ile sandık tablolarına kademe-ağırlıklı ham/külçe ekleme (Haven sandıkları düşük, kale/zindan/boss yüksek). `docs/06` loot oranları tek kaynak `data/drops.csv` + `mob_drops.csv`'ye eklenir.

## Önerilen iş bölümü
1. **Veri şeması + B yolu** (Opus 5.5 · xHigh): `GEAR_STEPS`, araç/silah ölçeği, T13 bant yeniden ayarı, `sim.py` yeşil.
2. **Malzeme/alaşım içeriği + doku + tarifler** (Sonnet 5.5 · xHigh): 13 yeni adım, `generator/gear*.py`, lang.
3. **Worldgen + Lootr** (Sonnet 5.5 · high): cevherler, etiketler, loot değiştirici, yeni-seed taraması.
4. **Stat bütçesi** (Sonnet 5.5): `stats.py` bütçe satırlarını yeni T13'e göre.

## Kullanıcıya sorular (`docs/10` Q86-Q90)
Q86 eşleme/adlar onayı (özellikle 4. adım zincir zırhı mı, 17-28'in adları) · Q87 A mı B mi · Q88 hız hedefi (saat) · Q89 yeni dünya mı, mevcut dünyaya geriye dönük mü (R10) · Q90 alet/silah ölçeği zırhla aynı oranda mı büyüsün (öneri: evet).
