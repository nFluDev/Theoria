# 00 — Özet

## Tek cümle
Vanilla gibi başlayan, kanatla özgürce uçup devasa bir dünyayı keşfettiğin, mob/maden/dungeon avıyla 14 kademelik zırh-silah merdivenini tırmandığın, Netherite'i **katalizör** olarak kullanıp eşyalarını sınırsız güçlendirdiğin ve anime-statüsü tarzı bir seviye ekranıyla kendini geliştirdiğin bir modpack — özel modlarımızla.

## Üç katman
1. **Hazır modlar** (JEI, Curios, Fantastic Wings, Sodium/Lithium, Jade, Waystones, Terralith, Dungeons & Taverns, Lootr, harita, Mac düzeltmeleri…) — 26.3'te doğrulanmış olanlar `docs/11`'de.
2. **Kendi modumuz `theoria`** (tek NeoForge projesi, çok modüllü Gradle): HUD, Kanatlar ekranı, denge/hasar çekirdeği, materyaller+zırh+alet, Kataliz Masası, moblar, biyom/yapı, Durum ekranı, Curios Max, Kozmetik, Relics.
3. **Pack**: packwiz ile yönetilen mod listesi + config + Prism instance.

## Dürüst başlangıç gerçekleri (araştırmayla doğrulandı, 9 Kas 2026)
- Minecraft **26.3** (15 Eyl 2026) için NeoForge **26.3.0.101**, JEI 31.17, Curios 17.0.1, Fantastic Wings 26.3.1, Sodium 0.9.3-beta, Jade, Waystones, Terralith, Lootr **var**. Apotheosis, Silent Gear, Artifacts, Relics, Regions Unexplored, YUNG's/WDA, Alex's/Mowzie's/Cataclysm **26.x'te bulunamadı** → kendi çözümümüz ya da erteleme.
- **Java 25 şart**; makinede yalnızca Java 21 var → Faz 0'ın ilk işi.
- **Vulkan çalışıyor** (kullanıcı onayı + Prism `Vulkan Optimization` instance'ı: `preferredGraphicsBackend:"vulkan"`, 26.3, Sodium 0.9.3, render distance 32). Kodumuz yine arka uçtan bağımsız yazılır.
- **Shader:** Iris Vulkan'da çalışmıyor; Vulkan-yerli **Vitrail Shaders** (NeoForge 26.3 ✅) + diskindeki **Complementary Reimagined** kullanılacak. Biz shader yazmıyoruz; yalnızca vanilla render tiplerini kullanırız ki shader paketleri bizim içeriğimizi de işlesin.

## Kilometre taşları
| | Ne zaman oynanabilir | İçerik |
|---|---|---|
| **M1** | Faz 5 sonu | Vanilla+ : 5-kalp HUD, Kanatlar ekranı, QoL, Mac düzeltmeleri, Vulkan + Vitrail shader'lı pack |
| **M2** | Faz 8 sonu | 14 kademe zırh/alet, Kataliz Masası, ilk 30+ mob ve drop zinciri — asıl oyun döngüsü |
| **M3** | Faz 11 sonu | Yeni dünya (biyom/yapı/Haven), Durum ekranı, Curios Max, Kozmetik — "v1.0 adayı" |
| **v1.1** | Faz 12 | Kendi Relics'lerimiz |
| **v1.0** | Faz 13 | Denge turu, uzun oyun testi, yayın |

Kritik sıra kuralı (LucidRem'den): **biyom/dünya üretimi Faz 9'da kesinleşmeden "asıl dünya"yı başlatma** — sonradan eklenen worldgen chunk dikişi bırakır.
