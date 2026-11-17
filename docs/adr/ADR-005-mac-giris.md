# ADR-005 — macOS giriş sorunları (Ctrl+tık, Ctrl+Q)

**Durum:** Kabul · 2026-11-09 (Faz 0, spike S7) · **Etkiler:** Faz 5 (pack + QoL + macOS, M1 DoD) · **Fiziksel doğrulama:** kullanıcı onayı 2026-11-10 (`docs/10` D12)
**Kapanış:** 2026-11-10, Fable 5.2 xHigh gözden geçirdi — karar ve yedek sırası aynı; **netleştirildi:** temel kararın kanıtı log değil kaynak okumasıdır (fiziksel girdiyle hiç denenmedi); fiziksel test Faz 5'i beklemeden kullanıcının mevcut 26.3 instance'ında yapılabilir (aynı vanilla giriş kodu); kontrol listesinin geçme ölçütleri yazıldı; Yedek 2'nin D1 için sınırı ve son çare eklendi.

Kanıt etiketleri: **[log]** komut/log çıktısı · **[kaynak]** decompile edilmiş 26.3.0.101 kaynağından okundu, çalıştırılmadı · **[çıkarım]** denenmedi.

## Bağlam
- `docs/01` D1: Ctrl+sol tık sağ tık gibi davranıyor (sprint-vuruş, envanter). D2: Ctrl+Q (yığını bırak) çalışmıyor.
- Adaylar: **Mac Input Fixes (NeoForged) 26.10.03-p** (26.3 build'i var), **McMouser** (yalnızca 26.1/26.2 build'i), kendi `macos` paketimiz (`docs/04 §8`).
- 26.3'te Minecraft girişi **SDL3** üzerindedir (GLFW değil); eski hata raporları (MC-22882, MC-59810…) GLFW dönemine ait. Eski sürümlerde Ctrl+tık→sağ tık çevirisi `MouseHandler`'da sabit kodluydu; 26.3'te o kod yok, yerine bir SDL ipucu + ayar var [kaynak].
- Kullanıcının günlük instance'ı `Vulkan Optimization` da **26.3**'tür (Fabric; giriş düzeltme modu yok) → vanilla 26.3 giriş davranışı orada bugün gözlenebilir.

