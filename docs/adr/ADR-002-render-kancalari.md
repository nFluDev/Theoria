# ADR-002 — Render kancaları (HUD, 3B oyuncu, zırh katmanı)

**Durum:** Kabul · 2026-11-09 (Faz 0, spike S3 + S2) · **Etkiler:** Faz 3 (HUD), Faz 4 (WingsScreen), Faz 10 (Durum ekranı), Faz 11 (kozmetik/zırh gizleme)
**Kapanış:** 2026-11-10, Fable 5.2 xHigh gözden geçirdi — kancalar aynı; **düzeltildi:** (b) sürükle-döndür için `renderEntityInInventoryFollowsAngle` uygun değil (baş gövdeye göre ters döner) → `GuiGraphicsExtractor#entity` + kendi render state'imiz; (a) katman adları 26.3'e göre düzeltildi (`experience_*` yok) ve `Hud.leftHeight/rightHeight` yükümlülüğü eklendi; (c) `chestEquipment`'ın elytra ve pelerini de beslediği yazıldı; (d) `/wings` izin düzeyi, sunucu-kaynaklı çalıştırma ve geri dönüş planı somutlaştı; Vitrail uyumu "kanıt" değil "çıkarım" olarak yeniden etiketlendi.

Kanıt etiketleri: **[log]** komut/log çıktısı · **[kaynak]** decompile edilmiş 26.3.0.101 / FW 26.3.1 sınıflarından okundu, çalıştırılmadı · **[çıkarım]** denenmedi.

## Bağlam
- Kural (CLAUDE.md): yalnızca Mojang GUI/render-pipeline soyutlaması; doğrudan GL yok, özel shader/pipeline yok; içerik vanilla render tipleriyle çizilir ki Vitrail shader paketleri işlesin. Arka uç Vulkan (D7) ama OpenGL'de de aynı görünmeli.
- 26.x render modeli 1.21'den farklı ("extract → submit"): `GuiGraphics` → `GuiGraphicsExtractor`, `Screen#render` → `extractRenderState`, varlık render'ı `EntityRenderState` + `SubmitNodeCollector` (R2).
- LucidRem dersleri: pause'u bölen ekran sunucuyu durdurur; envanter-varlık çizimi ham fare koordinatı ister; kırmızı sprite boyanmaz (nötr sprite + renk çarpımı).

