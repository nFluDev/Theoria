# 11 — Mod erişilebilirliği matrisi

Kaynak: Modrinth API, `python3 -I tools/modmatrix/matrix.py --md` · **9 Kasım 2026 (Faz 0 yeniden çalıştırma)**. Yalnızca **NeoForge** build'leri sayılır (ilk planlama turundaki tablo loader ayırmıyordu). Hücre: en yeni sürüm (r=release, b=beta, a=alpha, tarih). `—` = o sürüm için NeoForge build'i yok; `no-project` = slug Modrinth'te yok (CurseForge'da olabilir).

**Sürüm kararı: 26.3** (ADR-001). **Faz 5 (11 Kas):** liste `pack/modlist.toml` → `pack/packwiz/` (42 metadosyası: 35 temel + 6 opsiyonel/yedek + shaderpack); packwiz'in eklediği bağımlılıklar `[bagimlilik]` bölümünde sabit: balm, shogi (Waystones), cloth-config (artık yalnızca opsiyonel Better Combat; More Culling çıkarıldı), lithostitched (Terralith) — opsiyonel olanlar `[bagimlilik-opsiyonel]`. Complementary Reimagined r5.9.3 de matriste (`tools/modmatrix/matrix.py`, shaderpack = yükleyici süzgeci yok). **D19:** Iris yok, OpenGL hedeflenmez. 26.2'de tek eksik Mac Input Fixes; 26.3'te paket listesinin 33/33'ü mevcut, çoğu daha yeni.

## Paket listesi (`pack/modlist.toml`)
| Bölüm | Slug | 26.2 NeoForge | 26.3 NeoForge | Sabit | Durum |
|---|---|---|---|---|---|
| zorunlu | fantastic-wings | 26.2.0 (r) | **26.3.1** (r, 11-02) | 26.3.1 | ok · S2 kanıtlı (Puzzles Lib 26.3.17 gerekir) |
| zorunlu | jei | 30.48.0.244 (b) | **31.17.0.109** (b, 11-05) | 31.17.0.109 | ok (mezz_config 0.6.11 gerekir) |
| zorunlu | curios | 16.0.0+26.2 (r) | **17.0.1+26.3** (r, 11-09) | 17.0.1 | ok |
| performans | sodium | 0.9.2 (r) | 0.9.3-beta.5 (b, 11-12) | **0.9.3-beta.1** (10-15) | ok · **beta.1'e SABİT** (Vitrail 0.13.0-beta ile test edilmiş küme; beta.2-4'te görüntü gecikmesi, bkz. `docs/journal/f09-kapi.md`) |
| performans | sodium-extra | 0.9.5 (r) | 0.9.6-beta.3 (b) | **0.9.6-beta.1** | ok · Sodium beta.1 ile eşli |
| performans | reeses-sodium-options | 2.2.4 (r) | 2.2.6-beta.3 (b) | **2.2.6-beta.1** | ok · Sodium beta.1 ile eşli |
| performans | immediatelyfast | 1.16.5 (r) | 1.17.1 (r) | 1.17.1 | ok |
| performans | badoptimizations | 2.4.1 (r) | 2.4.1 (r) | 2.4.1 | ok |
| performans | scalablelux | 0.3.0-alpha.0.16 (a) | 0.3.0-alpha.0.17 (a) | 0.3.0-alpha.0.17 | ok (sabit adı düzeltildi) |
| performans | moreculling | 1.8.2 (r) | 1.9.2 (r) | — (pakette YOK) | **çıkarıldı 2026-11-19** (`[bilerek-yok]`): Sodium ≥ 0.9.3-beta.2 ister, Vitrail ile görüntü gecikiyor |
| performans | dynamic-fps | 3.11.9 (r) | 3.11.10 (r) | 3.11.10 | ok |
| performans | modernfix-mvus | 5.27.21 (r) | 5.27.23 (r) | 5.27.23 | ok |
| performans | ksyxis | 1.4.6 (r) | 1.4.6 (r) | 1.4.6 | ok |
| performans | lithium | 0.25.3 (r) | 0.26.3 (r) | 0.26.3 | ok |
| performans | ferrite-core | 9.0.0 (r) | 9.0.0 (r) | 9.0.0 | ok |
| performans | entityculling | 1.11.3 (r) | 1.11.3 (r) | 1.11.3 | ok |
| performans | chunky | 1.5.66 (r) | 1.5.66 (r) | 1.5.66 | ok |
| shader | vitrail-shaders | 0.13.0-beta (b) | 0.13.0-beta (b, 10-13) | 0.13.0-beta | ok · S1 kanıtlı (NeoForge + Sodium) |
| qol | jade | 26.2.10 (r) | 26.3.5 (r) | 26.3.5 | ok |
| qol | mouse-tweaks | 26.2-2.31 (r) | 26.3-2.31 (r) | 26.3-2.31 | ok |
| qol | waystones | 26.2.0.13 (r) | 26.3.0.2 (r) | 26.3.0.2 | ok |
| qol | xaeros-minimap | 26.6.2 (r) | 26.6.2 (r) | 26.6.2 | ok |
| qol | xaeros-world-map | 1.47.2 (r) | 1.47.2 (r) | 1.47.2 | ok |
| qol | explorers-compass | 3.3.0 (r) | 3.3.2 (r) | 3.3.2 | ok |
| qol | natures-compass | 3.3.0 (r) | 3.3.1 (r) | 3.3.1 | ok |
| qol | lootr | 1.24.41.124 (r) | 1.25.42.124 (r) | 1.25.42 | ok |
| mac | mac-input-fixes-neoforged | — | 26.10.03-p (b, 10-03) | 26.10.03 | ok · **yedek** (ADR-005): 26.3'te yükleniyor; temel pakette yok |
| dunya | terralith | 2.6.4 (r) | 2.6.5+26.3 (r) | 2.6.5 | ok |
| dunya | terrablender | 26.2.0.0.2 (b) | 26.3.0.0.9 (b) | 26.3.0.0.9 | ok |
| dunya | dungeons-and-taverns | 5.3.2 (r) | 6.1.0 (r) | 6.1.0 | ok |
| opsiyonel | distanthorizons | 3.3.3 (r) | 3.3.4 (r) | 3.3.4 | ok · Vulkan uyumu ölçülmedi (Faz 5) |
| opsiyonel | better-combat | 3.2.2 (r) | 3.2.2 (r) | 3.2.2 | ok |
| opsiyonel | sophisticated-backpacks | 3.26.18 (r) | 3.26.19 (r) | 3.26.19 | ok |

