# 10 — Kararlar ve açık sorular

## A. Karara bağlananlar (9 Kas 2026, kullanıcı)
| # | Karar |
|--:|---|
| D1 | **HUD kalp modeli:** ekranda 5 kalp; her kalp 4 çeyrek, **1 kalp = 4 HP, 1 çeyrek = 1 HP**; 20 HP = 5 dolu kalp (kat). 21 HP'de ilk kalbin ilk çeyreği yeni renk. |
| D2 | **Açlık barı kalır, 5 sembol** (10 değil) — yer açmak için tek değişiklik bu. Satürasyon bu barın üzerinde gösterilir. |
| D3 | **Kademe isimleri onaylandı:** Kalay · Bronz · Çelik · Kobalt · Titanyum · **Lonsdaleit** (+ vanilla'lar, Mitril, Orikalkum, Platin). |
| D4 | **Zırh formülü global değişir** (vanilla 20-puan tavanı kalkar). |
| D5 | **Seviye tavanı 9999.** Ömür boyu XP; ek olarak **XP kazanımını artıran büyü** (bir "+%1600 XP" etkisine ulaşabilen) eklenecek. |
| D6 | **Netherite yalnızca katalizör** — zırh/alet/silah yok, kaldırılır. **Yeni id yok:** vanilla eşyaların statları değiştirilir. **Kazı kapıları:** her blok/cevher belirli bir alet kademesi ister; "hepsi elmas kazmayla" yok (docs/05 §7). |
| D8 | **Loader: NeoForge** (kullanıcı kararı bana bıraktı; gerekçe: Curios yalnızca NeoForge'da, Vitrail/Sodium/FW/JEI NeoForge 26.3'te var). Kayıp: C2ME/VMP → Chunky ön üretim + ModernFix-mvus + Lithium. |
| D9 | **Beceri puanı:** her stat'ın yumuşak tavanı + azalan getirisi (39.992 puan enflasyonuna karşı); katsayılar Faz 10'da simülasyonla. Q4-Q11 varsayılanlar onaylı sayıldı (kullanıcı: "önerilerini istediğin gibi yap"). |
| D10 | **Sürüm 26.3 + NeoForge 26.3.0.101 + Java 25 (Temurin 25.0.5), Vulkan birincil / OpenGL yedek (→ **D19** ile yedek kalktı)** — Faz 0 kanıtlarıyla kesinleşti (ADR-001). 26.4 ayrı göç fazı. |
| D11 | **Faz 0 ADR'leri:** render kancaları NeoForge API'leriyle mixin'siz (ADR-002); zırh formülü `Reduction.ARMOR` modifier + AT ile attribute tavanı (ADR-003); büyü seviyesi vanilla ≤255 + kendi component'imiz (ADR-004); macOS girişi vanilla 26.3 + yedek Mac Input Fixes (ADR-005). |
| D12 | **macOS girişi (Q12, 10 Kas):** kullanıcı 26.3'te (`Vulkan Optimization`) Kontroller ▸ "Right click emulation" (`ctrlClickEmulatesRightClick`) kapalıyken macOS giriş sorunlarının düzeldiğini doğruladı; bu vanilla 26.3 özelliği → **mod yok**, paket varsayılanında ayar kapalı gelir (ADR-005 Karar 1 fiziksel olarak doğrulandı; Faz 5'te Theoria instance'ında bir kez tekrar). |
| D13 | **Büyü etkin seviyesi 255'te tavanlı (Q16):** "daha fazlasına ihtiyaç yok" — madenler + Durum ekranı + katalizörlerle oyun sonu gücü zaten tek atacak düzeyde (ADR-004 Karar 5). |
| D14 | **Kanatlar (Q13, Q17):** kanat **ölümden sonra da kalıcı**; ESC ▸ Kanatlar ekranından **doğrudan** takılıp çıkarılır — iksir/eşya gerekmez, edinme şartı yok. ESC menüsü vanilla gibi oyunu durdurur; Kanatlar ekranı durdurmaz (kullanıcı ikinci "Q16: Evet"i Q17 için yazdı sayıldı). |
| D15 | **Elytra (Q18):** ana uçuş aracı kanat modu; "zırhı gizle" elytra'yı gizlemez (varsayılan korundu, ayrıntı Faz 11a). |
| D16 | **HUD can paleti (Q23, 11 Kas):** renk **mutlak HP'ye** bağlı, kat sabit 20 HP (azami cana bağlı değil). Mümkün olduğunca çok, birbirine karışmayan renk (9): **kırmızıdan başlar, yeşil ve siyah yok** (zehir/solma renkleri), ortalarda güzel bir **turkuaz**, en son **beyaz ve kalıcı** — beyazın üstündeki her HP beyaz (200, 215, 235 HP hep beyaz; başa dönüp kırmızıdan başlamaz). Binek sağlığı aynı palet. |
| D17 | **HUD zırhı (Q24):** HP gibi — 5 sembol, çeyrek çeyrek, mutlak. Renkler **ışık spektrumu**: en güçsüz frekanstan (kırmızı) en güçlüye; **son renk mor** ve kalıcı. (Kat 0 vanilla çelik tutuldu.) 1 çeyrek = 1 zırh → T10+ takımlar (≥ 180) mor/tam: tek sayıyla (`armor.unit`) ayarlanır. |
| D18 | **Vanilla HUD efektleri korunur (Q26):** hasarda kalp yanıp sönmesi, yenilenme dalgası, düşük can titremesi, açlık titremesi eklendi (Faz 3). **Q25** (seviye yazısının barın içinde okunurluğu) kullanıcı isteğiyle **sona bırakıldı** — küçük düzeltmeler gerektiğinde bakılır. |
| D19 | **Grafik arka ucu yalnızca Vulkan (11 Kas, Faz 5 başında kullanıcı):** shader = **Vitrail Shaders** (zorunlu bağımlılığı yalnızca Sodium; Modrinth API) + Complementary Reimagined r5.9.3; **Iris kullanılmaz, OpenGL hedeflenmez** (geliştirme, ölçüm ve görsel DoD yalnızca Vulkan). D10'daki "OpenGL yedek", ADR-001'in OpenGL+Iris geri dönüş planı ve Faz 5'in "OpenGL = Iris" FPS satırı **geçersiz** (ADR-001 "Faz 5 değişikliği"). Vulkan regresyonunun çaresi OpenGL'e dönmek değil: sürüm sabitleme / Vitrail-MoltenVK güncellemesini bekleme (`docs/09` R1). |
| D20 | **Loader NeoForge'da kalır (2026-11-11, kullanıcı; Q41 kapandı):** "Vulkan Optimization" (Fabric) tabanına geçiş/gömme önerildi; Theoria NeoForge'a bağlı (71 dosyanın 24'ü, ADR-004 olayı, Curios) ve shader sorununun nedeni kanıtlanmadı → önce neden daraltılır (Q42). Yalnızca neden NeoForge/Vitrail-NeoForge'a özgü çıkarsa taşıma yeniden açılır. |
| D7 | **Vulkan çalışıyor** (makinede `Vulkan Optimization` instance'ı `preferredGraphicsBackend:"vulkan"`, render distance 32, Vitrail shader'larıyla çalışırken doğrulandı). Spike S1 yalnızca yeni projede tekrar doğrulama. **Shader desteği olacak:** Vitrail Shaders (Vulkan-yerli) + Complementary Reimagined. |

## B. Hâlâ açık (varsayılanlarla başlıyorum — itiraz edersen değişir)
| # | Soru | Varsayılanım |
|--:|---|---|
| Q1 | **Loader:** Senin Prism instance'ların Fabric. Ben **NeoForge** öneriyorum (Curios yalnızca NeoForge'da; Sophisticated, AE2, Mutant Monsters, End Remastered, Vitrail NeoForge 26.3'te var; LucidRem NeoForge). Kaybımız: **C2ME ve VMP yalnızca Fabric** (worldgen/ağ performansı). Fabric'e geçmek ister misin? | NeoForge |
| ~~Q2~~ | ~~Sürüm 26.3~~ → **D10** ile karar | — |
| Q3 | **9999 seviye = 39.992 beceri puanı.** Her stat yumuşak tavanlı/azalan getirili olsun mu (aksi halde 40K puan her şeyi kırar)? | Evet: her stat'ın yumuşak tavanı + azalan getiri; Faz 10'da sim ile ayarlanır |
| Q4 | **XP büyüsü** adı/türü: "Bilgelik" büyüsü (V seviye: +%100/+300/+600/+1000/**+1600**), her alete/zırha konabilir, bulmacası boss/zindan loot + katalizör; Netherite +1 seviye daha verir. Bu şekilde olur mu? | Evet |
| Q5 | Dayanıklılık stat'ı: Vitalite (HP) ve Dayanıklılık (düz zırh) ayrı | Ayrı |
| Q6 | Beceri puanı sıfırlama (respec) nadir bir item ile | Evet |
| Q7 | Kozmetik: gerçek zırh parçasını kozmetik slotuna koymak yeterli | Evet |
| Q8 | Çok oyunculu hedef yok (ama sunucu-otoriter yazılır) | Yok |
| Q9 | Mob/yapı modları 26.x'e gelene kadar kendi içeriğimizle ilerle (Mutant Monsters, End Remastered, Structory, Towns & Towers, Hopo yapıları, Incendium, Nullscape 26.3'te var → onları ekleyelim) | Evet |
| Q10 | `git init` (yerel; push yok, kendiliğinden commit yok) | Evet |
| Q11 | Lang: Türkçe + İngilizce | İkisi |

## B2. Faz 0'da çıkan yeni sorular (faz sonunda topluca)
| # | Soru | Varsayılanım |
|--:|---|---|
| ~~Q12~~ | → **D12** ile karar | — |
| ~~Q13~~ | → **D14** ile karar | — |
| Q14 | **Shader'lı FPS hedefi / varsayılan render distance** (Faz 5 ölçtü, `docs/journal/f05.md`): tam paket, Vulkan+Vitrail+Complementary, tam ekran 1080p, arazi üretilmiş → RD12 **83** FPS (min 74) · RD16 **58** (min 41) · RD24 **60** · RD32 **46**. Paket şimdilik oyunun ön ayarını (Fancy ≈ RD16) bırakıyor. Hangisi varsayılan olsun? | **RD12** (≥ 60 güvenli); RD16-24 sınırda, RD32 shader'ı kısmadan 60'ın altında. Tek satır: `pack/packwiz/options.txt renderDistance:` |
| Q15 | `git init` (Q10 onaylı) — Faz 1'in ilk işi olarak yapılsın mı, yoksa şimdi mi? | Faz 1 başında |
| ~~Q16~~ | → **D13** ile karar | — |
| ~~Q17~~ | → **D14** ile karar | — |
| ~~Q18~~ | → **D15** ile karar | — |

## B3. Faz 2a'da (denge simülasyonu) çıkanlar — varsayılanlar uygulandı, itiraz edersen tek sabit değişir
| # | Soru | Varsayılanım (uygulandı) |
|--:|---|---|
| Q19 | **Çevre hasarı** (düşme, lav, boğulma, açlık) sabit sayı; can 200+ olunca göreli olarak hafifliyor (214 canla 200 blok düşülür). Can yüzdesiyle ölçeklensin mi (iyileşme gibi)? | Hayır — v1'de vanilla sabit kalır (güç fantezisi; kanatla uçuşta düşmek cezalandırmaz). Faz 8'de yeniden bakılır |
| Q20 | **Mob dayanıklılığı:** eşit kademe mob ≈ 5-6 kılıç vuruşunda ölüyor (vanilla ilk gün hissi; eski hedef "6-10" idi). Daha süngerimsi olsun mu? | Hayır — 5-6 (`G_MOB_HP` 1,33). 1,35 yapılırsa 5-7 olur ama geç oyunda dövüş başına can kaybı %45 → %60 |
| Q21 | **Zırh genişletmesi** 98-112-124 → **98-118-142** (Mitril/Orikalkum/Platin göğüslük): son kademelerde yükseltme değeri ve ölüm vuruşu düşüyordu. Deri parçaları da en az 1 zırh veriyor (takım 2 → 4). | Evet (senin dizin 1…82 aynen duruyor) |
| Q22 | **Büyü koruması vanilla'nın yarısı** (Koruma IV × 4: %64 → %32; tavan %80 → %40). Vanilla ölçeğinde büyüsüz oyuncu geç oyunda oynanamaz, büyülü oyuncu ölümsüz olurdu. | Evet |

## B4. Faz 3'te (HUD) çıkanlar — Q23, Q24, Q26 kararlandı (D16-D18); Q25 sona bırakıldı
| # | Soru | Durum |
|--:|---|---|
| ~~Q23~~ | → **D16** ile karar | — |
| ~~Q24~~ | → **D17** ile karar | — |
| Q25 | **Seviye yazısı barın içinde** (5 px'lik vanilla çubuk, ölçek 0,6, ince siyah dış hat, beyaz): okunuyor mu? Okunmazsa LucidRem gibi çubuğu 7 px'e büyütmek gerekir (locator/zıplama çubuğuyla yuva paylaşımı yüzünden daha kapsamlı bir değişiklik). Locator/zıplama çubuğu açıkken seviye gösterilmiyor (vanilla gösterirdi). | **Sona bırakıldı** (kullanıcı, 11 Kas): küçük düzeltmeler gerektiğinde bakılır (Faz 13 cilası ya da önce) |
| ~~Q26~~ | → **D18** ile karar | — |
| Q27 | **Nötr sprite ton aralığı 150-255** (LucidRem'den): turuncu/çivit gibi tonlar vanilla kırmızıya göre biraz mat görünür (gövde tonu 202/255). İstenirse `generator/hud.py` `TONE_LOW`/gövde tonu yükseltilir (parlaklık ↔ gölge okunurluğu). | Görsel onayda bakılır |

## B5. Faz 4'te (Kanatlar) çıkanlar — varsayılanlar uygulandı, itiraz edersen tek satırlık değişiklik
| # | Soru | Varsayılanım (uygulandı) |
|--:|---|---|
| Q28 | **Kart önizlemesi:** kartta yalnızca ad + yeşil "giyili" işareti var; kart başına küçük resim yok (FW'nin şişe simgesi FW iç yapısına bağlı). Asıl önizleme canlı 3B oyuncu (anında değişir). Yeter mi? | Evet |
| Q29 | **Seçili karta tekrar tıklamak kanadı çıkarır** (ayrıca "Kanatları çıkar" düğmesi var). Sadece düğme mi olsun? | Tıklama da çıkarır |
| Q30 | **Boşta dönüş:** kullanıcı sürükleyip bıraktıktan 3 sn sonra tabla yeniden yavaş dönmeye başlar (LucidRem'de sürükleyince sonsuza dek dururdu). | 3 sn sonra devam |

## B6. Faz 5'te (Pack/QoL/macOS) çıkanlar — bilgi + tek karar
| # | Konu | Durum |
|--:|---|---|
| Q31 | **Kullanıcı fiziksel macOS kontrol listesi** (ADR-005 madde 5: Ctrl+sol tık, Ctrl+Q, Shift+tekerlek) Theoria instance'ında. Başarısız madde olursa Mac Input Fixes yedeği (pakette opsiyonel, kapalı) açılır. | Kullanıcıda (M1 DoD) |
| Q32 | **Distant Horizons 3.3.4 Vulkan'da kapanışta asılıyor** (3 non-daemon iş parçacığı) ve çizimi doğrulanamadı → pakette opsiyonel-kapalı. Açmak istersen ayrı doğrulama gerekir. | Kapalı kalıyor |
| Q33 | **Waystones 26.3.0.2 yapılandırma dosyasını her ≈2 sn'de yeniden yazıyor** (+ `.bak` döngüsü). Oyunu bozmuyor, diske sürekli yazıyor. Bilinen mod hatası olabilir; Waystones'u beklemeye alma/çıkarma kararı sende. | Pakette kalıyor, izleniyor |

## B7. Faz 6'da (Gear) çıkanlar — varsayılanlar uygulandı, itiraz edersen tek satırlık değişiklik
| # | Soru | Varsayılanım (uygulandı) |
|--:|---|---|
| Q34 | **Büyü gibi davranan öntanımlı özellikler** (docs/05 §1: Çelik Koruma I, Altın Yağma I, Elmas/Mitril Dayanıklılık II-III, Lonsdaleit Koruma/Keskinlik II, Orikalkum Tamir + element dirençleri, Platin +1 katalizör yuvası). Faz 6 yalnız nitelik tabanlı olanları yaptı (Deri +%2 hız, Mitril +%4 hız, Titanyum geri itme 0,2, kademeye göre büyü yeteneği, Altın 40). Nedeni: ref bütçesi (`ref_epf`, `docs/06 §3`) ve dayanıklılık eğrisi bunları zaten kapsıyor → varsayılan büyü olarak eklenseler bütçe iki kez sayılır, taşlama taşıyla silinir, her parçada parlama olurdu. | ✅ **Faz 7a'da yapıldı (ADR-006 Karar 9):** doğuştan sanal büyüler, silinemez, parlama yok, bütçeden sayılır; Bakır "ucuz onarım" karşılıksız bırakıldı (Q46) |
| Q35 | **Mob'un elindeki silah yükseldi:** vanilla silah nitelikleri D6 ile tabloya geçti, mob'lar da bunu alıyor (altın kılıç ×4,8, altın balta ×4,1, demir balta/kılıç ×1,9; `docs/06 §10`). T0'da bile piglin/brute/vindicator 2-5 kat vurur. | Faz 8'e devredildi (`sim.py --mobs` yeniden): mob el eşyası niteliği vanilla'ya sabitlensin mi, yoksa tabanlar mı düşsün? |
| Q36 | **Obsidyen/Antik Enkaz sertliği:** docs/05 §7 tablosundaki 9,0 formülün bu bloklara körlemesine uygulanmasıydı (vanilla 50/30). 9,0 ile elmas kazma obsidyeni 1 sn'den kısa sürede kırar. Kapı (alet idx 8) aynen duruyor, **sertlik vanilla kaldı**. | Vanilla (50/30). İstersen `tiers.py ORES` satırındaki `None` yerine çarpan yaz |
| Q37 | **Yay/arbalet/üçlü mızrak/gürz kademe almadı** (docs/05 §2 "yay/arbalet için kademe çarpanı (ayrı eşya değil, damage bonusu)"): bunların bir kademesi yok; vanilla hasarı T13'te (815 can) işe yaramaz. | ✅ **Faz 7a'da yapıldı (ADR-006 Karar 10):** tek dereceli katalizör "Menzil Gücü" (Q47) |
| Q38 | **Cevher yerleşimi yer tutucu** (`data/ores.csv`): hepsi `#minecraft:is_overworld`, kalay y 40-110, kobalt −60…−10 deepslate, titanyum >140, lonsdaleit/orikalkum çok seyrek (meteor kraterleri / yüzen adalar Faz 9'da), mitril −64…−40, platin −64…−48 (boss kasaları Faz 8-9). Damar boyutu < 3 hiç blok koymayabiliyor (kontrol eklendi). | Faz 9a "cevher dağılımı" yeniden ayarlar |
| Q39 | **Netherite şablon çoğaltma tarifi** duruyor: NeoForge vanilla tarifin kendi kopyasını (`#c:gems/diamond`) gönderiyor ve bizim `neoforge:never` geçersiz kılmamızı ezıyor. Tarifin tek girdisi şablonun kendisi; şablon her loot'tan süzüldüğü için tarif etkisiz (JEI'de görünür). | Etkisiz kalsın |
| Q40 | **Zırh `humanoid_baby` katmanı** vanilla demirin bebek dokusundan türetildi (oyuncu bebek değil ama zombi/piglin bebekleri giyebilir). Dokular vanilla demir şablonlarından boyanmış varyantlar: lisans notu Faz 13'e (HUD sprite'larıyla birlikte). | Böyle kalsın |

## B8. Prism testinden çıkanlar (2026-11-11 akşam) — karar bekliyor
| # | Soru | Önerim |
|---|---|---|
| Q41 ✅ **D20: NeoForge'da kal** | **Altyapı:** "Vulkan Optimization" (Fabric) paketini taban alıp Theoria'yı ona gömmek = **Theoria'yı Fabric'e taşımak** (D8/Q1'i yeniden açmak). Maliyet: 71 kaynak dosyasının 24'ü `net.neoforged` içe aktarıyor; Faz 2 hasar hattı (`LivingIncomingDamageEvent`), 5 attachment dosyası, ağ yükleri, ödül/loot değiştiricileri, yaratıcı sekme olayları, config; ADR-004'ün `GetEnchantmentLevelEvent` karşılığı Fabric'te yok (mixin gerekir); Curios (Faz 11) yalnızca NeoForge. Kazanç: C2ME/VMP/noisium + kullanıcının "sorunsuz" dediği yığın. **Sorunun nedeni henüz kanıtlanmadı** (Faz 6 journal ek bölümü) — nedeni bilmeden yük taşımak riskli. | **NeoForge'da kal;** Vulkan Optimization'ın ayarlarını/modlarını uyarla ve nedeni daralt (Q42); yük ancak neden NeoForge/Vitrail-NeoForge'a özgü çıkarsa |
| Q42 | Shader sorununu daraltmak için kullanıcı testleri (her biri ≈2 dk): (1) Seçenekler ▸ Erişilebilirlik ▸ **Bozulma Efektleri 0** (Vulkan Optimization'da 0) (2) Video ▸ **Chunk güncellemelerini önceliklendirme: Yok** (3) RD 12 (4) Vitrail ayarlarında **Render Scale 75** (5) shader kapalıyken de oluyor mu? Hangisi düzeltirse onu pakete yazarız. | Yapalım |

## B9. Faz 7a'da (Kataliz Masası tasarımı) çıkanlar — varsayılanlar `catalysts.py`'de, itiraz edersen tek sabit
| # | Soru | Varsayılanım |
|--:|---|---|
| Q43 | **Bilgelik:** toplam XP tavanı **+%2400** (mainhand + 4 zırh toplanır) ve kapsam **yalnız öldürme + blok kırma XP'si** (fırın, balık, ticaret, üreme, XP şişesi, taşlama taşı dışarıda: iade tarzı kaynaklar çarpanla sonsuz döngü kurar). Netherite ile VI = +%2000 (tek parça tavanı aşmaz, iki VI tavana vurur). Etki JSON'da değil Java'da (vanilla efektleri parçalar arasında çarpılır). | Böyle |
| Q44 | **Netherite maliyet/sınır:** eşya başına en çok **5 uygulama**, n. uygulama **1-2-4-6-9** külçe (toplam 22); büyü başına bonus tavanı tablosu (Koruma ailesi 1, Lure/Riptide/tek seviyeli 0, Servet/Yağma 2, Keskinlik/Güç/Verimlilik/Dayanıklılık 5, bilinmeyen mod büyüsü: max_level ≥ 2 ise +1). Netherite yalnız *gerçek* büyülere (katalizör/doğuştan sanal büyülere değil). | Böyle |
| Q45 | **Yuva/sökme:** aynı katalizör bir eşyada tek yuva (seviye I-V; yükseltmek = daha yüksek seviyeyle değiştirmek, eski olan yok olur); katalizör "Boşalt" ile iade olmadan çıkarılabilir; seviye kademe kapısı yok, seviyeyi **maliyet** (IV'ten itibaren boss çekirdeği) sınırlar. | Böyle |
| Q46 | **Doğuştan (öntanımlı) özellikler:** sanal (örs/taşlama görmez, parlama yok, tooltip'te mor "Doğuştan: Koruma I"); Bakır "ucuz onarım" karşılıksız kaldı; Orikalkum Tamir = Onarım II + Ateş/Patlama/Mermi Koruması I; Platin = Koruma III + element korumaları II + 1 yuva. Çelik Koruma I × 4 = EPF 4 = ref_epf(5) (bütçe iki kez sayılmaz). | Böyle |
| Q47 | **Yay/arbalet/üçlü mızrak/gürz (Q37):** yeni kademeli eşya YOK; tek **dereceli** katalizör "Menzil Gücü" (tarifte seçilen külçe kademesi katalizörün derecesi olur, hasar = `0,60 × eğri × kılıç hasarı(derece)`); bu eşyalar 3 yuvalı. Element hasar fraksiyonları (Buz %10 … Işık %30) **taslak**; savaş denemesiyle ayarlanır. | Böyle |

## B10. Faz 7b'de (Kataliz Masası uygulaması) çıkanlar — varsayılanlar uygulandı, itiraz edersen tek satırlık değişiklik
| # | Soru | Varsayılanım |
|--:|---|---|
| Q48 | **Katalizör üretimi Kataliz Ocağı'nda** (ADR-006 Karar 11 değişti): III-V seviye 15-39 malzeme ister, 3×3 şekilsiz tarif ≤ 9 malzeme alır. Ocakta hedef yuvası boşken yığın sayıları okunur. **Bedel:** katalizör maliyeti JEI'da görünmez (özel JEI kategorisi derleme bağımlılığı → yapılmaz); keşif için katalizör ipucundaki "Üretim maliyeti" satırı ve ocak önizlemesi. | Böyle. Alternatif: seviye zinciri (her seviye bir öncekinden ≤ 9 malzemeyle) — maliyet merdiveni değişir. |
| Q49 | **Atıl doğuştan satırlar:** Bronz, Çelik ve Titanyum **zırh-only** kademeler (`tools.csv`'de yok) → Çelik silah Keskinlik I, Bronz alet/silah Dayanıklılık I uygulanamıyor (zırh satırları çalışıyor). | Böyle (zırh tarafı bütçeyi taşıyor). İstersen çelik/bronz alet kademesi eklenir (Faz 6 kapsamı). |
| Q50 | **Element etkileri taslak ve Java'da:** Solma "iyileşmeyi keser" yapılmadı; koşullu Işık/Gölge/Fırtına T8 kılıçta vuruş başına 9,78 (kılıç hasarının %30'u; sayımda ×0,4); Zincir/AoE/Gravite sayıları tablo. | Savaş denemesinde ayarlanır (tek sabit: `catalysts.py`). |
| Q51 | **Ev sahibi = yalnız kademeli eşyalar** (Theoria + vanilla gear tabloları + yay/arbalet/üçlü mızrak/gürz). Başka modların zırhı/aleti katalizör alamaz (kademe eşlemesi yok); Curios/Relics Faz 11-12. | Böyle; dış mod kademeleri Faz 13'te. |
| Q52 | **Hammadde kaynakları geçici:** toz %35 × 1-2 (cevher bloğu, ipek dokunuşu hariç) · öz %12 (20 düşman) · çekirdek: 4 boss %100 (1-2), 6 sandık %25. | Faz 8/9 yeniden düzenler. |
| Q53 | **Ocak ekranı 356 px geniş** (iki sütun): GUI ölçeği 1-3'te 1280×720'de sığar; 720p'de ölçek 4'te taşar (vanilla envanter + tarif defteri de dar pencerede taşar). | Böyle; sorun olursa tek sütuna düşen yerleşim. |
| Q54 | **Görünüş onayı (kullanıcı):** ocak ekranı, katalizör ikonları (vanilla elmas/ametist/zümrüt/lapis şablonlarının renkli varyantları), ocak bloğu dokusu (karartılmış mor taş) ve tooltip metinleri yer tutucu sanat. | Kullanıcı Faz 7 DoD'sinde bakar. |

## C. Öneriler (sormadan eklediklerim — hoşuna gitmezse çıkarırız)
- **Kapı boss'ları** (8 adet): her 2 kademede bir "geçit" boss'u.
- **Haven'larda** yatak/ocak/depo hazır — "benim yerim" için çalışan ev başlangıcı.
- **Fırtına katalizörü**: uçarken bonus (Kanat sinerjisi).
- **`/theoria test`** oyun-içi doğrulama paketi.
- **Denge simülatörü** (`tools/balance/sim.py`).
- Kendi mob/yapı içeriğimiz oturana dek **26.3'te hazır olan** yapı ve mob modlarını geçici doldurucu olarak kullanmak (docs/12).
