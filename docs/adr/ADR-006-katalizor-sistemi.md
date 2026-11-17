# ADR-006 — Katalizör sistemi: component şeması, kurallar, tavanlar, maliyetler, UX

**Durum:** Kabul (taslak sayılar kullanıcı onayına açık: `docs/10` B9, Q43-Q47) · 2026-11-11 (Faz 7a, Opus 5.5 high için yazıldı, Sonnet 5.5 uyguladı) · **Etkiler:** Faz 7b (uygulama), Faz 8 (kaynak damlaları), Faz 10 (Durum ekranı aynı nitelikleri kullanır), Faz 13 (ağ/şema donması)
> **Faz 7b güncellemeleri (2026-11-11): aşağıdaki "Faz 7b sonucu" bölümü Karar 1, 5, 9 ve 11'i değiştirir/netleştirir** — özellikle Karar 11: katalizör üretimi masada değil Kataliz Ocağı'nda.
**Dayanak:** ADR-004 (etkin seviye olayı, 255 tavanı), `docs/06 §3` (ref bütçe), `docs/04 §4`, `docs/05 §3`. **Sayıların kaynağı:** `tools/balance/catalysts.py` → `data/balance/catalyst*.csv`, `enchant_caps.csv`, `innate.csv`, oyun JSON'u `data/theoria/balance/catalysts/default.json`. Bu sayfada sayı tablosu tekrarlanmaz; kural ve gerekçe burada, değer orada.

Kanıt etiketleri: **[log]** komut çıktısı · **[kaynak]** decompile 26.3.0.101 / NeoForge kaynağından okundu, çalıştırılmadı · **[çıkarım]**.

## Bağlam
Tasarım hedefi: eşya kalıcı olarak güçlenir; güç `docs/06` referans bütçesine oturur (yalnızca düz eklemeler); her şey veri satırı; yeni katalizör = bir CSV satırı. Faz 7a'nın cevaplaması gerekenler: component şeması · üst sınırlar · tarif maliyetleri · UX · Q34 (öntanımlı özellikler) · Q37 (menzilli/ağır silahlar) · ADR-004'ten devredenler (büyü başına bonus tavanı, Tür B "sanal büyü", Bilgelik toplam tavanı, XP kaynakları kapsamı, Netherite maliyet eğrisi).

## Karar