## İzlenenler (pakette değil)
| Slug | 26.2 | 26.3 | Karar |
|---|---|---|---|
| trinkets-updated | 4.1.1 | 4.2.1 | ✗ Curios yeter |
| appleskin | 3.0.10 | 3.0.10 | ✗ kendi SAT göstergemiz |
| iris | 1.11.4 | 1.11.7 | ✗ Vulkan'da çalışmıyor; OpenGL yedeği |
| journeymap | 6.0.10 | 6.0.10 | ⏳ Xaero alternatifi |
| silent-gear | 4.3.0 | — | ✗ |
| **mcmouser** | 26.1-neoforge-1.0.0 | — | ✗ **26.3'te FML reddediyor** (`[26.1,26.3)`, ADR-005) |
| artifacts, relics-mod, apotheosis, apothic-attributes, modernfix, regions-unexplored, yungs-api, yungs-better-dungeons, when-dungeons-arise, alexs-mobs, mowzies-mobs, l_enders-cataclysm, armor-toggle, macos-input-fixes (Fabric), c2me-fabric, vmp-fabric | — | — | ✗ NeoForge 26.x'te yok → kendi sistemlerimiz (Kataliz, Relics, Haven, mob/yapı) |
| biomesoplenty, ice-and-fire-ce, cosmetic-armor-reworked, ftb-quests, puffish-skills | no-project | no-project | ✗ (Modrinth'te bu slug yok) |

**Sonuç:** İstek listesinin büyük kısmı 26.3 NeoForge'da hazır. Eksikler (Apotheosis, Relics, kozmetik, mob/yapı modları) zaten "kendimiz yaparız" kategorisinde.

**Ek:** kullanıcının mevcut Prism instance'larından 205 mod için NeoForge 26.3 doğrulaması → `docs/12-instance-modlari.md` (planlama turu; Faz 5'te yeniden çalıştırılır).

## Faz 11 sonrası eklenenler (2026-11-20, kullanıcı isteği; `pack/modlist.toml` tek kaynak)
| Mod | Sürüm (26.3 NeoForge) | Not |
|---|---|---|
| ok-zoomer | 19.0.0-beta.3 | yakınlaştırma (Zoomify Fabric API bağımlılığı istediği için bırakıldı) |
| inventory-profiles-next + libipn + kotlin-lang-forge | 2.3.8 / 6.9.0 / 2.20.0 | envanter/sandık sıralama, "hepsini taşı/al" düğmeleri, kaydırarak taşıma (varsayılan kısayollar IPN'in kendi ayarlarında); Mouse Tweaks ile birlikte yüklü |
| just-enough-resources-jer | 1.11.0.64 (**alpha**) | JEI'da cevher/mob/loot nerede çıkar; "villager trades" yüklemede uyarı veriyor (zararsız görünüyor) |
| sophisticated-core / -storage / -backpacks | 1.5.40.2492 / 1.5.134.2242 / 3.26.22.2260 | artık **varsayılan açık** (Faz 5'te opsiyonel-kapalıydı) |
| ae2 + guideme | 26.3.2-alpha / 26.3.7-alpha | Applied Energistics 2 (**alpha**) |
| veinminer | 2.14.0 | damarı tek seferde kır (**FTB Ultimine Modrinth'te yok**, yalnız CurseForge; bu onun 26.3 NeoForge alternatifi) |
| **YOK:** FTB Ultimine (CurseForge), Create, ExtendedAE, EMI | — | Modrinth'te 26.3 NeoForge sürümü yok (2026-11-20 taraması); çıkınca `modlist.toml`'a satır |
Doğrulama: `check_pack.py --online` OK; hepsi NeoForge dev istemcisinde Theoria ile birlikte yüklendi: duman senaryosu PASS, `-PdevClient=curios` PASS, günlükte 0 ERROR (IPN: Controlify mixin hedefi yok uyarısı, JER: köylü takasları uyarısı). Prism'de Vulkan+Vitrail altında ve oynanışta (sıralama, AE2…) denenmedi.
