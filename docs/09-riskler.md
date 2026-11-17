# 09 — Riskler

| # | Risk | Olasılık/Etki | Azaltma |
|--:|---|---|---|
| R1 | ~~Vulkan çalışmıyor~~ → **Düşürüldü (D7):** kullanıcı makinesinde Vulkan + Vitrail kanıtlı çalışıyor. Kalan: 26.4'te Vulkan varsayılan olunca regresyon, Vitrail beta olduğundan kırılma | Düşük / Orta | Faz 0 S1 tekrar doğrulama; 26.3'te kal; Vitrail sürümünü pack'te sabitle; **OpenGL+Iris yedeği kalktı (D19)** — çare: sürüm sabitleme / Vitrail-MoltenVK güncellemesini bekleme |
| R2 | 26.x API'leri 1.21'den çok farklı (render state, codec adları) → LucidRem kodu doğrudan taşınmaz | Kesin / Orta | S3 render spike'ı; taşıma = yeniden yazma; NeoForge primer + vanilla kaynak; Opus xHigh |
| R3 | Java 25 / Gradle 9.1 / IDE uyumu | Orta / Düşük | Faz 0 ilk iş; Temurin 25 |
| R4 | Üçüncü taraf mod eksikliği (Apotheosis, Relics, yapı/mob modları) | Kesin / Orta | Bilerek kendi sistemimiz (Kataliz, Relics, Haven); hazır modlara bağımlılık minimum |
| R5 | FW 26.3.1 iç yapısı değişmiş; animatör/katalog API'si bilinmiyor | Orta / Orta | S2; gerekirse sadece komut/event entegrasyonu |
| R6 | Enchant seviyesi 255'te kesiliyor ("sınırsız" sözü) | Yüksek / Düşük | S4; kendi component'imiz; azalan getiri ile pratikte sınırsız |
| R7 | Vanilla zırh formülü 20'de tavan → sayılar anlamsız | Kesin / Yüksek | Hasar azaltma enjeksiyonu (ADR-003, Faz 2) — projenin en kritik teknik karar |
| R8 | Sayı enflasyonu: HUD'da 6 haneli sayılar, TTK bozuluyor | Orta / Orta | `sim.py`, kısaltma, TTK ölçüm testi |
| R16 | Fabric'teki C2ME/VMP NeoForge'da yok → worldgen/ağ performans farkı | Orta / Düşük | Q1; Chunky ön üretim; ModernFix-mvus, Lithium, Ksyxis |
| R17 | 9999 seviye + 40K beceri puanı + %1600 XP → sayı kırılması | Yüksek / Orta | Q3: yumuşak tavan/azalan getiri; XP `long`; Faz 10 sim |
| R9 | Zırh render gizleme/kozmetik 26.x render yeniden yazımında zor | Orta / Orta | S3; fallback: yalnızca kendi oyuncuda |
| R10 | Worldgen'i sonradan değiştirmek chunk dikişi bırakır | Kesin / Yüksek | Faz 9 sonrası worldgen dondurulur; M2'ye kadar test dünyaları kullan |
| R11 | Performans (16 GB, RX 580): 20 biyom + 40 mob + yapılar | Orta / Orta | Chunky ön üretim, render distance 8-10, Sodium/Lithium; GameTest bütçeleri |
| R12 | Kapsam çok büyük (≈ 45 oturum) | Yüksek / Yüksek | M1/M2/M3 kilometre taşları; kapsam kesme sırası (docs/07 sonu) |
| R13 | Bağlam şişmesi (LucidRem project.md 3000 satır) | Orta / Orta | Faz başı journal, kısa PROGRESS.md, `/clear` disiplini |
| R14 | Lisans/dağıtım: yalnızca kendin oynuyorsan sorun yok; paylaşırsan CurseForge/Modrinth kuralları | Düşük | Paylaşımda packwiz manifest + bağlantı |
| R15 | Ekran görüntüsü alma özel pencereleri yakalayabilir (LucidRem'de olmuş) | Düşük / Yüksek | Yasak; çevrimdışı render ve kullanıcı doğrulaması |
