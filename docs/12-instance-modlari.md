# 12 — Mevcut Prism instance'larından modlar

Taranan: `~/Library/Application Support/PrismLauncher/instances/` (8 instance, 448 jar → 305 Modrinth projesi). Her proje için **NeoForge 26.3** sürümü Modrinth'ten sorgulandı (9 Kas 2026). Sonuç: **205 mod NeoForge 26.3'te var, 100'ü yok** (çoğu Fabric-yerli, ya da LucidRem instance'ındaki 1.21.1 sürümleri).
Yeniden üretmek için: `python3 -I tools/modmatrix/instances.py` (yazdığı `inst_mods.json` çalışma dizinine düşer).

## 1. Vulkan'da çalıştığını gördüğün yığın (`Vulkan Optimization` + `Vulkan Realistically` instance'ları)
Ayar: `preferredGraphicsBackend:"vulkan"`, Sodium 0.9.3-beta, **Vitrail 0.13.0-beta** + `ComplementaryReimagined_r5.9.3` (Vitrail log'u: pipeline/shader module cache çalışıyor).
| Rol | Mod | NeoForge 26.3 |
|---|---|---|
| Render | Sodium · Sodium Extra · Reese's Sodium Options | ✅ ✅ ✅ |
| **Shader** | **Vitrail Shaders** (OptiFine-format paketleri native Vulkan'da çalıştırır) | ✅ 0.13.0-beta |
| Performans | BadOptimizations · ImmediatelyFast · ScalableLux · MoreCulling · Dynamic FPS · EntityCulling · FerriteCore · Lithium · ModernFix (**modernfix-mvus** çatalı) · Ksyxis · Ixeris · Better Block Entities · Noisium(yok) | ✅ (Noisium ❌) |
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
