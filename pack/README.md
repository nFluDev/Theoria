# pack/ — Theoria paketi

```
modlist.toml        TEK KAYNAK: slug = sürüm (Modrinth, NeoForge 26.3). Mod eklemek/çıkarmak = buraya satır.
packwiz/            modlist'ten üretilir (elle düzenleme: yalnızca ayar dosyaları)
  pack.toml index.toml  mods/*.pw.toml  shaderpacks/*.pw.toml   ← packwiz metadosyaları (sürüm kimliği = kilit, sha512)
  options.txt                          ← paket varsayılanı: Vulkan (D19) + macOS (ADR-005); yalnızca YOKSA yazılır (preserve)
  vitrail/pack.txt, shaderpacks/*.zip.txt ← Vitrail'in Complementary Reimagined seçimi ve ayarları (preserve)
dist/               (git'te yok) dışa aktarılan Theoria-<sürüm>.mrpack
```

**Mimari (D19):** grafik arka ucu yalnızca **Vulkan**; shader = **Vitrail Shaders** (zorunlu bağımlılığı Sodium) + Complementary Reimagined. **Iris yok, OpenGL hedeflenmez.**

## Günlük iş akışı (repo kökünden)
```
python3 -I tools/pack/sync_packwiz.py          # modlist.toml → pack/packwiz (eksikleri ekler, refresh)
python3 -I tools/check_pack.py                 # modlist ≡ paket, D19, ADR-005, bütünlük (çevrimdışı)
python3 -I tools/check_pack.py --online        # + Modrinth'e karşı özet ve zorunlu bağımlılık
python3 -I tools/modmatrix/matrix.py           # sabitler 26.3 NeoForge'da hâlâ var mı (çıkış 0)
python3 -I tools/pack/make_instance.py         # Prism instance'ı "Theoria" (packwiz-installer'la; oynanış)
python3 -I tools/pack/make_instance.py --export   # pack/dist/Theoria-<sürüm>.mrpack (Prism ▸ Add Instance ▸ Import ile alınır)
```
Mod ekleme: `modlist.toml`'a satır → `sync_packwiz.py` → packwiz'in kendiliğinden eklediği bağımlılıklar uyarı olarak çıkar (`check_pack.py` "modlist'te yok" der) → onları `[bagimlilik]` bölümüne sürümüyle yaz.
Opsiyonel (varsayılan KAPALI, packwiz `[option]`): `[mac-yedek]`, `[opsiyonel-test-edilecek]`, `[bagimlilik-opsiyonel]`. Prism ön-başlatma komutuyla çalışan packwiz-installer GUI'si bunları sorar.
`make_instance.py` yalnızca istediğin instance'ın klasörüne yazar (varsayılan `Theoria`); `--with distanthorizons` gibi opsiyonelleri elle açık kurar; `-g` kipinde installer `default=false`'u yok sayıp **hepsini** kurduğu için araç kapalı olanları süzülmüş bir paket kopyasından kurar.

## Araçlar (git'te yok: `tools/bin/`)
- `packwiz` — Go ile derlenir (resmî go.dev tarball'ı geçici dizinde, sistem Go'su gerekmez):
  `GOBIN=$PWD/tools/bin go install github.com/packwiz/packwiz@latest` (Go 1.27.4 darwin-amd64, sha256 `2b799377…c38f43`).
- `packwiz-installer-bootstrap.jar` — https://github.com/packwiz/packwiz-installer-bootstrap/releases/latest (v0.0.3; sha256 `a8fbb24d…d116c`).

## Prism'e elle kurulum (kullanıcı)
1. Prism ▸ Add Instance ▸ Import ▸ `pack/dist/Theoria-<sürüm>.mrpack` (NeoForge 26.3.0.101 ve 42 dosya gelir; Java 25'i Prism seçer).
2. RAM: 4096 MB min / 6144 MB maks (S1'de ölçülen yapı).
3. Opsiyoneller (DH, Better Combat, Sophisticated Backpacks, Mac Input Fixes) Prism'in içe aktarma penceresinde işaretlenmedikçe kurulmaz.
