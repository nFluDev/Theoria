# ADR-007 — Curios Max ve kozmetik zırh

**Durum:** Kabul · 2026-11-20 (Faz 11a karar, 11b uygulama) · **Etkiler:** Faz 12 (Relics slotları ve etiketleri), Faz 13 (paket gözden geçirme, Curios sürümü)
Kanıt etiketleri: **[log]** komut/log çıktısı · **[kaynak]** decompile edilmiş 26.3.0.101 / NeoForge / Curios 17.0.1 sınıflarından okundu · **[çıkarım]** denenmedi.

## Bağlam
- `docs/04 §6-7`: kozmetik zırh slotları + göster/gizle ayarları + "tam ekran büyük Curios ızgarası". `ADR-002 (c)`: zırh görünümü `AvatarRenderStateModifier` ile değişir; açık borçlar "dört slot + uzak oyuncular", "elytra/pelerin kuralı", "başka modların zırh katmanları".
- Kurallar (CLAUDE.md): doğrudan GL yok · **Curios dışında hiçbir moda derleme bağı yok** (Faz 11 bu istisnayı ilk kez kullanıyor) · ekranlar oyunu durdurmaz · içerik veri-güdümlü · her sistem doğrulanabilir.
- Curios 17.0.1+26.3 (pakette, `zorunlu`): slot türleri veridir ama oyuncuya **kendiliğinden verilmez**; kendi ekranı `CuriosMenu` sunucuda sayfalar; `Slot.x/y` 26.3'te `final`.

## Kararlar
**K1 — Curios'a derleme bağı.** `compileOnly` + `localRuntime` (`maven.modrinth:curios:NBT8yOsH`, paketin sürüm kimliği), `neoforge.mods.toml`'da `required`, `[17.0.1,18)`. **Yalnız `dev.theoria.curios` Curios sınıflarına dokunur** (+ devtest); `dev.theoria.cosmetics` Curios'tan bağımsızdır. Sınır `tools/check_curios.py` ile denetlenir (başka mod bağımlılığı, sürüm kimliği ≠ paket, fazladan `import top.theillusivec4` kırmızı). Slot **tanımı** Java gerektirmez (K6).

**K2 — Kendi `CuriosMaxMenu` + `CuriosMaxScreen`; `CuriosMenu` yeniden kullanılmaz.** Gerekçe [kaynak]: slot konumu `final` ve `CuriosMenu` kurucuda sabitliyor; sayfalama en çok 48 slot/sayfa ve sunucuda (`CPacketPage` → slotları silip yeniden ekler); menü bir 2×2 üretim ızgarası taşıyor. Bizim menü: [0,4) giyilen zırh · [4,8) kozmetik zırh · [8,44) envanter · [44, curioEnd) Curios slotları türe göre · kalanı "Hepsi" ızgarasının kopyaları (hızlı-taşıma hedefi değildir). Her Curios yığını **iki slot**: biri "Hepsi" ızgarasında, biri kendi türünün ızgarasında, ikisi de **sabit hücrede**; sekme/sayfa geçişi yalnız `Slot#isActive()`'i değiştirir (vanilla ekran çizimde de fare isabetinde de ona bakar [kaynak]) → **sunucuya paket gitmez**, sunucu `isActive`'e bakmaz. Slotlar Curios'un kendi `CurioSlot`'unun alt sınıfıdır (`MaxCurioSlot`): doğrulama `SlotContext` üzerinden, takma/çıkarma olayları, tür ikonu Curios'unkiyle aynı. Slot sayısı değişirse (`ICuriosMenu#resetSlots`) Curios slotları yeniden kurulur (`lastSlots`/`remoteSlots` için iki `public` AT, Curios'un da açtığı alanlar).

