# Theoria

Keşif ve güçlenme odaklı Minecraft modpack'i + sıfırdan yazılan modlar. Vulkan-hazır, NeoForge, Java 26.x.

**Durum:** sürüm adayı **1.0.0-rc.1** (Faz 0-13 uygulandı; v1.0 etiketi kullanıcının 20+ saatlik oyun testi ve görünüş onaylarına bağlı) → `STATUS/PROGRESS.md` · yayın: [docs/13-yayin.md](docs/13-yayin.md) · doğrulama: `python3 -I tools/preflight.py`

## Belge indeksi (okuma sırası)
| Dosya | İçerik |
|---|---|
| [docs/00-ozet.md](docs/00-ozet.md) | Tek sayfa özet: ne yapıyoruz, ne zaman oynanabilir |
| [docs/01-gereksinimler.md](docs/01-gereksinimler.md) | İsteklerin madde madde, yorumlarımla (belirsiz yerler işaretli) |
| [docs/02-teknik-temel.md](docs/02-teknik-temel.md) | Sürüm, loader, Java 25, Vulkan gerçekleri, LucidRem dersleri |
| [docs/03-mimari.md](docs/03-mimari.md) | Repo yapısı, modüller, veri akışı, konvansiyonlar |
| [docs/04-sistem-tasarimi.md](docs/04-sistem-tasarimi.md) | HUD, Kanatlar, Durum ekranı, Kataliz Masası, Kozmetik, Curios Max, macOS |
| [docs/05-icerik-tasarimi.md](docs/05-icerik-tasarimi.md) | Kademeler, madenler, moblar, biyomlar, yapılar, Haven'lar |
| [docs/06-denge-modeli.md](docs/06-denge-modeli.md) | Sayılar: zırh/silah/mob/hasar azaltma/XP eğrisi (üretilmiş tablo) |
| [docs/07-fazlar.md](docs/07-fazlar.md) | Faz faz iş listesi, bitiş kriterleri, model+effort |
| [docs/08-model-ve-baglam-rehberi.md](docs/08-model-ve-baglam-rehberi.md) | Hangi aşamada hangi model/effort, `/clear` ve `/compact` politikası |
| [docs/09-riskler.md](docs/09-riskler.md) | Riskler ve azaltma |
| [docs/10-acik-sorular.md](docs/10-acik-sorular.md) | Başlamadan cevaplanacak sorular (varsayılanlarla) |
| [docs/11-mod-matrisi.md](docs/11-mod-matrisi.md) | 26.2 / 26.3 için gerçek mod erişilebilirliği (Modrinth, 9 Kas 2026) |
| [docs/13-yayin.md](docs/13-yayin.md) | Faz 13: sürüm kimliği, yayın akışı, lisans notları, oyun testi kapısı |
| [docs/adr/](docs/adr/) | Mimari kararlar (ADR-001..008; 001-005 Faz 0 kanıtlarıyla) |
| [docs/journal/](docs/journal/) | Faz başına kısa günlük (yapılan · öğrenilen · kararlar) |
| [prompts/](prompts/) | Her faz için başlangıç prompt'u + devir şablonu |

## Hızlı başlangıç
```
cd <depo klasörü>   # örn. ~/code/Theoria
python3 -I tools/preflight.py          # veri denetimleri + derleme + GameTest'ler (yeşil olmalı)
# yeni Claude Code oturumu: STATUS/PROGRESS.md → "Bir sonraki adım"daki prompt'u başlat
```