## Karar
**(a) HUD — NeoForge GUI katmanları**
- Kendi barlarımız: `RegisterGuiLayersEvent#registerAbove(VanillaGuiLayers.PLAYER_HEALTH, theoria:status_bars, GuiLayer)`; `GuiLayer#render(GuiGraphicsExtractor, DeltaTracker)`. [log]
- Vanilla katmanları gizleme: `RenderGuiLayerEvent.Pre` → `setCanceled(true)`: `player_health`, `armor_level`, `food_level` [log]; gerekirse `vehicle_health`, `air_level` [kaynak]. Alternatif `replaceLayer`/`wrapLayer` — tercih edilen iptal (başka modlar vanilla katmanı sarmalıyorsa kırılmaz).
- **XP barı:** 26.3'te `experience_bar` katmanı yok; XP barı `contextual_info_bar_background` + `contextual_info_bar` içindedir ve bu yuvayı locator bar ile binek zıplama barı paylaşır (`Hud.ContextualInfo`: `EXPERIENCE / LOCATOR / JUMPABLE_VEHICLE`); seviye sayısı ayrı katman: `experience_level` [kaynak]. Contextual katmanı iptal etmek locator ve zıplama barını da üstlenmek demektir → öneri: o katman kalır, yalnızca `experience_level` yerine kendi seviye metnimiz; son karar Faz 3.
- **Yükseklik sayaçları:** vanilla katmanlar `mc.gui.hud.leftHeight` / `rightHeight` (her kare 39'dan başlar) sayaçlarını ilerletir; iptal edilen katman ilerletmez [kaynak]. Katmanımız çizdiği satırlar kadar ikisini de ilerletir; yoksa hava kabarcıkları, binek kalpleri, eylem çubuğu mesajı / seçili eşya adı ve başka modların katmanları barlarımızın üstüne biner. (Katmanımız `player_health`'in hemen ardından çalıştığı için sonrakiler güncel değeri görür.)
- **Görünürlük:** vanilla katmanların koşulu (`survivalVisible` = `mc.gameMode.canHurtPlayer() && !mc.gui.hud.isHidden()`) katmanın *içindedir*; olayla eklenen mod katmanı koşulsuz çalışır [kaynak; spike katmanı yaratıcı modda da her kare çağrıldı — log]. Katmanımız aynı koşulu kendisi uygular (F1'de ve yaratıcı/seyirci modda çizmez).
- Çizim API'si: `blitSprite(RenderPipelines.GUI_TEXTURED, Identifier sprite, x, y, w, h, int argb)` (renk = çarpım → üst katlar için beyaz/nötr sprite üretilir, `generator/hud.py`), metin `text(Font, …, color, shadow)`. Sprite'lar normal resource-pack atlası (`assets/theoria/textures/gui/sprites/hud/...`). [log]

**(b) 3B oyuncu (Kanatlar/Durum ekranları)**
- `Screen` alt sınıfı, **`isPauseScreen() == false`**, çizim `extractRenderState(GuiGraphicsExtractor, mouseX, mouseY, a)` içinde. [log]
- Fareyi izleyen görünüm: `InventoryScreen.extractEntityInInventoryFollowsMouse(g, x0, y0, x1, y1, size, offsetY, mouseX, mouseY, entity)` — **ham** fare koordinatı. [log]
- **Sürükle-döndür ve "varsayılan arkadan" görünüm:** NeoForge'un `renderEntityInInventoryFollowsAngle`'ı **kullanılmaz** — gövdeyi `180 + 20·xAngle`, başı gövdeye göre `20·xAngle` çevirir; yalnızca ±~30° içindir, gövde 180° döndüğünde baş tam ters bakar [kaynak]. Bunun yerine o metodun gövdesini kendimiz kurarız: `mc.getEntityRenderDispatcher().getRenderer(e).createRenderState(e, 1f)` → `shadowPieces.clear()`, `outlineColor = 0`, `bodyRot = 180 + açı`, `yRot = 0`, `xRot = 0`, ölçek normalizasyonu → `GuiGraphicsExtractor#entity(state, size, translation, rotation, overrideCameraAngle, x0, y0, x1, y1)` (vanilla, public). Açıyı kendimiz biriktiririz. [kaynak — çalıştırılmadı]
- `createRenderState` NeoForge render-state modifier'larını da çalıştırır → (c)'deki gizleme/kozmetik önizlemede aynen görünür. [kaynak]
- **Pause semantiği:** bu ekranlar açıkken oyun akar (60 istemci tick'inde oyuncu +60, sunucu +60 [log]); FW animatörü oyuncu tick'ine bağlıdır (`PlayerTickEvents.End` [kaynak]) → o da akar. Vanilla ESC menüsü tek oyunculuda oyunu durdurmaya **devam eder**; FW animasyonu orada donar [log] — değiştirmiyoruz. Faz 4 DoD'sindeki "pause ekranında animasyon akıyor" = ESC menüsünden açılan **Kanatlar ekranında** (`docs/10` D14 — kullanıcı onayı).
- ESC menüsüne "Kanatlar" butonu: `ScreenEvent.Init.Post#addListener`. [kaynak — S3'te denenmedi]

**(c) Zırh katmanı gizleme / kozmetik**
- `RegisterRenderStateModifiersEvent#registerAvatarEntityModifier(new AvatarRenderStateModifier(){ accept(avatar, AvatarRenderState state) })` → `state.headEquipment/chestEquipment/legsEquipment/feetEquipment` alanlarını `ItemStack.EMPTY` (gizle) veya kozmetik slottaki eşya (görünüm değiştir) yap; gerçek envanter/stat'lar değişmez. [log: göğüs slotu, yerel oyuncu · çıkarım: diğer üç slot ve uzak oyuncular — aynı kod yolu]
- `chestEquipment` üç katmanı besler: `HumanoidArmorLayer`, `WingsLayer` (elytra) ve `CapeLayer` (elytra/göğüslük varken pelerini saklama) [kaynak] → alanı körlemesine boşaltmak elytra'yı da gizler, pelerini geri getirir. Kural: elytra görünür kalır (`docs/10` D15); ayrıntı Faz 11a.
- Çok oyunculu görünürlük (Faz 11): ayar senkronize attachment'tan okunur; modifier tüm `Avatar`'lara (yerel + uzak oyuncular) uygulanır.

**(d) Kanatlar (S2 sonucu, Faz 4 için)**
- Katalog: FW 26.3.1'in datapack kayıt defteri `fantasticwings:wings` → `registryAccess().lookup(ResourceKey.createRegistryKey("fantasticwings:wings"))`, istemci ve sunucuda aynı 11 kayıt [log]. FW'ye derleme bağımlılığı yok.
- Takma/çıkarma komut düzeyinde: `/wings give <oyuncular> <id>` [log], `/wings take [<oyuncular> [<id>]]` [kaynak: komut ağacında var, çalıştırılmadı].
- `/wings` `LEVEL_GAMEMASTERS` izni ister [kaynak] → hayatta kalma oyuncusu kendisi çalıştıramaz (spike op'lu yaratıcı oyuncuyla denedi). Komutu sunucudaki payload işleyicimiz **sunucu kaynağıyla** çalıştırır: `server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withEntity(player).withSuppressedOutput(), "wings give @s <id>")`; sonuç `withCallback` ile okunur. İstemciden gelen `<id>` önce sunucudaki kayıt defterinde doğrulanır; doğrulanmamış metin komuta girmez. [kaynak]
- FW kanadı ölümde siler [log] → seçim kendi attachment'ımızda (`copyOnDeath`), `PlayerEvent.PlayerRespawnEvent`'te yeniden uygulanır. **Ürün kuralı (`docs/10` D14):** kanat ölümden sonra kalıcıdır ve ESC ▸ Kanatlar ekranından doğrudan takılıp çıkarılır — FW'nin iksir/eşya edinme yolu gerekmez, ekran bütün kataloğu şartsız sunar.

## Kanıt
**[log]** `./gradlew runSpikeClient` (otomatik sabit-seed dünya, senaryo, kendiliğinden kapanma) — iki arka uçta, 0 ERROR/istisna. Üç çalıştırmanın satırları: `docs/adr/kanit/s2_s3_s7_runs.log`. Aşağıdaki sayılar 2. (Vulkan) çalıştırmadan; kare sayıları çalıştırmaya göre değişir (OpenGL: hud 1392 · zırh 2132 · ekran 292).
```
Using graphics backend OpenGL, using drivers: 4.1 ATI-7.1.8            (1. çalıştırma)
Using graphics backend Vulkan, using drivers: 1.2.350 MoltenVK 1.4.2   (2. çalıştırma)
S3a/c final: hud calls=1443, cancelled={minecraft:armor_level=1443, minecraft:food_level=1443, minecraft:player_health=1443}, armor hides=2164
S3c armor render-state hides=340 (item=minecraft:diamond_chestplate), real chest slot=minecraft:diamond_chestplate
S3b non-pause screen: extract calls=296, isPaused=false, client player ticks +60, server ticks +60 (over 60 client ticks)
S3b vanilla PauseScreen (screen=true): isPaused=true, client player ticks +0, server ticks +1 (over 60 client ticks)
S2 catalog (client registry): 11 [fantasticwings:angel, fantasticwings:bat, ...]
chat> Applied wings to Dev
chat> Dev has the following entity data: {"fantasticwings:flight": {"fantasticwings:flight": {wing_type: "fantasticwings:angel"}}}
(after /kill + respawn) {"fantasticwings:flight": {"fantasticwings:flight": {}}}
```
Kod: spike istemci senaryosu Faz 1'de koddan çıkarıldı; kaynağı referans olarak `docs/adr/kanit/SpikeClient.java.txt`'te (Faz 3/4/11 için 26.3 API kullanım örneği).
**[kaynak]** `InventoryScreen` (iki statik metot), `GuiGraphicsExtractor#entity`, `EntityRenderer#createRenderState` (sonunda `RenderStateExtensions.onUpdateEntityRenderState`), `HumanoidModel` (`head.yRot = state.yRot`), `Hud` (`leftHeight/rightHeight`, `ContextualInfo`), `VanillaGuiLayers`, `GuiLayerManager`, `WingsLayer`/`CapeLayer`, `Commands#performPrefixedCommand`, `CommandSourceStack#withEntity/withSuppressedOutput/withCallback`; FW `WingsCommand` sınıfının sabit havuzu (`give`, `take`, `LEVEL_GAMEMASTERS`).

## Sonuçlar
- ✅ Üç kanca da mixin'siz, NeoForge'un genel API'leriyle; Vulkan ve OpenGL'de aynı kod yolu, 0 hata. [log]
- ⚠ **Vitrail uyumu kanıtlanmadı** [çıkarım]: S1 instance'ında spike `fps` modundaydı; HUD katmanı da önizleme ekranı da Vitrail altında hiç çalışmadı. Beklenti: GUI ayrı pipeline'da çizilir, oyuncu/zırh vanilla `EntityRenderState` yolundan gider, vanilla envanter önizlemesi kullanıcının Vitrail'li instance'ında zaten çalışıyor. Kanıt = kullanıcı onayı (aşağıda).
- ⚠ Görsel doğruluk (HUD hizası, zırhın gerçekten görünmemesi, kanat görünümü) log ile değil **kullanıcı onayıyla** kapanır (ekran görüntüsü yasak).
- ⚠ `RenderGuiLayerEvent` her katman için her karede çalışır; iptal dinleyicisi birkaç `Identifier` karşılaştırmasıyla sınırlı kalmalı (maliyeti ölçülmedi; ihmal edilebilir beklenir).
- ⚠ 26.x'te `mc.gui.setScreen(...)` / `mc.gui.screen()` (eski `mc.setScreen`) — tüm ekran kodu buna göre. [log]

## Sonraki fazlara yükümlülükler
- **Faz 3:** ✅ `leftHeight/rightHeight`, görünürlük koşulu, XP/contextual bar kararı, hava kabarcığı yeri — aşağıdaki "Faz 3 sonucu". **Açık:** DoD'deki kullanıcı onayı iki arka uçta + **Vitrail açıkken bir duman kontrolü** (M1'i beklemeden; Faz 3'te Vitrail'li örnek çalıştırılmadı).
- **Faz 4:** ✅ döndürme `GuiGraphicsExtractor#entity` ile; ESC butonu; sunucu-kaynaklı komut + id doğrulama; `take`; öl → yeniden doğ; `/theoria test wings` — aşağıdaki "Faz 4 sonucu". **Açık:** kanatların 3B önizlemede ve oyunda *görünüşü* kullanıcı onayı (iki arka uçta); Vitrail açıkken Kanatlar ekranı (Faz 5 / M1).
- **Faz 5 (M1):** Vulkan + Vitrail açıkken HUD ve Kanatlar ekranı kullanıcı onayı (DoD'de var).
- **Faz 10:** Durum ekranı Faz 4'ün önizleme bileşenini yeniden kullanır.
- **Faz 11a:** dört slot + uzak oyuncular; elytra/pelerin kuralı; başka modların zırh katmanları.

## Faz 3 sonucu (2026-11-11, HUD)
Kanıt etiketleri yukarıdaki gibi. Ayrıntı: `docs/journal/f03.md`, tasarım: `docs/04 §1`.
1. **Kanca aynen ADR'deki gibi çalıştı** [log]: `registerAbove(PLAYER_HEALTH, theoria:status_bars, …)` + `Pre` iptali (`player_health`, `armor_level`, `food_level`; survival HUD görünürken `vehicle_health`); hava kabarcığı katmanı vanilla'da kaldı. Gözetimsiz senaryo (`-PdevClient=hud`, Vulkan **ve** OpenGL, 16 adım): her adımda katman kare başına çizdi, vanilla katmanlar kare başına iptal (≥ 3 iptal/çizim), 0 istisna; yaratıcıda 0 çizim.
2. **`replaceLayer` vanilla görünürlük koşulunu da götürür** [kaynak: `GuiLayerManager.add(name, layer, shouldRender)` koşulu katmanın içine sarar; `replaceLayer` o sarmalı değiştirir]: `experience_level` katmanını değiştirirken `hasExperience`/`hud.isHidden()` koşulunu kendimiz uygularız. Yeni katman için de aynısı geçerli (ADR a) — ikisinde de `Hud.isHidden()` + `gameMode.canHurtPlayer()/hasExperience()` elle.
3. **`leftHeight/rightHeight`** [log]: katmanımız iki satır için ikisini 59'a (`bottom 39 + 2 × 10`), emilim satırı varken solu 69'a çıkarır; yaratıcıda dokunmaz (39 kalır). Sonraki vanilla katmanlar (hava kabarcığı satır C'nin hizasında, seçili eşya adı, eylem çubuğu mesajı) bu sayaçlardan yerleşir.
4. **Kısmi `blitSprite`** [kaynak + log]: `blitSprite(pipeline, id, spriteW, spriteH, u, v, x, y, w, h, argb)` — vanilla'nın XP barı da aynısını kullanır. Çeyrek/yarım dolgu ayrı sprite değil çalışma anı kırpmasıdır; yarım sprite'lar hiç kullanılmaz (`armor_half`'ın opak boş yarısı katmanlı çizimde altı siyahla örter).
5. **AT: `Hud.contextualInfoBar`** (`public`) [log: derlendi, çalıştı]: XP çubuğu yuvayı locator/binek zıplama çubuğuyla paylaştığı için seviye yazısı yalnızca `contextualInfoBar.getSecond() instanceof ExperienceBar` iken çizilir; eğerli at binildiğinde yazı 0 kare çizildi, inince geri geldi (senaryo `jump_bar_owns`). `Hud.ContextualInfo` özel enum'dur — alanın yalnızca `getSecond()`'u kullanılır, tip adı yazılmaz.
6. **Vanilla zehir/solma/donma/hardcore sprite'ları** kat 0 için doğrudan kullanılır (`hud/heart/{poisoned,withered,frozen}_{,hardcore_}full`); `Hud.HeartType.forPlayer` özel olduğu için koşul (`POISON` → `WITHER` → `isFullyFrozen`) kopyalandı.
7. **Vanilla efektleri korundu** (D18, kullanıcı isteği) [log: gözetimsiz senaryoda kare sayaçlarıyla]: hasar yanıp sönmesi (`*_blinking` sprite'ları + `HealthTracker`), yenilenme dalgası, düşük can titremesi, açlık titremesi — hepsi `Hud.java`'daki mantığın aynası; kararlar saf fonksiyon ve Python'la eşit. `RenderGuiLayerEvent.Pre` maliyeti ölçülmedi (kare başına ~15 katman × `Set.contains`; ihmal edilebilir beklenir).
8. **Hâlâ [çıkarım]:** Vitrail altında HUD (Faz 3'te çalıştırılmadı; Faz 5/M1 ve kullanıcının duman kontrolü).

## Faz 4 sonucu (2026-11-11, Kanatlar)
Kanıt etiketleri yukarıdaki gibi. Ayrıntı: `docs/journal/f04.md`, tasarım: `docs/04 §2`.
1. **ESC düğmesi** `ScreenEvent.Init.Post#addListener` ile eklendi [log: gözetimsiz senaryo, Vulkan **ve** OpenGL]: her yeniden `init`'te (GUI ölçeği değişimi, ekrana dönüş) tam bir kez bulunur, ekranın içinde, vanilla sütununun altında, hiçbir sütun düğmesine binmeden. Sütun ölçümü yalnızca ortadaki `AbstractButton`'lar (başlık `StringWidget`'ı sayılmaz). Ekran 427×240 GUI'de düğme y=192, sütun y=184'te biter.
2. **`GuiGraphicsExtractor#entity` kendi açımızla** [log]: `createRenderState` → `bodyRot = açı`, `yRot = xRot = 0` → `entity(...)`; senaryoda 174 kare = 174 önizleme çağrısı, 20 px sürükleme tam −32° döndürdü. Hâlâ [çıkarım]: kanatların önizlemede gerçekten *göründüğü* (render-state uzantısı yolu) — kullanıcı onayı.
3. **26.3 fare kodları:** `InputConstants.MOUSE_BUTTON_LEFT == 1` (GLFW'nin 0'ı değil); `AbstractWidget#isValidClickButton` ve `MouseButtonEvent#button()` bu sabitlerle çalışır. `event.button() == 0` yazmak sessizce hiçbir tıklamayı yakalamaz — gözetimsiz senaryonun ilk koşusu tam bunu yakaladı (düğme tıklaması ekranı açmadı). Kodda hep sabiti kullan.
4. **Pause semantiği, gerçek ekranla** [log]: ESC menüsü açıkken `isPaused()=true`, oyuncu **+0** tick/20; Kanatlar ekranında `isPaused()=false`, oyuncu **+30**, sunucu **+30** tick/30 istemci tick'i. İlk ölçümde sunucu yalnızca +17 verdi: ESC menüsünden çıkınca entegre sunucu duraklamış uykudan ≈ yarım saniyede uyanıyor — kalıcı yavaşlık değil (25 tick ısınma sonrası 30/30).
5. **`/wings` sunucu kaynağıyla** [log]: `server.createCommandSourceStack().withEntity(player).withSuppressedOutput().withCallback(…)` + `performPrefixedCommand` hayatta kalma oyuncusu için `give` ve `take` çalıştırdı; aynı oyuncunun *kendi* kaynağıyla `/wings give` hiçbir şey yapmadı (komut düğümü izinle gizli) → ADR'nin `LEVEL_GAMEMASTERS` varsayımı [kaynak]'tan [log]'a döndü. `take`, kanat yokken "Target doesn't have wings to remove" ile başarısız olur; hedef durum (kanat yok) zaten sağlandığı için bunu başarı sayarız.
6. **Id doğrulaması** [log]: 9 kötü kimlik (`…; op @s`, satır sonlu, boşluklu, 200 karakter, büyük harf, `../`, 3 parçalı, kayıtta olmayan `fantasticwings:nope`, `minecraft:stone`) → hepsi `UNKNOWN_WING`, durum değişmedi, oyuncu operatör olmadı. Payload codec'i kimliği 64 karakterle keser; `Identifier.tryParse("fantasticwings:")` boş yolu geçirdiği için `WingsCatalog.parse` boş yolu ayrıca reddeder.
7. **Ölüm ve kalıcılık** [log, başsız GameTest + gözetimsiz istemci]: FW kanadı ölümde siler; `theoria:wings` (`copyOnDeath`) kalır; `PlayerRespawnEvent` işleyicisi tam bir kez yeniden takar (sayaçla kanıtlı), istemcide senkron attachment değişmeden döner. End-tipi yeniden doğuş (tüm veri korunur) yeniden takmaz, FW durumu kalır. Kanadı çıkarıp ölen oyuncuda kanat belirmez. Kayıttan kalkmış kanat kimliği ölümde çökmez. `PlayerList#respawn` sahte bağlantıyla da `PlayerRespawnEvent`'i tetikliyor (sonraki fazların başsız testlerinde kullanılabilir).
8. **FW durumunu okumak** [log]: `data get entity @s neoforge:attachments` → `wing_type: "fantasticwings:…"`; hem GameTest hem istemci senaryosu FW'nin *kendi* durumunu bununla doğruladı (kendi attachment'ımıza güvenmek yetmez).
9. **Yapılmadı:** kart başına küçük resim — FW'nin şişe simgesi FW'nin özel item özelliğine/`GrantWingsConsumeEffect`'e bağlı, bunlara dokunmak ADR (d)'nin "FW iç yapısına bağlanma" sınırını aşar; asıl önizleme canlı 3B oyuncu. **Hâlâ [çıkarım]:** Vitrail altında Kanatlar ekranı; dedicated server'da payload/respawn yolu (kod aynı, çalıştırılmadı).

## Geri dönüş planı
- (a) Katman API'si değişirse: `RegisterGuiLayersEvent#replaceLayer` ile vanilla katmanın yerine geçmek; o da yoksa `Hud` üzerine mixin (son çare).
- (b) `GuiGraphicsExtractor#entity` imzası değişirse: `extractEntityInInventoryFollowsMouse`'a sentetik fare koordinatı (yalnızca ±~30°; arkadan görünüm kaybolur — kabul edilebilir düşüş).
- (c) Render-state modifier yetmezse (ör. başka mod zırhı kendi katmanında çizer): `RenderLivingEvent.Pre` / `EntityRenderersEvent.AddLayers` ile katman listesi düzenleme (ikisi de 26.3'te mevcut [kaynak]); o da yetmezse "yalnızca kendi oyuncumuzda gizle" kısıtlı modu (R9 fallback).
- (d) FW sürümü pack'te sabittir; güncellemeden önce `/theoria test wings` çalıştırılır. Komut ya da kayıt defteri yoksa Kanatlar ekranı "kullanılamıyor" durumuna düşer (çökme yok) ve FW eski sürümde kalır. JSON düzeyi alternatif: FW'nin kanat veren tüketim etkisi (`GrantWingsConsumeEffect` [kaynak: sınıf adı; şeması incelenmedi]).