**K3 — Kozmetik = kendi `theoria:cosmetics` attachment'ı** (4 yuva: başlık, göğüs, pantolon, bot). `Attachments.playerState` (kayıtlı, `copyOnDeath`, `sync` = oyuncuyu alan **tüm** istemcilere [kaynak: `AttachmentType.sync(StreamCodec)` "all clients that receive the holding object"]). Menüdeki 4 kozmetik slotu attachment'ı kaplayan bir `Container`'ın (`CosmeticContainer`) slotlarıdır: tıkla/shift-tık/sürükle vanilla protokolüyle çalışır, **sunucu her yerleştirmeyi `CosmeticRules.accepts` ile doğrular** (o yuvanın `Equippable`'ı + donanım dokusu; aksi halde vanilla zırh katmanı hiçbir şey çizmez). Kozmetik parça ekipman yuvasında **değildir** → zırh/sertlik/büyü/nitelik vermez. Curios'un kendi kozmetik yığınları kullanılmaz (hiçbir slot türünde `add_cosmetic` açık değil). Beşinci "kanat/sırt" yuvası yok (Kanatlar ekranı zaten kozmetik, D14).

**K4 — Görünüm kuralı tek saf fonksiyon:** `CosmeticLook.resolve(slot, giyilen, kozmetik, izleyici)`:
1. göğüs ve giyilen parça **kanat** (`DataComponents.GLIDER`) ise → giyilen (D15; göğüs alanı `HumanoidArmorLayer`, `WingsLayer` ve `CapeLayer`'ı birlikte besler [kaynak] — körlemesine boşaltmak elytra'yı gizler, pelerini geri getirir);
2. kozmetik doluysa ve izleyici bu avatar için gösteriyorsa → kozmetik;
3. avatar izleyicinin kendisi ve "gerçek zırhı göster" kapalıysa → boş (çıplak);
4. yoksa → giyilen (tam vanilla).
Üç **istemci ayarı** (`theoria-client.toml`): `show_cosmetics` (kendim), `show_others_cosmetics`, `show_real_armor` (yalnız kendim; başkasının zırhı asla soyulmaz). İlk ikisi kapalı + üçüncüsü açık = **vanilla** (kuralın tüm girdiler üzerinde süpürme denetimi). Uygulama: **tek** `AvatarRenderStateModifier` (`registerAvatarEntityModifier`) — yerel oyuncu (önizleme dahil), uzak oyuncular, `RemotePlayer`; `Player` olmayan avatar'lar atlanır. Mixin yok, kendi render katmanımız yok.

