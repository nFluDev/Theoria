# 12 — Mevcut Prism instance'larından modlar

Taranan: `~/Library/Application Support/PrismLauncher/instances/` (8 instance, 448 jar → 305 Modrinth projesi). Her proje için **NeoForge 26.3** sürümü Modrinth'ten sorgulandı (9 Kas 2026). Sonuç: **205 mod NeoForge 26.3'te var, 100'ü yok** (çoğu Fabric-yerli, ya da LucidRem instance'ındaki 1.21.1 sürümleri).
Yeniden üretmek için: `python3 -I tools/modmatrix/instances.py` (yazdığı `inst_mods.json` çalışma dizinine düşer).

## 1. Vulkan'da çalıştığını gördüğün yığın (`Vulkan Optimization` + `Vulkan Realistically` instance'ları)
Ayar: `preferredGraphicsBackend:"vulkan"`, Sodium 0.9.3-beta, **Vitrail 0.13.0-beta** + `ComplementaryReimagined_r5.9.3` (Vitrail log'u: pipeline/shader module cache çalışıyor).
| Rol | Mod | NeoForge 26.3 |
|---|---|---|
| Render | Sodium · Sodium Extra · Reese's Sodium Options | ✅ ✅ ✅ |
| **Shader** | **Vitrail Shaders** (OptiFine-format paketleri native Vulkan'da çalıştırır) | ✅ 0.13.0-beta |
| Performans | BadOptimizations · ImmediatelyFast · ScalableLux · ~~MoreCulling~~ (çıkarıldı 2026-11-19: Sodium beta.1 ile uyumsuz) · Dynamic FPS · EntityCulling · FerriteCore · Lithium · ModernFix (**modernfix-mvus** çatalı) · Ksyxis · Ixeris · Better Block Entities · Noisium(yok) | ✅ (Noisium ❌) |
| Fabric-yerli (yerine yok) | **C2ME**, **VMP**, Fadeless, ZFastNoise vb. | ❌ → Q1 |
| Kanat | Fantastic Wings | ✅ 26.3.1 |

→ Vitrail, Fantastic Wings ve Sodium bir arada kanıtlanmış; NeoForge'da aynı yığın kurulabilir. Karar: **Vitrail + Complementary Reimagined** (paket zaten diskinde).

## 2. Faydalı bulduklarım — NeoForge 26.3'te mevcut
| Grup | Modlar |
|---|---|
| **Çekirdek (zaten planda)** | JEI · REI(alternatif, JEI seçili) · Curios · Fantastic Wings · Lootr · Terralith · TerraBlender · Waystones · Jade · Explorer's/Nature's Compass · Journeymap · Mouse Tweaks · Chunky |
| **Dövüş/hareket** | Better Combat · Combat Roll · Reliable Gliders · Wall Jump · Player Animation Library · GeckoLib (mob animasyonu için) |
| **Envanter QoL** | Inventory Essentials · Inventory Profiles Next · Inventory Sorting · Crafting Tweaks · Trash Slot · Easy Anvils · Easy Magic · Enchantment Descriptions · Legendary Tooltips · Shulker Box Tooltip |
| **Depolama (opsiyonel)** | Sophisticated Backpacks/Storage · Tom's Storage · Traveler's Backpack · AE2 (+Wireless Terminals) · Carry On · Easy Villagers |
| **Hayat kalitesi** | Gravestone (+Curios uyumu) · Your Items Are Safe · Open Parties and Claims · FallingTree · Right Click Harvest · Replanting Crops · Easy Homes · Universal Bone Meal |
| **Dünya/yapı (geçici doldurucu)** | Dungeons and Taverns (+Ancient City Overhaul) · Structory (+Towers) · Towns and Towers · Hopo (Mineshaft/Ruined Portals/Underwater) · Incendium · Nullscape · End Remastered · MES/Moog's · Tectonic · Lithostitched · Amplified Nether · Snowy Tents · Illager Arena/Warship |
| **Mob** | Mutant Monsters (26.3.1) |
| **İlerleme** | `skills` (Puffish Skills benzeri, 0.19.2) — kendi Durum ekranımız var, **kullanmıyoruz**; sadece UI ilham/uyumluluk |
| **Altyapı** | Cloth Config/YACL/Fzzy Config/Puzzles Lib/Balm/Bookshelf/Architectury… (yalnızca bağımlılık gerekirse) |

## 3. NeoForge 26.3'te **yok** (aranan ana modlar)
Apotheosis ailesi · Silent Gear/Lib · Artifacts · **Relics** (`relics-mod`, `more-relics`, `reliquified-artifacts`) · Regions Unexplored · The Undergarden · Deeper and Darker · Aether (+Deep, Villages) · Alex's Mobs · Mowzie's · Cataclysm · Ice and Fire · Born in Chaos · Bosses of Mass Destruction · Simply Swords · YUNG's (API/Dungeons/Nether Fortresses) · When Dungeons Arise · Towers of the Wild · Mekanism/Create/Mystical Agriculture · Patchouli/Placebo · **McMouser** · Cosmetic Armor Reworked · Travelers Titles · Modern UI · Create.
→ Bu liste kendi içerik kararlarımızı doğruluyor: Katalizör (Apotheosis yerine), Relics, kozmetik, mob ve yapı üretimi bizde.

## 4. Kontrol: 26.3 instance'larında zaten kurulu olup bizim kapsamda **kullanmayacaklarımız**
Iris (Vulkan modunda çalışmıyor; Vitrail kullanılır — OpenGL'e düşersek Iris yedek) · AppleSkin (kendi barımız) · Colorful Hearts · tüm "Fabric API/Mod Menu/Fabric Language Kotlin" (NeoForge'da gereksiz).

## 5. QoL araştırması (Faz 13, 2026-11-20) — saf artı olanlar
Kaynak: Modrinth API (NeoForge 26.3 sürümü var mı), ChillCraft instance'ının 26.3'te çalışan mod listesi, Modrinth indirme sayıları. **"Saf artı"** = oynanışı/dengeyi değiştirmez, Theoria'nın HUD'u, Kataliz/Relic ipuçları, Curios Max ve Vitrail ile çakışma riski düşük.

**Pakete eklendi (Faz 13):** Controlling (kontroller arama çubuğu — kullanıcı isteği) · Crafting Tweaks · Enchantment Descriptions · **Mac Input Fixes** (artık temel). Zaten vardı: Jade · Mouse Tweaks · Inventory Profiles Next · Xaero · Waystones · JEI + JER · OK Zoomer · Veinminer · Sophisticated · Lootr · AE2.

**Önerilen, EKLENMEDİ (onay bekliyor)** — hepsi 26.3 NeoForge'da var:
| Mod | Ne yapar | Not |
|---|---|---|
| **NetherPortalFix** 26.3.0.1 | Nether portalları eşleme hatalarını düzeltir | saf düzeltme, ayar yok |
| **Shulker Box Tooltip** 5.4.3 | Shulker içeriğini ipucunda gösterir | 41 M indirme; Sophisticated ile çakışmaz |
| **BetterF3** 20.0.0 | F3 ekranını okunur yapar | saf arayüz |
| **Durability Tooltip** 1.2.0 | Dayanıklılığı sayıyla yazar | yüksek dayanıklılık sayılarımızla (≤ 15 570) işe yarar |
| Held Item Tooltips 26.3.0 | Elindeki eşyanın büyülerini hotbar üstünde gösterir | **HUD çakışması denenmeli** (bizim HUD alttaki satırları çiziyor) |
| Legendary Tooltips 1.6.3 | Nadir eşyalara çerçeveli ipucu | **görünümü değiştirir** (Relic/Kataliz ipuçlarına da uygulanır) — zevk meselesi |
| No Chat Reports 2.21.3 · Chat Heads 1.3.2 | Sohbet imza uyarıları / oyuncu kafası | yalnız çok oyunculu |
| Sound Physics Remastered 1.5.4 (beta) | Ses yankı/engel | performans maliyeti, beta |
| Visual Workbench · ItemPhysic Lite | Tezgâhta eşya / yere düşen eşya fiziği | süs; Vulkan altında denenmedi |
| Client Tweaks 26.3.0.1 | Birçok küçük istemci ayarı | yapılandırması geniş, seçici açılmalı |

**Bilerek önerilmeyen:** FallingTree (Veinminer ile aynı işi yapar — ağaç/damar iki kez) · LambDynamicLights (Vitrail altında dinamik ışık desteği bilinmiyor) · AppleSkin (kendi SAT göstergemiz var) · Controlify (kontrolcü; klavye-fare oyuncusu için gereksiz).

**26.3 NeoForge'da Modrinth'te YOK** (ChillCraft'ta CurseForge'dan vardı → CurseForge yayınında düşünülebilir): Right-Click-Harvest · cpw'nin Inventory Sorter'ı · Better Ping Display · EMI.

## 6. Kullanıcı listesi incelemesi (Faz 13, 2026-11-20) — eklenenler ve elenenler
Her mod Modrinth API'siyle (NeoForge 26.3 sürümü, bağımlılıklar) ve Theoria sistemleriyle (HUD, IPN, Curios Max, Kataliz/XP, Lootr, Vitrail) karşılaştırıldı; **eklenenler dev istemcisinde yüklenip duman testinden geçti** (ERROR yok; yalnız zararsız `@OnlyIn` uyarıları).
| İstek | Karar | Not |
|---|---|---|
| Dynamic Lights | ✅ LambDynamicLights 4.13.0 | Shader açıkken çakışma denenmedi (Prism'de) → Mods ▸ LambDynamicLights ▸ `mode = off` ile kapanır |
| NetherPortalFix · Shulker Box Tooltip · BetterF3 | ✅ | BetterF3 → Cloth Config artık temel pakette |
| Durability Tooltip | ✅ **opsiyonel, varsayılan AÇIK** | Modun kendi aç/kapa anahtarı yok → paket kurulumunda işareti kaldırarak kapatılır (`[opsiyonel-acik]` bölümü) |
| Mouse Tweaks · Crafting Tweaks · Jade · Enchantment Descriptions · Controlling · Waystones · Nature's/Explorer's Compass | ✅ zaten pakette | |
| TrashSlot · Visual Workbench · Chat Heads | ✅ | |
| AppleSkin | ✅ ama **HUD katmanları kapalı** | Kendi HUD'umuz var; AppleSkin vanilla çubuk konumlarına çizer. Yemek değerleri ipucunda kalır (`config/appleskin-client.toml`) |
| RightClickHarvest | ✅ 4.7.1 (+ JamLib) | |
| Carry On | ✅ 2.12.2, tuş **Ctrl+K** | `options.txt key_key.carry.desc:key.keyboard.k:CONTROL`; Prism instance'ına da yazıldı. Varsayılanda bosslar/ender ejderhası/düşman yaratıklar taşınamaz |
| Tree Harvester | ✅ 26.3.0-11.1 (+ Collective) | Balta + çömelme; Theoria baltaları `#minecraft:axes`'te. Veinminer yalnız cevher (`#c:ores` + kazma) → çakışma yok |
| Corpse (+ Corpse x Curios compat) | ✅ | Curios yuvaları da cesete girer; kozmetik/istatistik (attachment) ölümde kalır |
| Easy Anvils · Easy Magic · Trading Post · Clumps | ✅ | Clumps XP küreleri birleştirir; toplam XP `XpChange` ile sayılır → istatistik XP'si etkilenmez |
| Sound Physics Remastered | ✅ 1.5.4 **BETA** | Performans maliyeti; beta |
| Double Doors (çift kapıyı tek tıkla aç) | ✅ 26.3.0-7.5 | Kullanıcının tarif ettiği mod bu (Automatic Doors DEĞİL: o, yaklaşınca tüm kapıları — demir dahil — kendiliğinden açar, eklenmedi). Varsayılan: kapılar açık, çit kapısı/kepenk kapalı (ayardan açılır); çömelince tekli açılır |
| Leave My Bars Alone | ❌ | Bindiğinde HUD'u kendi çiziyoruz (araç satırı dahil); modun düzelttiği vanilla çubuklar zaten çizilmiyor |
| Inventory Essentials | ❌ | IPN ile aynı işleri (sıralama, taşıma) ve orta tıkı yapar → çakışır |
| Polymorph(+) | ❌ | Modrinth'te NeoForge 26.3 sürümü yok |
| Eating Animation (gerçek yeme) | ❌ | Modrinth'te NeoForge 26.3 sürümü yok (3 benzeri de yok); CurseForge'a bakılmadı |
| Serilum's QoL Bundle | ❌ | Saf QoL değil: Always a Wither Skull, Automatic Doors, Bottled Air vb. oynanış değişiklikleri; içindeki RightClickHarvest ve Tree Harvester ayrı eklendi |
| "Tweaks OG" | ❓ | Modrinth'te bu adla mod bulunamadı; "Tweaks" (gamerule değiştirici) farklı bir mod |
| That Makes Sense (Tweaks) | ❌ | Varsayılan AÇIK 30'a yakın oynanış değişikliği (creeper hasarı yok, totem yığını, anvil sınırı, köylü fiyatları…) — Theoria dengesini ve Easy Anvils/RightClickHarvest'i çiftler. İstenirse yalnız seçili anahtarlarla eklenir |

### Türkçe dil kapsamı
Oyun dili `lang:tr_tr` seçilince: vanilla + Theoria tamamen Türkçe. Üçüncü taraf modlardan **eksik satırlar** `data/lang_tr/<mod>.json`'dan (`generator/lang_tr.py`) 26 mod için **644 satır** olarak Theoria jar'ına eklenir (modun kendi tr_tr'siyle birleşir; dev istemcisinde doğrulandı). Özel adlar İngilizce bırakıldı: Waystone/Sharestone/Portstone/Warp Plate, Sophisticated …, Trading Post, mod adları, eşya/blok adları.
**Kapsam dışı (ayar/yönetici metni, oyunda nadir görünür):** Xaero minimap + dünya haritası ayar ekranları (~800 satır), ModernFix, Sodium/Sodium Extra/Reese's, Collective, Sound Physics, JEI ayar açıklamaları, Entity Culling, Dynamic FPS. Oyun dili şu an `en_us` (kullanıcının Prism instance'ı); Türkçe için Ayarlar ▸ Dil.

