# ADR-004 — Büyü (enchantment) seviye tavanı

**Durum:** Kabul · 2026-11-09 (Faz 0, spike S4) · **Etkiler:** Faz 7 (Kataliz Masası, Netherite kuralı, Bilgelik büyüsü), Faz 6 (öntanımlı büyüler), Faz 2a (büyü koruması politikası — ADR-003)
**Kapanış:** 2026-11-10, Fable 5.2 xHigh gözden geçirdi — **Karar 4 değişti:** etkin seviye artık her etki için elle `Δ` eklenerek değil NeoForge `GetEnchantmentLevelEvent` ile verilir; 255 üstü v1.0 kapsamından çıktı (Δ yolu genişleme seçeneği olarak kaldı, Q16); büyü id'si `docs/05` ile hizalandı (`theoria:erudition`); kanıt türleri etiketlendi; faz yükümlülükleri eklendi.

Kanıt etiketleri: **[log]** komut/log çıktısı · **[kaynak]** decompile edilmiş 26.3.0.101 kaynağından okundu, çalıştırılmadı · **[çıkarım]** denenmedi.

> **Faz 7a (ADR-006) güncellemeleri:** Karar 2'deki bonus haritası → `netherite` sayacı + `enchant_caps.csv` (Karar 3'ün "vanilla ≥ 1" şartı korunur) · Karar 7'deki Bilgelik etkisi `mob_experience` JSON'u yerine **Java kancası** (JSON efektleri parçalar arasında çarpılır) · Tür B için sanal büyü kararı verildi (ADR-006 Karar 6).

## Bağlam
- İstek: katalizörlerle "sınırsız" güçlenme (R6); Netherite eşyanın mevcut her büyüsünü +1 seviyeye taşır (D6; `docs/05 §3` Tür C: "sınır teknik tavana (255) kadar"); Bilgelik büyüsü +%1600 XP'ye ulaşır (D5/Q4).
- Soru: vanilla `minecraft:enchantments` component'i 255'in üstünü kabul ediyor mu? Datapack büyü etkileri (`LevelBasedValue`) 300. seviyede çalışıyor mu?
- NeoForge vanilla'nın **oynanış** seviye sorgularını tek olaydan geçirir (`GetEnchantmentLevelEvent`); **ham** component'i yalnızca zanaat yolları okur (örs, taşlama, onarım tarifi, `/enchant`, tooltip) [kaynak — aşağıda].