## Karar
1. **Temel paket vanilla 26.3 davranışına dayanır**, ek mod yok [kaynak — fiziksel girdiyle doğrulanmadı]:
   - `ctrlClickEmulatesRightClick:false` (vanilla varsayılanı; paketin varsayılan `options.txt`'sine açıkça yazılır) → SDL ipucu `SDL_MAC_CTRL_CLICK_EMULATE_RIGHT_CLICK=0` → Ctrl+sol tık = sol tık + Ctrl (D1).
   - Yığını bırak: oyun içinde fiziksel Ctrl tarama kodu, envanterde SDL Ctrl maskesi → Ctrl+Q çalışır (D2). `quitShortcuts:false` yazılır (Cmd+Q oyunu kapatmasın; kullanıcının mevcut ayarı da böyle).
2. **Yedek 1 — Mac Input Fixes 26.10.03-p**: 26.3 NeoForge'da temiz yüklendiği kanıtlı [log] (sorunu *çözdüğü* değil — o da ancak fiziksel testle görülür); kontrol listesinden biri başarısız olursa pakete eklenir (sürüm sabit, `pack/modlist.toml` `[mac-yedek]`).
3. **Yedek 2 — kendi `dev.theoria.macos` paketi** (Mac Input Fixes de yetmezse), ayar ekranında aç/kapa (`docs/04 §8`):
   - D2 için yeterli: `AbstractContainerScreen#keyPressed(KeyEvent)` ve `Minecraft`'taki `keyDrop` dalına küçük mixin (Ctrl **veya** Cmd kabul).
   - D1 için **sınırlı**: çeviri SDL'in içinde, `MouseHandler#onButton`'dan önce olur; mixin "Ctrl basılı + sağ tık" görür ve bunu gerçek bir Ctrl+sağ tıktan ayırt edemez → yalnızca sezgisel düzeltme. D1'de Yedek 1 de başarısızsa son çare: paket varsayılanında koşma tuşunu Ctrl dışına almak (kullanıcının mevcut ayarı zaten `key.sprint = left.shift`) ve sınırı belgelemek.
4. **McMouser reddedildi** (26.3'te yüklenmiyor [log]).
5. **Doğrulama kontrol listesi (kullanıcı, ~1 dk; ilk onay D12)** — vanilla giriş kodu Fabric/NeoForge'da aynı olduğu için Faz 5'i beklemeden `Vulkan Optimization` instance'ında yapılabilir; Faz 5'te Theoria paketinde bir kez daha tekrarlanır:
   - (i) Ctrl basılıyken sol tık: dünyada **vurur/kırar** (blok yerleştirmez, eşya kullanmaz); envanterde eşyayı **alır** (yarım yığın ayırmaz).
   - (ii) Ctrl+Q: elde ve envanterde (imleç bir yuvanın üstündeyken) **tüm yığını** bırakır; Q tek başına bir adet bırakır.
   - (iii) Shift basılıyken tekerlek: hotbar'da yuva **değiştirir** (Shift'sizken olduğu gibi; yön ters gelirse not edilir).

## Kanıt
- **[kaynak]** (decompile 26.3.0.101):
  - `Options`: `ctrlClickEmulatesRightClick` (macOS'ta Kontroller ekranında; `OptionInstance.createBoolean(..., false, MacosUtil::setCtrlClickEmulatesRightClick)`) → `Minecraft` kurulumunda `InputQuirks.EMULATE_RIGHT_CLICK_WITH_CTRL_KEY` (= macOS) ise `MacosUtil.setCtrlClickEmulatesRightClick(...)` → `SDLHints.SDL_SetHint("SDL_MAC_CTRL_CLICK_EMULATE_RIGHT_CLICK", value ? "1" : "0")`. `MouseHandler`'da Ctrl→sağ tık çevirisi yok.
  - `Minecraft#hasControlDown()` = `InputConstants.isKeyDown(224) || isKeyDown(228)` (LCTRL/RCTRL); `keyDrop` → `gameMode.dropItem(player, hasControlDown())`.
  - `AbstractContainerScreen#keyPressed`: `keyDrop.matches(event)` → `slotClicked(..., event.hasControlDown() ? 1 : 0, THROW)`; `InputWithModifiers#hasControlDown` = `(modifiers & 192) != 0` (Cmd'ye çevrilen `hasControlDownWithQuirk` **değil**).
  - `Window#onQuitRequested`: `quitShortcuts || !InputQuirks.isQuitShortcutDown()`; `quitShortcuts` varsayılanı `true`.
  - `SDLEventHandler#handleMouseWheelEvent`: macOS'ta Shift basılı ve yalnızca yatay delta varsa `scrollY = −x` (`InputQuirks.SHIFT_INVERTS_SCROLL_AXIS`).
- **[log]** `./gradlew runSpikeClient -PspikeMods=macinput` (`docs/adr/kanit/s2_s3_s7_runs.log` §3) →
  ```
  [MacOSInputFixesClientMod] Native library loaded successfully!
  [MacOSInputFixes] First runTick after game load — initializing macOS native hooks
  ... S3b non-pause screen ... DONE   (0 ERROR)
  ```
  (`mixins.macos_input_fixes.json` `required: true`, `defaultRequire: 1` → bir hedef bile tutmasa açılış çökerdi.) Dylib: Mach-O universal x86_64 + arm64.
- **[log]** `./gradlew runSpikeClient -PspikeMods=mcmouser` →
  ```
  Mod ID: 'minecraft', Requested by: 'mcmouser', Expected range: '[26.1,26.3)', Actual version: '26.3'
  FMLLoader: Mod mcmouser requires minecraft 26.1 or above, and below 26.3
  ```
- **[log]** Ayar dosyaları: yeni geliştirme istemcisinin `options.txt`'si `ctrlClickEmulatesRightClick:false`, `quitShortcuts:true` (vanilla varsayılanları); kullanıcının `Vulkan Optimization` `options.txt`'si `ctrlClickEmulatesRightClick:false`, `quitShortcuts:false`, `key_key.sprint:key.keyboard.left.shift`. (Bunlar ayarı gösterir, davranışı değil.)
- **[kullanıcı, 2026-11-10 — D12]** `Vulkan Optimization` (26.3, giriş modu yok): Kontroller ▸ "Right click emulation" kapalıyken macOS giriş sorunları düzeliyor. Karar 1 fiziksel olarak doğrulandı; kontrol listesi madde madde raporlanmadı → Faz 5'te Theoria instance'ında bir kez tekrarlanır.
- **[çıkarım → yukarıdaki onayla kapandı]** SDL3'ün Cocoa katmanının ipucu `0` iken Ctrl+tıkı gerçekten sol tık olarak iletmesi ve Ctrl+Q'nun macOS tarafından yutulmaması — yalnızca fiziksel testle görülür.

## Sonuçlar
- ✅ Temel pakette macOS için ek mixin/mod yok → 26.x güncellemelerinde kırılacak bir parça az.
- ✅ Yedek yollar tanımlı; Faz 5 DoD'deki "Ctrl+tık/Ctrl+Q çalışıyor" maddesi bu ADR'nin kontrol listesiyle kapanır.
- ⚠ Fiziksel tuş davranışı yalnızca gerçek girdiyle test edilebilir; sentetik OS olayı enjekte etmedik (kullanıcının pencerelerine tıklama riski). Bu, ADR'nin tek açık doğrulamasıdır ve sonucu ne olursa olsun karar tanımlıdır (1 → 2 → 3).
- ⚠ Mac Input Fixes eklenirse JDK uyarısı: `System::load` → `--enable-native-access=macos_input_fixes` JVM argümanı Prism instance'ına eklenmeli (gelecek JDK'larda zorunlu olabilir).

## Sonraki fazlara yükümlülükler
- **Faz 5:** paket varsayılan `options.txt`'sine `ctrlClickEmulatesRightClick:false`, `quitShortcuts:false`; kontrol listesi Theoria instance'ında (M1 DoD); başarısızlıkta Yedek 1 → aynı liste → Yedek 2. `docs/07` Faz 5 mod listesindeki "Mac Input Fixes" koşulludur (`[mac-yedek]`), temel pakete girmez.

## Faz 5 sonucu (2026-11-11)
- **Karar 1 pakete işlendi:** `pack/packwiz/options.txt` (`preserve`) `ctrlClickEmulatesRightClick:false`, `quitShortcuts:false`; `tools/check_pack.py` bunları denetler (`--break` ile sınandı).
- **Kanıt zinciri [kaynak] → [log]:** taze kurulumda duman senaryosu `option → SDL ipucu` zincirini okur: `ctrlClickEmulatesRightClick=false → SDL_MAC_CTRL_CLICK_EMULATE_RIGHT_CLICK=0 (consistent)`, `quitShortcuts=false` (tam paket, Vulkan, `docs/journal/kanit-f05/client_smoke_fresh_pack.txt`). Davranışın kendisi (SDL'nin Ctrl+tıkı sol tık olarak iletmesi) hâlâ yalnızca fiziksel girdiyle görülür.
- **Yedek 1 hazır:** Mac Input Fixes 26.10.03 pakette `[option]` (varsayılan KAPALI); tam pakette, Better Combat/Backpacks/DH ile birlikte yüklenip duman senaryosunu geçti, `--enable-native-access=macos_input_fixes` instance'a yazılıyor (`make_instance.py --with mac-input-fixes-neoforged`). **McMouser** 26.3'te yine reddedilir (matris: 26.3 build'i yok).
- **Yedek 2 (`dev.theoria.macos`) yazılmadı** — Yedek 1 de gerekmeden temel karar fiziksel testte geçerse hiç gerekmez.
- **Açık:** kontrol listesi (i)-(iii) kullanıcıda (M1 DoD); sonuç bu bölüme işlenir.

## Geri dönüş planı
Karar 1 → 2 → 3 sırası (yukarıda). Her adım aynı 3 maddelik kontrol listesiyle doğrulanır.