**K5 — Açma = sunucu tarafı yönlendirme.** Curios tuşu (J) ve envanter düğmesi `CPacketOpenCurios` yollar; Curios sunucuda `player.openMenu(new CuriosMenuProvider())` çağırır. `PlayerContainerEvent.Open` dinleyicimiz bu menüyü görünce **bizimkini açar** (Curios'un menüsü kapanır; elde taşınan eşya Curios'un işleyicisince `containerMenu`'ye, yani bizimkine yazılır). `OpenCuriosMaxPayload` doğrudan istektir. `LOCAL` yapılandırmada `curios_max_screen = false` klasik ekranı geri getirir.

**K6 — Slot sayıları veridir:** `data/curios_slots.csv` → `generator/curios.py` → `data/theoria/curios/slots/<id>.json` (`{"operation":"set","size":N}`; Curios aynı kimliği ad alanları arasında birleştirir, `set` = büyük olan kazanır) + `entities/player.json` (slotları oyuncuya verir: **Curios türleri yükler ama hiçbir varlığa atamaz** — "Loaded 11 curio slots, Loaded 0 curio entities"). Toplam **36** slot (DoD ≥ 30). Yeni tür = yeni satır; yalnız Curios ön ayar kimlikleri kabul edilir (ikon/sıra/dil onlarda).

**K7 — Ekran:** 316×224 (vanilla GUI 320×240'ın altına inmez), `isPauseScreen()=false`; solda zırh | kozmetik | 3B oyuncu (`WingsPreview`, kozmetikleri dünyadaki gibi çizer), sağda tür sekmeleri + Hepsi + Kozmetik + arama + 8×5 ızgara (sayfalı) + altta envanter; arama soluklaştırır (istemci); ayar düğmeleri Kozmetik sekmesinde. Geometri saf (`CuriosMaxLayout`); her GUI boyutu ve 0/1/11/20/40 sekme için örtüşme/taşma süpürülür.

## Kanıt
**[log]** (`./gradlew runGameTestServer -PgameTestOnly=cosmetics_,curios_`, `build/dev-out/*.txt`; Curios 17.0.1 gerçek yüklü):
- `curios_checks` 3 denetim: oyuncu için **35 slot**, 11 tür; sayfa aritmetiği; 8 GUI boyutu × 5 sekme sayısı için yerleşim.
- `curios_menu_layout`: menü her Curios slotunu iki kez taşıyor (36 → 116 menü slotu); sekmeler Curios `order` sırasında; her görünümde aktif slot sayıları `[36, 4, 1, 3, 2, 2, 4, 2, 8, 2, 2, 6, 0]` (Hepsi, 11 tür, Kozmetik); hiçbir görünümde iki aktif slot aynı hücrede değil, hepsi ızgara içinde.
- `curios_menu_moves`: gerçek kap protokolüyle (`clicked`): geçerli eşya yüzük slotunda **Curios'un gerçek envanterinde** (`getStacksHandler("ring")`); çubuk reddedilir ve imleçte kalır; shift-tık envanterden Curios'a (3 kalem → toplam 4, envanterde 0), Curios'tan envantere.
- `curios_menu_cosmetic_slots`: elmas göğüslük kozmetik göğüs yuvasına girer ama **giyilmez** (ekipman boş, zırh değişmez); başlık göğüs yuvasına reddedilir; dolu yuvaya tıklamak imleçle takas eder; çubuk reddedilir; shift-tık dışarı alır; envanterden shift-tık zırhı **gerçekten giydirir**.
- `curios_open_redirect`: Curios'un kendi sağlayıcısıyla `openMenu` → `containerMenu` **`CuriosMaxMenu`**; imleçteki yığın yeni menüye geçer; menü açıkken yeniden açmak hiçbir şey yapmaz; kapatınca yığın envantere döner; doğrudan açma yolu da imleci taşır.
- `cosmetics_sync_to_viewers`: bir oyuncu başka bir oyuncuyu görmeye başlayınca NeoForge'un yolladığı ilk eşzamanlama paketi sahibin `theoria:cosmetics`'ini taşıyor (paket yakalanıp çözüldü). Sonraki değişiklikler `syncEntityUpdate` ile aynı işleyiciden gider [kaynak]; sahte oyuncu hiçbir parçanın izleyici listesinde olmadığından bu yarı başsız denenemedi.
- `cosmetics_stat_neutral` / `_survive_death` / `_codecs`: dört elmas kozmetik zırh, sertlik, azami can ve zırh değerini **değiştirmez**; kozmetikler ölümde ve End-tipi dönüşte kalır; NBT/akış codec'leri gidiş-dönüş.
- `/theoria test cosmetics` 4 denetim: `accepts`, 12 belgelenmiş görünüm vakası, `vanilla_when_off` (ayarlar kapalıyken her girdide giyilen aynen), `never_leaks` (kozmetik yalnız isteyen izleyiciye, kanadın üstüne asla, başkası asla soyulmaz).
- `tools/check_curios.py` OK + `--break` kırmızı (10 ihlal yakalandı).
**[log, istemci]** `./gradlew runDevClient -PdevClient=curios` (gözetimsiz gerçek pencere, 1280×720 → GUI 427×240, OpenGL dev çalıştırması), `build/dev-out/client_curios.txt`, **RESULT PASS**, 17 adım:
- `curios_data`: istemcideki oyuncunun Curios envanteri **35 slot / 11 tür**.
- `open_payload` ve **`open_curios_key`** (Curios'un tuşunun/düğmesinin gönderdiği `CPacketOpenCurios` paketinin aynısı): ikisinde de açılan ekran `CuriosMaxScreen` (Curios'un `CuriosScreen`'i değil), menü `CuriosMaxMenu`, 116 menü slotu, ≥ 100 kare + 3B önizleme çizildi, `isPaused()` false, panel ve her widget/slot pencerenin içinde.
- `tabs`: yüzük sekmesi tam 8 yüzük slotu, kozmetik sekmesi 0 Curios slotu + 3 anahtar, Hepsi 36.
- `place_cosmetic` (**gerçek kap tıklamaları**: elmas göğüslüğü al → kozmetik göğüs yuvasına bırak): sunucu elmas göğüslüğü kozmetik olarak kaydetti, istemciye eşzamanlandı, giyilen göğüs hâlâ boş ve zırh değeri 0.
- `drawn_on_self`: modifier 120 karede 241 kez yerel avatarı değiştirdi; çizilen göğüs = elmas göğüslük. `drawn_on_other`: ikinci avatar (`RemotePlayer`) için de çalıştı (kozmetik başlık çizildi, giyilen göğüs korundu; "başkalarınınkini göster" kapalıyken başlık boş).
- `switches`: varsayılan → kozmetik; "kozmetiklerim" kapalı → giyilen demir; iki kozmetik anahtarı kapalı → vanilla; üçü kapalı → çıplak; yalnız "gerçek zırhım" kapalı → kozmetik hâlâ çizilir. `elytra_rule`: elytra giyiliyken (kozmetik göğüslük dolu, "gerçek zırhım" kapalı dahil) elytra çizilir.
- `take_out` (shift-tık) kozmetiği envantere döndürdü; `cleanup` dünyayı temiz bıraktı.
**[kaynak]** `HumanoidArmorLayer`/`WingsLayer`/`CapeLayer`, `AbstractContainerScreen#extractSlots/findSlot` (`isActive`), `Slot` (`x/y final`), Curios `CuriosMenu`/`CurioSlot`/`CuriosSlotResources`/`CuriosServerPayloadHandler`/`CuriosSlots` (`curios:tag` doğrulayıcısı: `#curios:<slot>` **veya** `#curios:curio`), NeoForge `AttachmentType#sync`, `RegisterRenderStateModifiersEvent`, `NetworkRegistry#configureMockConnection`, `ConfigSync`.

## Sonuçlar
- ✅ Mixin'siz, Curios'un genel API'si + bir iç sınıf (`CurioSlot`) ile; tek derleme istisnası kontrollü.
- ⚠ **`CurioSlot` iç sınıf** (API değil): Curios'un büyük sürüm atlaması menüyü kırabilir → `curios_*` GameTest'leri ve `check_curios.py` alarm verir; geri dönüş: `curios_max_screen=false`.
- ⚠ Curios slotlarına öğe koymak `#curios:<slot>` etiketi ister; üretimde bunu Faz 12'nin relic'leri taşıyacak. Faz 11'in testleri `curios:tag` doğrulayıcısını **geçici** sarmalar (altın parça) ve geri koyar — üretime test öğesi sokulmadı.
- ⚠ Görsel doğruluk (ekranın görünüşü, kozmetiğin dünyada/Vulkan+Vitrail altında görünüşü) log ile değil **kullanıcı onayıyla** kapanır (ekran görüntüsü yasak).
- ⚠ Başka modların **kendi katmanında** çizdiği zırhlar (Geckolib, Curios'un kendi `CuriosLayer`'ı) kozmetik yolundan geçmez [çıkarım]; Faz 13 paket taramasında bakılacak.

## Sonraki fazlara yükümlülükler
- **Faz 12 (Relics):** her relic öğesi `data/curios/tags/item/<slot>.json`'a (veya `#curios:curio`) eklenir (`curios:tag` doğrulayıcısı); slot sayıları/dağılımı `data/curios_slots.csv`'de ayarlanır (Q76); `render_toggle` göz simgesi Max'e eklenir (Q77). Relic testleri `/theoria test relic <id>`.
- **Faz 13:** Curios sürümü pakette sabit; güncellemeden önce `curios_*` testleri koşulur. Başka modların zırh katmanları (Q82) paket taramasında denenir.

## Geri dönüş planı
- Curios iç sınıfı kırılırsa: `TheoriaConfig.curios_max_screen=false` (klasik Curios ekranı; kozmetik zırh menü dışında komutla/ayar ekranı olmadan kalır → `MaxCurioSlot`'u kendi `Slot` alt sınıfımızla değiştirmek ≈ 60 satır: `ResourceHandlerSlot` + `CuriosApi.isStackValid(SlotContext, …)`).
- Render-state modifier yetmezse (başka mod zırhı kendi katmanında çizer): ADR-002 geri dönüş (c).
- Kozmetik slot sayısı/kuralı değişirse: `Cosmetics.SLOTS` + `CosmeticRules` (tek yer).

## Tasarım değişikliği (2026-11-20, kullanıcı — K2/K4/K7'nin yerine geçer)
Kullanıcı ekran görüntüsüyle **Curios'un klasik ekranını birebir** istedi (sol panel, 5 sütun, slot başına gizle noktası) ve sadeleştirmeyi: kendi tabları/arama/Kozmetik sekmesi/3 anahtar **kaldırıldı**.
- **K2':** `CuriosMaxMenu extends CuriosMenu`, `CuriosMaxScreen extends CuriosScreen` (Curios'un kendi paket/sayfa/hızlı-taşıma kodu çalışır; `menuType` AT'si ile kendi MenuType'ımızı bildirir). Slot sırası Curios'unki (0 sonuç · 1-4 üretim · 5-8 zırh · 9-44 envanter · 45 yan el · 46… Curios), 4 kozmetik slot **en sonda**. Tek sayfa (en çok 64 slot; `/theoria test curios` aşılırsa kırmızı).
- **Kozmetik satırı:** panelin ilk satırı = 4 kozmetik slot + 5. hücre **Kanatlar düğmesi** (Kanatlar ekranını açar; kanat öğesi slotu göğüs görünüm alanıyla çakışacağı için slot değil), altında ayırıcı çizgi, mor ton.
- **K4':** kural `resolve(slot, giyilen, kozmetik, gizli, izleyici)`: kanat korunur · izleyici göstermiyorsa vanilla (gizle bayrakları dahil) · slot **gizliyse hiçbir şey** (giyilen de kozmetik de) · kozmetik doluysa o · yoksa giyilen. `theoria:cosmetics`'e 4 bitlik `hidden` eklendi (herkese eşzamanlı, ölümde kalır); "gerçek zırhım" ayarı kalktı, iki istemci ayarı kaldı.
- **Ctrl + sol tık** envanterdeki zırhı kozmetik slotuna koyar (3 vanilla tıklama; eski parça geri gelir), kozmetik parçada geri alır; Shift vanilla kalır. Gizle noktası: Curios'un `RenderButton` sprite'ı, istek `ToggleCosmeticHiddenPayload` (sunucu 0..3).
- **Kanıt:** GameTest 12/12 (yeni `curios_menu_layout`, `cosmetics_hide_switches`, Ctrl+tık dizisi), `/theoria test cosmetics` 5 denetim (16 vaka, süpürmeler), istemci senaryosu **23 adım PASS** (override, gizle noktası, boş kozmetik + gizli + giyili = çizilmez, elytra, Kanatlar hücresi gidiş-dönüş).

## Ek (2026-11-20, kullanıcı geri bildirimi): envanterin Curios düğmesi fareyi ortalıyordu
Düğme → Curios'un menüsü → sunucu yönlendirmesi, istemciye **aç + kapat + yeniden aç** olarak iniyor; kapatma `setScreen(null)` ile fareyi pencere ortasına alıyordu. Çözüm: `CuriosClient` envanter ekranlarında (`InventoryScreen`, yaratıcı sekme) Curios'un `CuriosButton`'ını **aynı görünüm/konumda kendi düğmemizle değiştiriyor** (`ScreenEvent.Init.Post`, `LOWEST`); düğme doğrudan `OpenCuriosMaxPayload(carried)` yollar → tek `OpenScreen`, ekrandan ekrana geçiş, imleç yerinde kalır. J tuşu ve başka modların yolu sunucu yönlendirmesiyle sürer (dünyadan açıldığı için orada zaten ortalanır). **Gerçek pencerede denenmedi** (kullanıcı "oyunu açıp test etmene gerek yok" dedi); derleme + GameTest yeşil, düğme değiştirme mantığı yalnız okuma/derleme düzeyinde doğrulandı.