## Karar
1. **Vanilla component'te seviye ≤ 255 kalır** (sert kesik [log]); 255 üstünü vanilla component'e/codec'ine yazmaya dayanan hiçbir şey yazmayız.
2. **Kendi data component'imiz** `theoria:catalysts` (şema Faz 7a; `persistent` + `networkSynchronized` — olay istemcide de sorulur): katalizör yığınları ve **büyü seviye bonusları** (`Holder<Enchantment>` → `int`); codec'i kendi sınırımızla (denge verisinden) doğrulanır.
3. **Etkin seviye = vanilla seviye + bonus**; bonus yalnızca vanilla seviye ≥ 1 iken geçerlidir (Netherite "yalnızca büyüsü olan eşyada"; taşlama büyüyü silerse bonus kendiliğinden etkisizleşir). Bonus her zaman kendi component'imizde durur, vanilla component'e **yazılmaz**: tek doğruluk kaynağı; örs birleştirmesi seviyeyi `max_level`'e kırparken (`AnvilMenu` [kaynak]) bonusu silemez; mod kaldırılınca kayıt bozulmaz.
4. **Uygulama — `GetEnchantmentLevelEvent`** (oyun bus'ı, iki taraf; mixin yok): dinleyici `event.getEnchantments().set(büyü, vanilla + bonus)` yapar (`getTargetEnchant()` doluysa yalnızca o büyü, `null` ise haritadaki bonuslu tüm büyüler). Vanilla bundan sonra **bütün** etki tiplerini etkin seviyeyle kendisi uygular: değer etkileri (`modifyDamage`, `modifyMobExperience`, dayanıklılık…), slot/konum etkileri (attribute'lar), vuruş-sonrası etkiler, loot fonksiyonları (Servet/Yağma). Kendi kancalarımızda `Δ` eklemeyiz → çift sayım riski yok; yeni büyüler yalnızca JSON'dur.
5. **Tavan:** etkin seviye ≤ 255. `Mutable.set` fazlasını sessizce kırpar [log]; Kataliz Masası buna güvenmez, `vanilla + bonus > min(255, büyü başına tavan)` olacak uygulamayı reddeder. **255 üstü kapsam dışı** — kullanıcı onayı 2026-11-10 (`docs/10` D13: "daha fazlasına ihtiyaç yok"); genişleme yolu "Geri dönüş" 2.
6. Pratik "sınırsızlık" azalan getiriyle sağlanır (`docs/06`, Faz 2a/7a sim); seviye sayısı tek başına sınırsız büyümez.
7. **Bilgelik (XP) büyüsü:** `theoria:erudition` (`docs/05 §3` Tür D; "Bilgelik/Wisdom" Durum ekranındaki *stat*'ın adıdır, id çakışmasın), `max_level` 5; Netherite ile etkin VI. `max_level` yalnızca büyü masası/örs/`/enchant`'i sınırlar, etki hesabını sınırlamaz [log: `max_level` 5 olan Keskinlik 255'te +128] → JSON'daki `LevelBasedValue` V üstünü de tanımlamalıdır (`docs/05`: `+%1600 + 400·(L−5)`). Etki `minecraft:mob_experience` / `block_experience` efektleriyle JSON'da.

## Kanıt
**[log]** `./gradlew runGameTestServer` → GameTest `theoria:s4_enchant_cap` geçti (2026-11-09; 2026-11-10 06:34'te yeniden: `All 5 required tests passed :)`). Faz 1'den beri kalıcı regresyon testi: `theoria:vanilla_enchant_cap` (`devtest/VanillaAssumptions.java`). Çıktı: `docs/adr/kanit/s4_enchant_cap.txt`:
```
ItemEnchantments.CODEC level 255 -> OK
ItemEnchantments.CODEC level 300 -> ERROR: Value 300 outside of range [1:255] missed input: {"minecraft:sharpness":300}
ItemEnchantments.Mutable.set(sharpness, 300) stores 255
ItemStack.enchant(sharpness, 300) -> stack level 255
datapack enchantment definition max_level=300 -> ERROR: Value must be within range [1;255]: 300
Enchantment.modifyDamage(level=5) direct call -> +3.00 damage (vanilla linear 1+0.5*(L-1) = 3.00)
Enchantment.modifyDamage(level=255) direct call -> +128.00 damage (vanilla linear 1+0.5*(L-1) = 128.00)
Enchantment.modifyDamage(level=300) direct call -> +150.50 damage (vanilla linear 1+0.5*(L-1) = 150.50)
Enchantment.modifyDamage(level=1000) direct call -> +500.50 damage (vanilla linear 1+0.5*(L-1) = 500.50)
```
**[kaynak]** (çalışma zamanında denenmedi — S4 bu olaya dinleyici kaydetmedi):
- `EnchantmentHelper.getItemEnchantmentLevel` → `piece.getEnchantmentLevel(ench)` (Neo yaması: "always check gameplay enchantments") → `EventHooks.getEnchantmentLevelSpecific` → `GetEnchantmentLevelEvent` → `enchantments.getLevel(ench)`.
- `EnchantmentHelper.runIterationOnItem` (iki aşırı yükleme; `runIterationOnEquipment` de bunu çağırır) → `piece.getAllEnchantments(lookup)` → `EventHooks.getAllEnchantmentLevels` → aynı olay. Bütün `modify*`/`process*`/`doPostAttackEffects`/`forEachModifier`/`runLocationChangedEffects` yolları buradan geçer.
- Loot: `ApplyBonusCount`, `BonusLevelTableCondition` → `getItemEnchantmentLevel`; `EnchantedCountIncreaseFunction`, `LootItemRandomChanceWithEnchantedBonusCondition` → `EnchantmentHelper.getEnchantmentLevel(ench, entity)` → aynı yol.
- Ham component okuyanlar: `AnvilMenu`, `GrindstoneMenu`, `RepairItemRecipe`, `EnchantCommand` (`getEnchantmentsForCrafting`) ve tooltip → bonus zanaata sızmaz.
- `ItemEnchantments` kurucusu `level > 255`'te hata fırlatır; `STREAM_CODEC` VAR_INT ama aynı kurucudan geçer; olay da `ItemEnchantments.Mutable` taşıdığı için 255'te kırpılır.
- `enchantment.level.N` çeviri anahtarı vanilla'da yalnızca 1…10 için var; `ItemTooltipEvent#getToolTip()` değiştirilebilir liste; `Enchantment.getFullname(holder, level)` public.

## Sonuçlar
- ✅ Vanilla dünya kaydı ve ağ codec'i hiç bozulmaz (component sınırında kalıyoruz) → `/enchant`, örs ve diğer modlar güvenli. [log]
- ✅ Etkin seviye ≤ 255 için etki tipi başına kod yok; Servet/Yağma gibi loot-fonksiyonlu büyüler de kapsanır (eski Δ tasarımında bunlar için kanca yoktu). [kaynak]
- ⚠ Olay çok sık atılır (her oynanış sorgusu, tick etkileri, iki taraf) → dinleyici eşyada `theoria:catalysts` yoksa ilk satırda çıkmalı, tahsis yapmamalı.
- ⚠ Tooltip ham seviyeyi gösterir → `ItemTooltipEvent`'te vanilla satırı (`Enchantment.getFullname(holder, vanillaSeviye)` ile eşleştirip) etkin seviyeyle değiştiririz; 10 üstü için `enchantment.level.11…255` lang anahtarları üretilir ya da rakam yazılır (Faz 7b).
- ⚠ Her büyü +1'i kaldırmaz → **büyü başına bonus tavanı tablosu** (Faz 7a): tek seviyeli büyüler (Onarım, İpeksi Dokunuş, lanetler) etkisiz; Koruma EPF'si vanilla'da 20'de (%80) tavanlı (`CombatRules.getDamageAfterMagicAbsorb`) ve zırh azaltmasıyla **çarpılır** (ADR-003 → Faz 2a); Yem (Lure) etkin VI'da balık hiç vurmaz (`FishingHook`: `rastgele(100…600) − 100·L ≤ 0` → sürekli yeniden zar) [kaynak].
- ⚠ Aynı olay vanilla component'te **olmayan** bir büyüyü de sanal olarak ekleyebilir → element katalizörleri (Tür B) için aday mekanizma; karar Faz 7a (o durumda Karar 3'teki "vanilla ≥ 1" koşulu yalnızca Netherite bonusu için geçerlidir).

## Sonraki fazlara yükümlülükler
- **Faz 2a:** sim büyü korumasını (EPF, %80 tavan) ve zırhla çarpımını modellemeli (ADR-003 "Yükümlülükler").
- **Faz 6:** öntanımlı büyüler vanilla component'e normal seviyeyle (≤ `max_level`) yazılır; bu ADR'ye dokunmaz.
- **Faz 7a:** component şeması (sync'li), büyü başına bonus tavanı tablosu, Netherite maliyet eğrisi, Tür B için "sanal büyü" kararı, Bilgelik'in çok parçada toplam tavanı (%2400 — tek tek JSON etkileriyle ifade edilemez; `LivingExperienceDropEvent`'te kırpma ya da tavanı kaldırma) ve `mob_experience`/`block_experience` dışındaki XP kaynakları (fırın, balık, ticaret) kapsam kararı.
- **Faz 7b — ilk iş, [kaynak] satırlarını [log]'a çeviren GameTest** `theoria:enchant_effective_level`: Keskinlik V + bonus 1 → `getItemEnchantmentLevel` = 6, `getTagEnchantmentLevel` = 5, `EnchantmentHelper.modifyDamage` +3,5 (bonussuz +3,0); Servet III + 1 → 4; bonus 300 → 255; örs birleştirmesi ve kayıt/yükle sonrası bonus duruyor. Kırmızıysa "Geri dönüş" 1.
- **Faz 7b DoD** ("Netherite ile Keskinlik V→VI"): tooltip VI gösterir, hasar +3,5, örs/taşlama sonrası davranış Karar 3'e uygun.

## Geri dönüş planı
1. Olay zinciri GameTest'te beklendiği gibi çalışmazsa: bonusu doğrudan vanilla component'e yaz (`EnchantmentHelper.updateEnchantments`); kendi component'imiz yalnızca katalizör kaydı olur. Bilinen bedel: örs birleştirmesi `max_level` üstünü kırpar → `AnvilUpdateEvent` ile korunur ya da kabul edilir.
2. 255 üstü ileride istenirse (D13 değişirse): yalnızca değer etkilerinde kendi kancalarımızdan `Δ = etki(etkin) − etki(255)` eklenir (`Enchantment#modifyDamage(level, …)`, `modifyMobExperience(level, …)` doğrudan çağrı — [log] satırları bunu kanıtlıyor; `LevelBasedValue` doğrusal olmayabileceği için seviye farkıyla değil etki farkıyla). Attribute/loot etkileri bu yolla taşınamaz; etki tipi başına tablo gerekir.
3. Mixin (`ItemEnchantments.LEVEL_CODEC` + kurucu + `Mutable.set/upgrade` + `EnchantmentDefinition.max_level`): 5+ enjeksiyon noktası, dünya kaydını 255+ ile yazar (modu kaldırınca kayıt bozulur) → **reddedildi**.