### 1. Ev sahibi sınıfları
Bir eşya katalizör taşıyabilir ⇔ `theoria:catalyst_hosts/<sınıf>` etiketinde. Sınıflar: **weapon** (kılıç, balta, mızrak) · **tool** (kazma, kürek, balta, çapa) · **armor** (kask=head, göğüslük=chest, pantolon=legs, bot=feet) · **ranged** (yay, arbalet, üçlü mızrak, gürz: *kademesiz*). Balta hem weapon hem tool'dur. Etiket başka modların eşyalarını da kabul eder (veri); katalizör kademesi bilinmeyen eşya taşıyamaz. **Kademe** `idx` = eşyanın malzemesinin merdiven idx'i (zırh merdiveni 0-13; alet merdiveni `tiers.TOOLS`; Faz 6 `gear.json` zaten bilir). Kademesiz ev sahipleri kademe yerine **derece** (katalizörün kendi `grade`'i) kullanır (Karar 10).

### 2. Component `theoria:catalysts` (şema donduruldu)
```
theoria:catalysts = {
  slots:     [ {id: "theoria:<katalizör>", level: 1..5, grade: 0..13 (yalnızca ranged_power'da)} , … ]   # sıralı; en çok yuva(ev sahibi)
  netherite: 0..5                                                                                          # Netherite uygulama sayısı
}
```
- `persistent` + `networkSynchronized` (olay iki taraftan sorulur; tooltip istemcide hesaplanır). Yoksa eşya katalizörsüzdür; **dinleyici ilk satırda çıkar, tahsis yok** (ADR-004 "⚠ olay çok sık").
- Codec doğrulaması: `id` benzersiz, `level` 1..5, `slots.size ≤ yuva`, `grade` yalnız `graded` katalizörde, `netherite` 0..5. Tavanlar `catalyst_rules.csv`'den (`levels`, `slot_*`, `netherite_max`). Bozuk kayıt `DataResult.error` → bileşen düşer (eşya katalizörsüz yüklenir; dünya bozulmaz).
- **Saklanan yalnızca `(id, level, grade)`; değer saklanmaz.** Etkin değer her seferinde `cap × eğri[level] × taban(kademe)`'den hesaplanır → yeniden dengelenen sayılar eski eşyalara da geçer (aynı ilke: mob kademesi, Faz 2b).
- Yuva sırası kullanıcıya görünür sıradır; değişimde aynı indeks korunur. Vanilla `enchantments` component'ine **hiçbir şey yazılmaz** (ADR-004 Karar 3).
- **Parlama:** katalizörlü eşya `enchantment_glint_override = true` alır; Kataliz Ocağı uygulamada yazar, ilk katalizör kalkarsa kaldırır (sanal büyüler vanilla parlamasını tetiklemez; bu yüzden ayrı bayrak). Öntanımlı (doğuştan) özellikler parlama VERMEZ.

### 3. Katalizör item'ı
31 yeni item (`theoria:catalyst/<id>`; Netherite katalizörü = **vanilla `minecraft:netherite_ingot`**, yeni item yok) + iki bileşen: `theoria:catalyst_level` (1..5, öntanımlı 1) ve `theoria:catalyst_grade` (yalnız `ranged_power`). Farklı seviyeler birbirine yığılmaz. Model, ad, tooltip veri-güdümlü (üreteç `catalysts.csv`'den; `generator/` 7b). Tooltip: ad + seviye, "Elmas kılıçta: +0,32 saldırı hızı" biçiminde **basılı olduğu eşyaya göre** değer (fareyle üstüne gelinen ev sahibi yoksa genel formül) ve hangi ev sahibi sınıflarına uyduğu.

### 4. Yuvalar ve kısıtlar (`catalyst_rules.csv`)
- `yuva = min(4, 1 + ⌊idx/4⌋)`; **Platin +1** → T0-3: 1 · T4-7: 2 · T8-11: 3 · T12: 4 · **T13: 5**. Kademesiz ev sahibi: 3.
- **Aynı katalizör bir eşyada en çok bir yuvada** (eski "N=5 kez" kuralının karşılığı seviyedir: V). Daha yüksek seviye = eski yuvayı yenisiyle **değiştirmek**.
- `min_idx`: katalizörün en düşük ev sahibi kademesi (Kalp 1; Kritik/Yıldırım 2; Yaşam Çalma/Sertlik/Solma/Işık/Gölge/Gravite 4; Fırtına 6 …). Altındaki ev sahibine konamaz.
- Seviye başına kademe kapısı **yok** (denendi: T4 referansı IV gerektirir; kapı ref bütçesini kırar). Seviyeyi **maliyet** sınırlar (Karar 11).
- Hedef uyumu: katalizör yalnızca `targets` sınıflarındaki eşyaya (ve zırhta yalnız belirtilen parçaya: Diken göğüs/pantolon, Koşu/Sıçrayış pantolon+bot, Tüy Düşüş bot, Doygunluk kask/göğüs) konur.
- Birleşik yumuşak tavanlar (katalizör + Durum ekranı toplamı; teknik tavan DEĞİL): Kritik Şansı ≤ 0,75 · Kritik Hasar ≤ 2,0 · Yaşam Çalma ≤ 0,30 · Doygunluk ≤ 12 · Sabitlik ≤ 1,0.
- Teknik tavanlar: `technical_caps.json` yeni özel nitelikleri de kapsar (7b); katalizör değeri hiçbir nitelikte vanilla tavanı yazmaz.

### 5. Uygulama kanalları (mixin yok)
| Tür | Kanal | Not |
|---|---|---|
| **A nitelik** (17) | `ItemAttributeModifierEvent` — eşya `getAttributeModifiers()`'a modifier ekler [kaynak: `ItemStack.forEachModifier` Neo'da `getAttributeModifiers()`'ı kullanır ⇒ **tooltip de aynı yoldan** nitelik satırlarını gösterir]. Kimlik `theoria:catalyst/<id>/<yuva>`; işlem `ADD_VALUE` (Koşu hariç `ADD_MULTIPLIED_BASE`: savaş gücü değil hareket). | Değer canlı hesaplanır, bileşende modifier yok. |
| **A özel nitelikler** | `theoria:crit_chance`, `crit_damage`, `life_steal`, `thorns`, `food_saturation` Theoria nitelikleri olarak kaydedilir; tüketiciler Java: `CriticalHitEvent` (şans/çarpan; `setCriticalHit`, `setDamageMultiplier` [kaynak]), `LivingIncomingDamageEvent`/`LivingDamageEvent` (çalma, diken), `LivingEntityUseItemEvent.Finish` (doygunluk). Aynı nitelikleri Faz 10 Durum ekranı `ADD_VALUE` olarak kullanır → **tek boru hattı**. | birleşik tavanlar Karar 4 |
| **B element** (11) **+ Onarım**, **R Menzil Gücü** | **Sanal büyü** (Karar 6): `GetEnchantmentLevelEvent` ile etkin seviye eklenir; etki datapack enchantment JSON'u. | `theoria:frost/fire/lightning/poison/wither/cleave/bleed/radiance/umbra/tempest/gravity/restoration/ranged_power` |
| **D Bilgelik** | Sanal büyü `theoria:erudition` + Java XP kancası (Karar 8). | |
| **C Netherite** | `GetEnchantmentLevelEvent` bonusu (Karar 7). | |
Vanilla etki tipleri (hasar, yangın, zehir, kanama) sanal büyünün JSON'unda; JSON yalnız `LevelBasedValue` ile ifade edilebilen şeyleri taşır. Yıldırım zinciri / Gravite çekimi / Kanama üst üste binmemesi gibi JSON'la ifade edilemeyenler 7b'de küçük Java kancasıdır ve `docs/10`'a yazılır.

### 6. Sanal büyü (Tür B kararı; ADR-004 "⚠ Tür B için aday mekanizma" kapanışı)
- Element/Onarım/Menzil Gücü/Bilgelik/öntanımlılar **vanilla `enchantments` component'inde bulunmaz**; `GetEnchantmentLevelEvent`'te `set(büyü, P)` ile yalnızca *sorgu anında* var olurlar. Sonuçlar: tooltip'te vanilla büyü satırı çıkmaz (katalizör satırı çıkar, Karar 11), örs/taşlama/`/enchant` görmez, kitapla elde edilemez, **vanilla parlaması olmaz** (Karar 2'deki ayrı parlama bayrağı bunu karşılar).
- Datapack büyüleri `supported_items` ⊂ ilgili ev sahibi etiketi; `in_enchanting_table`, `tradeable`, `on_random_loot` etiketlerine **girmez**; `weight` ≥ 1, `max_level` 1 (`max_level` etki hesabını sınırlamaz — ADR-004 [log]; ama sınırı kodlamayız, JSON her seviyeyi tanımlar).
- **Kademe kodlaması:** etkin seviye `P = 1 + 5·g + (L − 1)`; `g` = ev sahibi idx'i (kademesizde katalizörün `grade`'i), `L` = katalizör seviyesi. `P ≤ 1 + 5·13 + 4 = 70 ≤ 255` ✓. Üreteç her sanal büyü için JSON `lookup` (70 değer, `fallback` 0) yazar: değer = `cap × eğri[L] × taban(g)` (`catalyst_values.csv` aynı sayıları verir ⇒ GameTest beklenen değeri). Etki tabanı kılıç hasarına bağlandığı için element hasarı kademeyle büyür, vanilla tarzı sabit 1-4 hasar T13'te işe yaramaz hâle gelmez.
- `getTargetEnchant()` doluysa yalnız o büyü, `null` ise tüm sanal büyüler eklenir (ADR-004 Karar 4 ile aynı). Dinleyici ilk satırda `theoria:catalysts` ve öntanımlı tablo yoksa çıkar.
- Aynı sanal büyüyü iki kaynak veriyorsa (katalizör + öntanımlı): **toplanır**; `min(255, …)`.

