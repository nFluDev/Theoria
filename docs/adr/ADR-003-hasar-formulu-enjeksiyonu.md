# ADR-003 — Hasar azaltma formülünün enjeksiyonu

**Durum:** Kabul · 2026-11-09 (Faz 0, spike S6) · **Etkiler:** Faz 2 (balance), Faz 3 (HUD zırh sayısı), Faz 6 (gear), Faz 7 (büyü koruması — ADR-004), Faz 8 (mob ölçekleme)
**Kapanış:** 2026-11-10, Fable 5.2 xHigh gözden geçirdi — mekanizma aynı (kanıt yeniden üretildi); **düzeltildi/eklendi:** (1) kendi formülümüz vanilla'nın `armor_effectiveness` (Breach) adımını da uygulamalı, yoksa o etki sessizce ölür; (2) attribute tavanı datapack'ten gelemez (mod kurucusu datapack'ten önce çalışır) → jar'a gömülü *teknik* tavan; (3) `H`'nin tam tanımı, modifier sırası, zırh dayanıklılık kaybı ve "zırh × büyü koruması" çarpımı Faz 2a yükümlülüğü olarak yazıldı; kanıt türleri etiketlendi.

**Faz 2a (2026-11-10, Fable 5.2):** katsayılar simülasyonla kilitlendi (`k1=30`, `k2=8`, `cap=0,90`); formüle **sertlik terimi** girdi; ikinci modifier eklendi (`Reduction.ENCHANTMENTS` × 0,5); toplam tavan **yok**; zırh aşınmasına kanca **yok** — ayrıntı en altta "Faz 2a sonucu". Mekanizma (Karar 1-4) değişmedi.

**Faz 2b (2026-11-11, Sonnet 5.5):** mekanizma uygulandı ve ölçüldü; Karar 1-4 değişmedi. Önceki [çıkarım]/[kaynak] satırlarının çoğu [log]'a döndü — ayrıntı en altta "Faz 2b sonucu".

Kanıt etiketleri: **[log]** komut/log çıktısı · **[kaynak]** decompile edilmiş 26.3.0.101 kaynağından okundu, çalıştırılmadı · **[çıkarım]** denenmedi.

## Bağlam
- D4: zırh formülü global değişir; vanilla 20-puan tavanı kalkar. `docs/06`: `azaltma = min(0,90, A/(A+60+4·H))`, takım zırhı 310'a, mob HP 815'e (boss'lar daha fazla) çıkıyor.
- Vanilla 26.3'te **iki ayrı tavan** var [kaynak + log]:
  1. `Attributes.ARMOR` = `RangedAttribute(0, 0, 30)` → `getArmorValue()` 30'u geçemez. Benzer: `ARMOR_TOUGHNESS` ≤ 20, `MAX_HEALTH` ≤ 1024, `ATTACK_DAMAGE` ≤ 2048.
  2. `CombatRules.getDamageAfterAbsorb`: etkin zırh `clamp(A − H/(2+T/4), 0,2·A, 20)` → azami **%80** azaltma.
- Vanilla'nın zırh adımı azaltmadan başka iki iş daha yapar [kaynak]: silahın `armor_effectiveness` büyü etkisini uygular (`EnchantmentHelper.modifyArmorEffectiveness` — Breach) ve zırhı yıpratır (`hurtArmor` → parça başı `max(1, H/4)`, `ArmorHurtEvent`).
- NeoForge 26.3.0.101 hasar boru hattı: `LivingIncomingDamageEvent` → `DamageContainer` → `actuallyHurt` içinde `setReduction(Reduction.ARMOR, …)` → kayıtlı `IReductionFunction`'lar vanilla azaltma değerini alıp değiştirir → `LivingDamageEvent.Pre/Post`. `Player#actuallyHurt` aynı kancaları içerir.

## Karar
1. **Formül:** `LivingIncomingDamageEvent` dinleyicisi (`EventPriority.HIGHEST` — fonksiyonlar ekleme sırasıyla çalışır; bizimki ilk olsun, başka bir modun modifier'ı bizim sonucumuzun üstünde çalışsın) her hasarda `event.addReductionModifier(DamageContainer.Reduction.ARMOR, fn)` ekler. `fn(container, vanillaReduction)`:
   - kaynak `#minecraft:bypasses_armor` ise `vanillaReduction`'ı (= 0) aynen döndür (büyü, düşme, açlık… vanilla gibi kalır);
   - aksi halde `H = container.getNewDamage()`, `A = victim.getArmorValue()`, `T = armor_toughness` ile `oran = min(cap, A/(A+k1+k2·H·tau))`, `tau = t0/(t0+T)` (Faz 2a; ilk hali `tau`'suzdu); ardından vanilla ile aynı adım: `source.getWeaponItem() != null` ise `oran = clamp(EnchantmentHelper.modifyArmorEffectiveness(level, silah, victim, source, oran), 0, 1)`; `H · oran` döndür.
   - **`H`'nin tanımı:** fonksiyon çağrıldığı andaki `getNewDamage()` = kalkan bloğu, kask çarpanı (`damages_helmet` ×0,75) ve donma çarpanından **sonraki**, zırhtan önceki hasar; i-frame içinde gelen daha güçlü vuruşta yalnızca *aşan kısım* (`hasar − lastHurt`). Sim ve testler aynı tanımı kullanır.
   - Katsayılar (Faz 2a: `k1=30`, `k2=8`, `cap=0,90`, `t0=8`; taslak 60/4/0,90 idi) **koda gömülmez**: `data/balance/combat.csv` ← `tools/balance/tiers.py` → datapack → `TierRegistry` (yalnızca sunucuda, hasar anında okunur → datapack uygundur).
2. **Attribute tavanları:** access transformer `public-f net.minecraft.world.entity.ai.attributes.RangedAttribute maxValue`; mod kurucusunda (iki tarafta, kayıtlardan önce, sıralı tek yer — LucidRem dersi 9) `ARMOR`, gerekirse `ARMOR_TOUGHNESS` ve `MAX_HEALTH` maxValue'ları **teknik tavana** yükseltilir. Teknik tavan bir denge değeri değildir (denge tavanları formüldedir: `cap`, yumuşak tavanlar); ekipman toplamının çok üstünde seçilir (takım 310, katalizör+seviyeyle "AR 1002" — `docs/06`). Sayının kaynağı `tools/balance/` üretimidir ama **jar'a gömülü** okunur (classpath kaynağı ya da üretilmiş sabitler; biçim Faz 2b): datapack/`TierRegistry` mod kurucusundan sonra ve dünya başına yüklenir, attribute nesnesi ise globaldir ve istemci aynı tavanı bilmek zorundadır.
3. Vanilla `minecraft:armor` attribute'u **korunur** (kendi `theoria:armor`'umuz yok) → Jade/tooltip/diğer modlar doğru değeri okur; vanilla eşya override'ları (D6) normal attribute modifier'larıyla çalışır.
4. Mixin **yok**; `CombatRules`'a dokunulmaz. Vanilla zırh adımı yine çalışır: azaltma sonucunu modifier'ımız ezer, zırh yıpratması (`hurtArmor`) vanilla yolunda kalır.

## Kanıt
**[log]** `./gradlew runGameTestServer` → `All 5 required tests passed :)` (2026-11-09 16:46; 2026-11-10 06:34'te yeniden, aynı çıktı). `docs/adr/kanit/s6_damage_mob.txt`, `s6_damage_player.txt`:
```
vanilla ARMOR attribute range: [0.0, 30.0]; CombatRules MAX_ARMOR=20 (=> max 80% reduction)
A vanilla cap, base armor 300 -> getArmorValue=30; 100 dmg mobAttack -> took 76.0000
B cap lifted, vanilla formula, armor 300 -> took 20.0000 (vanilla clamps effective armor to 20)
C cap lifted + Reduction.ARMOR modifier (armor/(armor+50)), armor 300 -> took 14.2857; vanilla reduction seen by modifier=80.0000
D base 30 + item-style modifier +270 -> getArmorValue=300; spike formula -> took 14.2857
E bypasses_armor source (magic) with spike formula -> took 100.0000 (expect 100)
player (survival, NORMAL difficulty) armor 300: vanilla formula took 20.0000, spike formula took 14.2857
```
Kod (Faz 1'den beri kalıcı regresyon testi): `mods-src/theoria/src/main/java/dev/theoria/devtest/VanillaAssumptions.java` — GameTest `theoria:vanilla_armor_caps_mob` / `_player` (eski adları `s6_damage_*`). Spike'ın kapsamı: sunucu tarafı, toughness 0, silahsız `mobAttack`, tavan test içinde (try/finally) yükseltildi.

**[kaynak]**
- `LivingEntity#actuallyHurt` ve `Player#actuallyHurt`: `setReduction(ARMOR, getNewDamage() − getDamageAfterArmorAbsorb(…))` koşulsuz çağrılır (`bypasses_armor` kaynağında değer 0'dır); `DamageContainer#setReduction` → fonksiyonlar ekleme sırasıyla → `newDamage = max(newDamage − azaltma, 0)`.
- `Reduction.ENCHANTMENTS` yalnızca EPF > 0 iken, `MOB_EFFECTS` yalnızca Direnç varken set edilir; "vanilla azaltma tetiklenmezse fonksiyon çalışmaz" (`DamageContainer` javadoc) → bu iki türe eklenen modifier yoktan azaltma **üretemez**, yalnızca var olanı değiştirir.
- `CombatRules.getDamageAfterAbsorb`: `weaponItem != null` ise `modifyArmorEffectiveness` + `clamp(…, 0, 1)`.
- `LivingEntity#hurtServer`: kalkan → donma/kask çarpanı → `setNewDamage` → i-frame dalında `setReduction(INVULNERABILITY, lastHurt)`.
- `AttributeInstance#calculateValue` → `attribute.sanitizeValue` (her taraf kendi `maxValue`'suyla hesaplar); sync paketinde kırpma yok; vanilla'da `getMaxValue()`'yu okuyan başka kod yok.

**[çıkarım]** (Faz 0'da denenmemişti): istemcide `getArmorValue()` = 300; tavanın mod kurucusunda yükseltilmesi; `modifyArmorEffectiveness` birleşimi; i-frame içindeki ikinci vuruş → **Faz 2b'de [log]'a döndü** ("Faz 2b sonucu"). Hâlâ [çıkarım]: "paket listemizde `Reduction.ARMOR` modifier'ı ekleyen mod yok" (jar'lar taranmadı; Faz 13).

## Sonuçlar
- ✅ %80 tavanı kalkar; 300+ zırh anlamlı. Mob ve oyuncu yolları aynı kancadan geçer. [log]
- ✅ Formül veri-güdümlü; GameTest ile ±%0,01 doğrulanabilir → Faz 2 DoD ("±%10") için `/theoria test balance` aynı GameTest altyapısını kullanır.
- ⚠ Azaltmalar **çarpılır**: zırh (≤ `cap` 0,90) × büyü koruması (vanilla EPF 20 → ≤ %80) × Direnç efekti. Tek tek tavanlar alt alta %98 eder; ADR-004 ile Netherite, Koruma IV×4'ü (EPF 16) tam tavana (V×4 = 20) taşır. → **Faz 2a:** büyü koruması yarıya ölçeklendi (≤ %40), ekipmandan en az %6 hasar geçer.
- ⚠ Toughness: taslak formül `T` kullanmıyordu → **Faz 2a:** `tau = t0/(t0+T)` büyük-vuruş terimini küçültür.
- ⚠ 26.x i-frame'i `LivingEntity#damageCooldownTime` alanında (`invulnerableTime` değil) — test/ölçüm kodu bunu sıfırlamalı. [log]
- HUD (Faz 3) zırh sayısını istemcide `getArmorValue()`'dan okur; tavan iki tarafta kalktığı için 310 doğru görünür. [kaynak/çıkarım]

## Sonraki fazlara yükümlülükler
- ✅ **Faz 2a (sim) — tamamlandı, aşağıda "Faz 2a sonucu".** Liste: `k1/k2/cap`; toughness politikası; **toplam azaltma** politikası (zırh × EPF × Direnç — toplam tavan mı, `Reduction.ENCHANTMENTS` modifier'ıyla yeniden biçimleme mi); `H` yukarıdaki tanımla; zırh yıpranması (parça başı `max(1, H/4)`/vuruş ↔ dayanıklılık eğrisi `80·1,33^idx`; gerekirse `ArmorHurtEvent#setNewDamage`); vanilla mob'ların küçük zırh değerleri (zombi 2) yeni formülde.
- ✅ **Faz 2b — tamamlandı (aşağıda "Faz 2b sonucu").** İş listesi: "Faz 2a sonucu"ndaki 1-6 + dinleyici + kurucuda tavan; S6'nın A–E + oyuncu senaryoları kalıcı GameTest'e taşınır ve şunlar **eklenir**: Breach'li silah, i-frame içinde ikinci vuruş, toughness > 0, kurucu sonrası `Attributes.ARMOR` tavanı, istemci tarafı `getArmorValue()` (istemci testi ya da Faz 3 HUD kontrolü). DoD: oyun-içi ölçüm sim ile ±%10.
- **Faz 3:** HUD DoD'sindeki "zırh 310" ekranı Faz 2b tavanına bağlıdır (sıra doğru: Faz 2 → Faz 3).
- **Faz 5:** Jade vb. göstergeler yüksek zırh/HP'de ikon satırı taşırıyor mu — config kontrolü.
- **Faz 8:** boss HP > 1024 ise `MAX_HEALTH` tavanı aynı yerde yükseltilir; mob zırh ölçeklemesi aynı formülden geçer.
- **Faz 13:** paketteki modlar `Reduction.ARMOR` modifier'ı ekliyor mu — tekrar kontrol.

## Geri dönüş planı
- AT kırılırsa (NeoForge AT biçimi değişir): `@Mixin(RangedAttribute.class)` `sanitizeValue` için belirli attribute örneklerine `@ModifyReturnValue` (MixinExtras NeoForge'da yerleşik) — tek sınıf.
- `addReductionModifier` kalkarsa: `LivingDamageEvent.Pre`'de `getContainer().getReduction(Reduction.ARMOR)` ile vanilla azaltmayı geri ekleyip kendi azaltmamızı düşerek `setNewDamage(...)` (sonraki EPF/Direnç azaltmaları orantılı olduğu için yeniden ölçeklenir) — aynı sonuç, daha kırılgan.
- Son çare: `LivingEntity#getDamageAfterArmorAbsorb` üzerine mixin (`Player` aynı metodu kullanır).

## Faz 2a sonucu (2026-11-10) — `docs/06 §6-§8`, `tools/balance/sim.py`
Sayılar `data/balance/combat.csv` + `tiers.csv`'dedir; beklenen değerler `data/balance/sim_cases.csv` (26 senaryo).
1. **Zırh modifier'ı** (`Reduction.ARMOR`): `tau = toughness_half/(toughness_half + T)`; `oran = min(armor_cap, A/(A + armor_k1 + armor_k2·H·tau))`; `A ≤ 0` ise 0; sonra Karar 1'deki `armor_effectiveness` adımı; `H·oran` döner. `A` = `getArmorValue()` (int, aşağı yuvarlar), `T` = `getAttributeValue(ARMOR_TOUGHNESS)`.
2. **Büyü modifier'ı** (`Reduction.ENCHANTMENTS`, aynı dinleyicide eklenir): `vanillaAzaltma × ench_scale` döner. EPF'yi yeniden hesaplamaya gerek yok: vanilla azaltması `hasar × min(EPF,20)/25` olduğundan ölçeklemek "EPF başı %2, ≤ %40" demektir. Bu tür yalnızca EPF > 0 iken set edildiği için modifier yoktan azaltma üretmez (yukarıda [kaynak]). [çıkarım: çalıştırılmadı → 2b GameTest `epf_16`, `epf_20`, `epf_over_cap`, `bypass_armor_epf`]
3. **Direnç efektine ve toplam azaltmaya dokunulmaz** (ayrı toplam tavan yok): zırh %90 × büyü %40 → en az %6 hasar geçer; Direnç geçicidir.
4. **Zırh aşınması vanilla kuralında** (`ArmorHurtEvent` dinleyicisi yok): parça başı `(int) max(1, H/4)` [kaynak: `LivingEntity#doHurtEquipment` → `CommonHooks.onArmorHurt`]; denge dayanıklılık eğrisiyle (`80·1,5^idx`).
5. **İyileşme ölçeği** (yeni kanca, bu ADR'nin boru hattı dışında): `LivingHealEvent#setAmount(miktar × azami can / MAX_HEALTH taban değeri)` — oyuncuda taban 20; mob'larda kademe can çarpanına eşit olur. `LivingEntity#heal` her iyileşmeyi bu olaydan geçirir (`FoodData#tick` → `player.heal`, iksir, Yenilenme) [kaynak; çalıştırılmadı].
6. **Mob hasar çarpanı** (`docs/06 §4`): kademeli mob'un *neden olduğu* hasar `LivingIncomingDamageEvent#setAmount` ile çarpılır — kaynak `#bypasses_armor` ise `mob_bypass_mult`, değilse `mob_dmg_mult`; bu yüzden `attack_damage` attribute'u ayrıca çarpılmaz (çift sayım olur). Zırh modifier'ı `H`'yi fonksiyon çağrıldığında okuduğu için çarpılmış hasarı görür. Sıra [kaynak, çalıştırılmadı]: `Player#hurtServer` zorluk ölçeği → `LivingEntity#hurtServer` başında olay (`onEntityIncomingDamage`) → `damage = container.getNewDamage()` → kalkan → kask/donma → i-frame → `actuallyHurt` (zırh modifier'ı). Yani çarpan zorluk ölçeğinden sonra, kalkandan önce uygulanır; Kolay'daki `min(d/2+1, d)` çarpandan önce hesaplanır. 2b GameTest'i bunu sabitler.
7. **Teknik tavanlar**: `attr_max_armor` 1.000.000 · `attr_max_armor_toughness` 1.000 · `attr_max_health` 1.000.000 · `attr_max_attack_damage` 1.000.000 (Karar 2'deki gibi jar'a gömülü).
8. Test notları: `sim_cases.csv`'deki `raw` = Normal zorlukta zırh öncesi `H`; oyuncu hedefli testte zorluk ölçeği olan kaynak (`mobAttack`) Normal'de ×1'dir, başka zorlukta beklenen değer kayar. i-frame için `damageCooldownTime` sıfırlanır (yukarıda). DoD satırı `armor300_vs_t8`: 26,019 → 11,514.

## Faz 2b sonucu (2026-11-11) — kod `dev.theoria.balance`, testler `devtest/BalanceTests`
**Uygulama.** Tek `LivingIncomingDamageEvent` dinleyicisi (`HIGHEST`, `DamageHooks`): (1) saldıranın mob kademesi varsa `event.setAmount(miktar × çarpan)` (`#bypasses_armor` ise `mob_bypass_mult`, değilse `mob_dmg_mult`; saldıran = `source.getEntity()`, yani okun sahibi iskelet); (2) `Reduction.ARMOR` modifier'ı (formül + Breach adımı); (3) `Reduction.ENCHANTMENTS` modifier'ı (`× ench_scale`). `LivingHealEvent` dinleyicisi iyileşmeyi `azami can / MAX_HEALTH taban değeri` ile çarpar. Katsayılar `TierRegistry`'den (`AddServerReloadListenersEvent`; `data/theoria/balance/combat/default.json` + `tier/<id>.json`), teknik tavanlar `TechnicalCaps`'ten (`theoria/technical_caps.json`, jar; mod kurucusunda iki tarafta). Veri henüz yüklenmediyse hiçbir kanca vanilla davranışa dokunmaz.

**Kanıt [log]** (`./gradlew runGameTestServer` → `All 19 required tests passed`; çıktılar `mods-src/theoria/build/dev-out/balance_*.txt`; istemci: `runDevClient` → `RESULT PASS`):
- 26 `sim_cases.csv` satırı gerçek `hurtServer` çağrısıyla: **mob kurbanında** (dünyadaki domuz + dünyaya hiç girmeyen atılıp-giden domuz) ve **hayatta kalma modundaki oyuncuda** (Normal) simülasyondan ≤ %0,025 sapma; `armor300_vs_t8` → **11,5143** (beklenen 11,5144).
- Kurucuda tavan: ARMOR/ARMOR_TOUGHNESS/MAX_HEALTH/ATTACK_DAMAGE `maxValue` teknik tavana yükseldi (`modloading-worker` iş parçacığı, hem sunucu dist'i hem istemci); `getArmorValue()` 300 (taban 300 ve taban 30 + `ADD_VALUE` 270 ikisinde de); **istemci** tarafı: `/attribute` ile 300 zırh + 214 can → istemcide `getArmorValue()=300`, `getMaxHealth()=214`.
- Mob hasar çarpanı kaynak türüne göre: yakın dövüş 3 → 26,019, ok 2 → 17,346 (okun sahibi kademeli iskelet), patlama 10 → 86,73, zırhı delen büyü 6 → 25,8 (= ×4,3); kademesiz mob → vanilla.
- **Sıra:** Kolay zorlukta oyuncuya taban 3: 21,68 = `min(3/2+1, 3) × 8,673` (zorluk ölçeği çarpandan önce; ters sırada 14,01 olurdu); Zor ×1,5.
- Breach (gürz + Breach IV, zırh 2000): kesir −0,6 kayıyor; ölçülen 38,8477 = beklenen; silahsız aynı vuruş 8,8477. (İlk testimde zırh 300'dü ve kesir 0'a kırpılıyordu — test Breach'i gizlerdi; zırh 2000'e çıkarıldı ve "klamp arkasına saklanmıyor" assert'i eklendi.)
- i-frame: zırh 100, vuruş 10 → 5,2381; i-frame içinde 25 → yalnızca üstteki 15 zırhlanıyor, 9,0000 (= beklenen); sonra 20 → 0. `H` tanımı doğrulandı.
- Mob kademesi: tier 8 zombi can 20 → 195,81 (×9,7907), zırh 2 → 17,346 (×8,673); yaralı mob can oranını koruyor; kaydet/yükle (NBT) sonrası kademe + modifier'lar duruyor; iki kez atama yığılmıyor; kademe 0 attachment'ı siler; bilinmeyen kademe reddedilir.
- Öldürme vuruşları: 14 kademede ref oyuncu (kademe kılıcı × (1 + ref bonus)) eşit kademe zombiyi `tiers.csv` `kills` sütunuyla birebir (4,5 – 5,7) öldürüyor — mob can + mob zırhı ölçeklemesi ve formül mob kurbanında.
- İyileşme: 214 canda `heal(1)` → +10,7; `FoodData` doğal rejenerasyonu hızlı (10 tick, doygun) +10,7, yavaş (80 tick) +10,7; 20 canda vanilla +1. `LivingEntity#heal` her yolda (FoodData, iksir, Yenilenme) [kaynak] — doğal rejenerasyon [log].
- Veri: datapack JSON ↔ `combat.csv`/`tiers.csv` (14 kademe) ve `technical_caps.json` ↔ `attr_max_*` eşleşiyor; `/theoria test balance` 6 kontrol geçiyor (hem GameTest'te hem gerçek entegre sunucuda, istemci duman testinden).

**Öğrenilen / düzeltilen.**
- Vanilla `CombatRules` büyük vuruşta zırhı önce `hasar/(2+T/4)` kadar aşındırır: 30 zırh ↔ 100 hasar = **%24** azaltma (76 alınır), "%80 tavan" yalnızca etkin zırh ≥ 20 iken. `vanilla_combat_rules_cap` testi bunu sabitler (ilk beklentim "20 alınır" yanlıştı; S6 logunda da 76 yazıyordu).
- Ölçüm kurbanı canı: 20 000 canda float çözünürlüğü (ulp 0,002) küçük vuruşlarda %0,4 sapma verdi (`small_hit_cap`, 0,1 hasar); 1000 canda ulp 0,00006. `BalanceProbe` kurbanın ölmesini hata sayar.
- Atılıp-giden varlık (dünyaya eklenmeyen domuz) `hurtServer`'dan sorunsuz geçiyor: `ChunkMap.sendToTrackingPlayers*` izlenmeyen varlığı atlıyor [kaynak] ve test geçti [log]. Kurban olarak zombi **seçilmedi**: `Zombie#hurtServer` Zor'da takviye çağırabilir (canlı dünyada yan etki).
- `EntityType.PIG` değil **`EntityTypes.PIG`** (26.x sabitleri `EntityTypes`'ta).
- Brigadier aynı kökü (`literal("theoria")`) birleştiriyor → `/theoria tier` kendi paketinde, `TheoriaCommand`'a dokunmadan kayıtlı.

**Hâlâ açık / devreden.** (a) Bu ADR'nin Karar 1'indeki `HIGHEST` önceliğinin başka modlarla etkileşimi → paket mod listesi taraması Faz 13. (b) Elde silah taşıyan mob'lar ve ağır elit mob'lar (`docs/06 §4`) Faz 6/8. (c) Dedicated server (`runServer`) açılışı denenmedi — EULA kabulü kullanıcıya ait; `runGameTestServer` sunucu dist'inde çalıştığı için istemci sınıfı sızıntısı olsaydı yakalanırdı. (d) Gerçek oynanış hissi (7 vuruş iyi mi?) yalnızca elle oynayınca anlaşılır; `/theoria tier @e[type=zombie,limit=1,sort=nearest] 8` ile denenebilir.