### 7. Netherite (Tür C)
- Netherite külçesi **evrensel katalizör**; yuva tutmaz. Bir uygulama: `catalysts.netherite += 1`. Maliyet n. uygulama için `NETHERITE_COST[n−1]` külçe (**1, 2, 4, 6, 9**; toplam 22; azami **5 uygulama**/eşya). Girdi yığınında o sayıda külçe aranır.
- **Etkin bonus** `bonus(büyü) = min(netherite, tavan(büyü), 255 − vanilla)`; yalnızca **gerçek (vanilla `enchantments` component'indeki) büyüler**, seviye ≥ 1 şartıyla (ADR-004 Karar 3). Sanal büyüler (katalizör/öntanımlı) etkilenmez. Sayaç (harita değil): sonradan örste eklenen büyü de bonuslanır, taşlama büyüyü silerse bonus etkisizleşir.
- **Büyü başına bonus tavanı** `enchant_caps.csv` (ADR-004 "⚠ her büyü +1'i kaldırmaz" kapanışı): tek seviyeli (Onarım, İpeksi, Sonsuzluk, Alev, Yıldırım Çağırma, Çoklu Atış, lanetler) **0**; **Lure 0** (VI'da balık vurmaz); Riptide 0; Koruma ailesi **1** (EPF: 4 parça × (IV+1) = 20 = tavan); Geri Tepme/İtme 1; Servet/Yağma/Denizin Şansı 2; Keskinlik/Kutsal/Eklembacaklı/Güç/Verimlilik/Dayanıklılık 5; tabloda olmayan büyü: `max_level ≥ 2` ise +1, değilse 0 (başka modlara temkinli varsayılan). **Tablodaki her id'nin gerçek kayıt defterinde bulunduğu 7b GameTest'inde doğrulanır** (kimlikler 26.3 `Enchantments.java`'dan okundu [kaynak]).
- Reddedilme nedenleri (UX Karar 11'de gösterilir): eşyada büyü yok · hiçbir büyü uygun değil (hepsi tavan 0) · 5 uygulama dolu · külçe yetmiyor.

### 8. Bilgelik (Tür D) ve XP kaynakları — ADR-004 Karar 7'yi **değiştirir**
- Büyü `theoria:erudition`, `max_level` 5 (vanilla bileşende normal büyü: büyü masası/örs/ganimet/kitap) **ve** Bilgelik Katalizörü onu sanal olarak verir (etkin = `max(vanilla, katalizör)` + Netherite bonusu yalnız vanilla parçadan; cap 1 ⇒ VI).
- Yüzdeler (`ERUDITION_PCT`): I-V = +%100 / 300 / 600 / 1000 / **1600**; V üstü `+%400/seviye` (VI = +%2000).
- **Etki JSON'da DEĞİL Java'da.** Gerekçe [kaynak]: `EnchantmentHelper.processMobExperience` her kuşanılan parçanın `mob_experience` efektini ayrı uygular; `multiply` etkileri **çarpılır** (6 parça × ×17 = 24 milyon) ve `add` etkisi yüzde değil mutlak XP ekler → "birden çok parçada toplanır, tavan %2400" JSON'la ifade edilemez. Bu yüzden: tüm parçaların (**mainhand + 4 zırh**; offhand hariç) etkin Bilgelik yüzdeleri **toplanır**, `min(toplam, 2400)` çarpan `1 + toplam/100`.
- **Kapsam:** yalnız *öldürme XP'si* (`LivingExperienceDropEvent`, `getDroppedExperience`/`setDroppedExperience` [kaynak]) ve *blok kırma XP'si* (`BlockEvent.BreakEvent` exp). Fırın, balık tutma, ticaret, üreme, XP şişesi, taşlama taşı **dışarıda**: taşlama taşı/şişe iade tarzı kaynaklar çarpanla sonsuz döngü kurar (harca → iade çarpanla büyür); diğerleri "mob + maden" tasarımı için gereksiz. Durum ekranı ömür-boyu sayacı (Faz 10) bu iki kaynağın çarpılmış değerini sayar.
- Toplam %2400 taslak (Q43).

### 9. Öntanımlı özellikler (Q34) = "doğuştan sanal büyüler"
`innate.csv`: (malzeme kademesi × ev sahibi sınıfı) → sanal büyü + seviye; `GetEnchantmentLevelEvent` dinleyicisi eşyanın kademe malzemesine bakıp ekler (gear.json yolunu kullanır). **Silinemez** (bileşende değil), **parlama vermez**, taşlama taşı görmez, örs `max_level`'e kırpmaz, ref bütçesinden **sayılır** (Çelik zırh Koruma I × 4 parça = EPF 4 = `ref_epf(5)`; Lonsdaleit II = 8 ≤ 14; Platin III = 12 ≤ 16 ⇒ boşluğu oyuncu kendi büyüleriyle doldurur). Netherite'e açık değil (Karar 7). Tooltip'te "Doğuştan: Koruma I". Karşılıksız: **Bakır "ucuz onarım"** (bakır onarım girdisi zaten külçe). **Platin +1 yuva** Karar 4'te. Orikalkum "Tamir" = `restoration II` (saniyede azami dayanıklılığın %0,08'i), "element dirençleri" = Ateş/Patlama/Mermi Koruması I, Platin "tam direnç" = II. Seviyeler tabloda; tavan EPF 20'dir (mevcut formül ADR-003).

### 10. Menzilli/ağır silahlar (Q37) — tek dereceli katalizör
Yay/arbalet/üçlü mızrak/gürz kademe malzemesine sahip değil. Çözüm: **Menzil Gücü** (`ranged_power`) *dereceli* tek katalizör: üretiminde tüketilen külçenin kademesi katalizörün `grade`'i olur (tarifte "kalay … platin" seçimi: `catalyst_grade` bileşeni); değeri `0,60 × eğri[L] × kılıç hasarı(grade)`, sanal büyü `theoria:ranged_power` (vanilla `minecraft:damage` etkisi; ok/üçlü mızrak/gürz hasarı `EnchantmentHelper.modifyDamage`'den geçer [kaynak: aynı yol]). Kademe kapısını **malzeme** koyar: Platin dereceli katalizör Platin külçesi ister. Kademesiz ev sahibinde yuva 3; uyan katalizörler: Menzil Gücü, Şans, Onarım, Bilgelik (+ Netherite). Yeni yay/arbalet kademesi eşyası YOK (kapsam).

### 11. Maliyetler ve kaynaklar
- **Katalizör üretimi masada değil, el tarifiyle** (şekilsiz, vanilla crafting): her katalizör × seviye `catalyst_rules.csv` `cost_l1…5` merdiveniyle: `(toz, imza, öz, çekirdek)` = I (3,1,0,0) · II (6,2,1,0) · III (10,3,2,0) · IV (16,4,4,1) · V (24,6,6,3). *İmza malzeme* katalizöre özgü vanilla eşya (`catalysts.csv`: Güç=alev tozu, Buz=mavi buz, Yıldırım=paratoner, Kalp=altın havuç, …). *Toz* `theoria:catalyst_dust` (cevher bloklarından ek damla, gear.json'daki karbon tozu mantığıyla global loot modifier), *öz* `theoria:catalyst_essence` (düşman damlası), *çekirdek* `theoria:catalyst_core` (boss/zindan sandığı). Puan (toz 1, imza 2, öz 5, çekirdek 25): 5 · 15 · 26 · 69 · 141 — her seviye öncekinin ≥ ×1,7'si; **IV'ten itibaren çekirdek** istenir ⇒ ref seviyesi (IV) bir boss uğrağıdır. Kaynakların oran/şansı 7b'de geçici ayarlanır, Faz 8/9'da yeniden düzenlenir (Q42-benzeri tek satır).
- Masa tarifi (`docs/04 §4`): 4 demir + 2 obsidyen + 1 elmas + 1 lav kovası (kolay ama bedava değil; ilk ~20 dk).
- Masada uygulama maliyeti: yalnızca katalizör/külçe tüketimi (XP yok). Yerleştirilen katalizör uygulamada **tüketilir**.

### 12. Masa (UX) — Kataliz Ocağı
```
┌ Kataliz Ocağı ─────────────────────────────────────────────┐
│  [ HEDEF ]   ◇ Yuva 1  ◇ Yuva 2  ◇ Yuva 3  🔒 T12'de  🔒 Platin   ← mevcut katalizörler dolu gösterilir
│                                                            │
│  GİRDİ:  [ ][ ][ ][ ][ ]      (katalizör ya da Netherite külçesi yığını)
│                                                            │
│  ÖNİZLEME  (yeşil + / kırmızı −)                           │
│   Saldırı hızı   1,60 → 1,92   (+0,32)                     │
│   Kritik şansı   %0  → %15                                 │
│   Buz hasarı     —    → +1,30 / vuruş                      │
│   ⚠ Hız III yok edilecek (Hız IV ile değiştirilir)          │
│  [ Uygula ]  ← kapalıysa nedeni tooltip'te                 │
└────────────────────────────────────────────────────────────┘
```
- **Menü:** yuva 0 hedef, 1-5 girdi (en çok yuva sayısı kadar; ev sahibinin açık olmayan yuvaları kilitli gösterilir), ayrı çıktı yuvası yok: **Uygula** `clickMenuButton` ile sunucuya gider; sunucu *otoriter* (istemci önizlemesi yalnız gösterim) ve tek atomik işlemde: doğrula → bileşeni yaz → girdileri tüket → hedefi aynı yuvada bırak. Başarısızlıkta hiçbir şey tüketilmez.
- **Yerleşim kuralı:** her girdi katalizörü sırayla ilk uygun yuvaya gider; aynı `id` zaten varsa *daha yüksek seviyede* ise o yuvanın yerini alır (eski yok olur, uyarı satırı), eşit/düşükse reddedilir; yuva doluysa girdi geçici olarak reddedilir ("yer yok — mevcut birini değiştir" için hedef yuvaya sürükleyerek açıkça seçilir). Önizleme birden çok girdinin sonucunu *birlikte* gösterir.
- **Sökme:** hedef yuvadaki dolu katalizör, "Boşalt" ile iade OLMADAN kaldırılır (Q45). Katalizörün seviyesini yükseltmek = daha yüksek seviyeyle değiştirmek.
- **Hata nedenleri** (tooltip + kırmızı satır, lang anahtarları): kademesiz/etiketsiz eşya · min kademe altında · bu sınıfa uymuyor · yuva yok · yinelenen katalizör · seviye eşit/düşük · Netherite: büyüsü yok / hepsi tavanda / 5 dolu / külçe yetmiyor.
- `isPauseScreen() == false` (CLAUDE.md); sunucu paket işlemeye devam eder.
- **JEI:** katalizör tarifleri normal şekilsiz tarif olarak görünür (JEI vanilla tarifleri zaten okur). Özel "Kataliz Ocağı" kategorisi JEI plugin API'sine derleme bağlamayı gerektirir; CLAUDE.md kuralı (Curios dışında `compileOnly` yok) nedeniyle **yapılmaz** — ocak bilgisi masa tarifinin tooltip'inde ve katalizör tooltip'indeki "Kataliz Ocağı'nda uygulanır" satırında verilir (Q yok; kural gereği). Tooltip'te katalizör satırları: "Hız IV (+0,32)", doğuştan olanlar mor, Netherite bonuslu büyü ("Keskinlik V **+1**").

### 13. Doğrulanabilirlik
- `python3 -I tools/balance/catalysts.py --check` (check_data.py içinde): dosyalar üreteçle özdeş + 20 denetim: 32 katalizör · yuva eğrisi · maliyet merdiveni monotonluğu · **ref bütçesi** (T4+: zırh IV %12,8 / talep %12,5; hasar aynı; Kalp IV %37 / talep %36; 15% sapma bandı) · talep yuvalara sığıyor · **katalizör-maks savunma** HTD en çok 9,2 (≤ 30) ve ref'in ×1,24'ü (≤ ×1,6) · **katalizör-maks silah DPS ×2,05** (≤ ×2,2; T13, 5 yuva) · örnek **Hız IV + Kritik Şansı III + Buz II (T8 elmas kılıç, 3 yuva) ×1,34** · Netherite kuralları · Bilgelik. `--break`: 8 bozulmuş sabit, 8'i de kırmızı. `--report`: tablolar.
- 7b GameTest listesi (ilk iş **`enchant_effective_level`**, ADR-004 Faz 7b): bileşen kayıt/yükle · sync · yuva sayısı/kapılar · uygulama (Hız+Kritik+Buz, bileşen + etkin nitelik) · Netherite (Keskinlik V→VI, hasar +3,5; EPF tavanı; Lure/tek seviyeli reddi; örs/taşlama sonrası) · sanal büyü (kodlama `P`, lookup ≡ `catalyst_values.csv`) · öntanımlı · Bilgelik (toplama, %2400 tavanı, yalnız öldürme/blok kaynakları, taşlama döngüsü yok) · ranged_power · atomik başarısızlık (hiçbir şey tüketilmez) · tooltip.

## Sonuçlar
- ✅ Mixin yok: nitelik = `ItemAttributeModifierEvent`, büyü seviyesi = `GetEnchantmentLevelEvent`, XP/kritik = vanilla/Neo olayları. Değerler canlı hesaplandığı için denge değişimi kayıtları bozmaz.
- ✅ Katalizör ref bütçesinin **yarısını** verir (arkasında kalan yarı: Durum ekranı); katalizör-maks oyuncu ref'i ezmez (×1,24 savunma, ×2,05 hasar).
- ⚠ **Sanal büyü kodlaması** (`P = 1 + 5g + L − 1`) `lookup` tablosunu 70 girişe şişirir; 14 kademe × 5 seviye dışında kademe eklenirse tablo ve `grade` aralığı genişler (veri satırı + üreteç).
- ⚠ **Tooltip** için `ItemTooltipEvent` hem katalizör hem Netherite'li büyü hem doğuştan satırlarını yazar (ADR-004 "⚠ tooltip" ile birlikte).
- ⚠ **Element hasar fraksiyonları** (Buz/Ateş 0,10, Yıldırım 0,12 …) *taslak*; DPS sayımına girmeleri ölçülen savaş denemesiyle ayarlanır (7b + kullanıcı).
- ⚠ Başka mod eşyası: kademesi bilinmiyorsa host değildir; etiket verisiyle genişler (`catalyst_hosts` + kademe eşlemesi `gear.json`'da yok → 7b "dış kademe" tablosu). Faz 13'te karar.
- ⚠ **Ağ:** şema donduruldu; component codec'i değişirse eski kayıtlar düşer. Kayıt formatı yalnızca bir sürüm daha değişebilir (Faz 13'te kilit).

## Faz 7b sonucu (2026-11-11, Sonnet 5.5) — uygulama; tasarımdan sapmalar ve nedenleri
Kanıt: `./gradlew runGameTestServer` → **All 50 required tests passed** (33 eski + 17 yeni: platformu kanıtlayan `enchant_effective_level` + 16 `catalyst_*`); `runDevClient -PdevClient=catalyst` → RESULT PASS (gerçek Vulkan penceresi); `/theoria test catalysts` 7 denetim. Ayrıntı ve sayılar: `docs/journal/f07.md`.

| Karar | Ne oldu | Neden |
|---|---|---|
| **11 (değişti)** | Katalizör **üretimi Kataliz Ocağı'nda**: hedef yuvası boşken beş girdi yuvasındaki *yığın sayıları* okunur; tek imza malzemesi katalizörü seçer, dört sayıyı (toz, imza, öz, çekirdek) karşılayan **en yüksek seviye** üretilir ve yalnız o maliyet düşülür (`CatalystForging`). Dereceli Menzil Gücü ek olarak bir külçe ister (en yüksek kademeli külçe derece olur, yalnız o külçe tüketilir). Masa tarifi (4 demir + 2 obsidyen + elmas + lav kovası) olduğu gibi vanilla şekilsiz tarif. | Karar 11'in "şekilsiz vanilla tarif"i **yapılamaz**: 3×3 ızgara en çok 9 malzeme alır ve her malzeme 1 adettir; III-V seviye 15-39 eşya ister (V: 24 toz + 6 imza + 6 öz + 3 çekirdek). 7a'da gözden kaçmıştı. Seviye merdiveni ve sayılar aynen duruyor (`catalysts.py`). **Bedel:** JEI vanilla tarif göstermediği için katalizör maliyeti JEI'da görünmez → keşfedilebilirlik katalizör ipucundaki "Üretim maliyeti" satırı + ocak önizlemesi (imza koyunca I-V maliyetleri listelenir). |
| **1 (değişti)** | `theoria:catalyst_hosts/*` **etiketleri yapılmadı**; ev sahibi tablosu `gear.json`'dan (her malzemenin zırh/alet kalemleri, vanilla dahil) + katalizör JSON'undaki 4 kademesiz eşyadan (`yay, arbalet, üçlü mızrak, gürz`) türer (`CatalystHosts`). | Kademesi bilinmeyen eşya zaten ev sahibi olamıyor (kademe yuva sayısını ve değerleri belirler); etiket, kademe eşlemesi olmadan başka mod eşyası ekleyemez → yalnızca belge olurdu. Dış mod kademeleri Faz 13. `gear.json` malzemelerine `idx` alanı eklendi. |
| **5 (değişti)** | **Element etkileri Java'da** (`ElementEffects`): anlık hasar (Buz, Ateş, Yıldırım hedefi, Işık [yalnız ölümsüz], Gölge [arkadan], Fırtına [havadayken]) `LivingIncomingDamageEvent`'te zırhtan önce eklenir; vuruş sonrası etkiler `LivingDamageEvent.Post`'ta (Buz yavaşlatır, Ateş tutuşturur, Yıldırım 2 düşmana zincirler, AoE 2 blokta herkese, Gravite hedefi çeker); Zehir/Solma (5 sn) ve Kanama (6 sn) zamanla hasar (aynı tür üst üste binmez, yenisi süreyi tazeler). Datapack büyüleri (`theoria:frost`…) yalnız **taşıyıcı**: `GetEnchantmentLevelEvent` bir `Holder` ister. **Yalnız Menzil Gücü JSON'da** (`minecraft:damage` + 70 girişli `lookup`; ok/mızrak/gürz hasarı vanilla boru hattından geçer). | Zincir, alan, çekim, süreli hasar ve koşullar `LevelBasedValue` ile ifade edilemiyor (ADR Karar 5 bunu öngörmüştü). Tek yerde Java, veri hâlâ tablodan. İkincil hasar (`indirectMagic(saldırgan, saldırgan)`) saldırganın silahını bildirir → yeniden girişi `secondary` bayrağı keser (GameTest'te yakalandı: zincir zincirleniyordu). |
| **5 (netleşti)** | Aynı nitelik iki hedeften geliyorsa **bir kez** uygulanır: balta hem silah hem alet; Şans (`luck`) ikisi için de tanımlı → ilk testte 0,4 çıktı (0,2 olmalıydı). Erişim farklı nitelik (varlık/blok menzili) olduğu için ikisini de alır. | `CatalystAttributes.entries`. |
| **9 (netleşti)** | Doğuştan satırlar `GetEnchantmentLevelEvent`'te (bileşensiz, hızlı yol: `IdentityHashMap<Item, Innate[]>`). `theoria:` doğuştan büyü (Onarım) ev sahibi kademesiyle P-kodlanır; aynı büyüyü katalizör de veriyorsa **seviyeler** toplanır (en çok V) ve yeniden kodlanır. **Üç satır atıl:** Bronz alet/silah (Dayanıklılık I) ve Çelik silah (Keskinlik I) — bronz, çelik ve titanyumun **aleti yok** (`tools.csv`). Zırh satırları çalışıyor. | Q49 (`docs/10`). |
| **6/8 (netleşti)** | Bilgelik: etkin = `max(gerçek, katalizör) + Netherite` (Netherite yalnız gerçek parçaya); kaynaklar `LivingExperienceDropEvent` (öldüren oyuncu) ve `BlockDropsEvent` (kıran oyuncu); offhand sayılmaz. | `Erudition`. |
| **12 (netleşti)** | Ekran 356×166 (iki sütun); GUI ölçeği 1-3'te 1280×720'e sığar, 720p'de ölçek 4'te taşar (Q53). Yaşam Çalma iyileşmesi `DamageHooks`'un "azami can/20" ölçeğine bölünür ki oyuncu tam olarak "verilen hasarın bu kesrini" alsın (`CatalystCombat#healScale`). Kritik şansı yalnız dolu saldırıda atılır (vanilla kritik koşuluyla aynı). | — |

**Yapılmadı (bilinçli):** özel "Kataliz Ocağı" JEI kategorisi (CLAUDE.md: derleme bağımlılığı yok) · Solma'nın "iyileşmeyi keser" yan etkisi (Q50) · ocak ekranı/ikon görünüşü (kullanıcı onayı; ikonlar vanilla elmas/ametist/zümrüt/lapis şablonlarının renkli varyantları, yer tutucu).

## Geri dönüş
1. Sanal büyü yolu `GetEnchantmentLevelEvent`'te çalışmazsa (7b ilk GameTest kırmızı): element/Onarım/Menzil Gücü etkileri `LivingDamageEvent` kancalarına taşınır (JSON yerine Java; ADR-004 Geri dönüş 1 de aynı olaya bağlı).
2. `ItemAttributeModifierEvent` tooltip'te görünmezse: `ItemTooltipEvent`'te satır elle yazılır.
3. Bilgelik'in Java kancası sorun çıkarırsa (başka mod XP'si): kapsam yalnızca mob öldürmeye daraltılır.

## Sonraki fazlara yükümlülükler
- **7b:** bu ADR'nin Karar 2-13 maddeleri. Yeni item/blok/menü/ekran, `generator/catalysts.py`, lang (tr/en), JEI, tooltip, loot kaynakları, GameTest + `/theoria test catalysts`.
- **Faz 8:** `catalyst_essence` ve `catalyst_core` damlalarını mob/boss zincirine bağlar (`data/catalyst_drops.csv`). Mob el silahı (Q35) buradan etkilenmez.
- **Faz 10:** `crit_chance` vb. nitelikleri Durum ekranı aynı `ADD_VALUE` yolunda kullanır; birleşik tavanlar (Karar 4) orada da geçerli; Bilgelik stat'ı ile `theoria:erudition` büyüsü adları çakışmaz (ADR-004 Karar 7).
- **Faz 12/13:** Relics/Curios katalizörü yalnızca etiket (`catalyst_hosts`) üzerinden; `theoria:catalysts` şeması Faz 13'te dondurulur.
