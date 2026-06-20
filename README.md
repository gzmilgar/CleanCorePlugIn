# SAP Clean Core Analyzer

Eclipse ADT plug-in. SAP müşterilerinin Z* / Y* geliştirmelerini tarar,
S/4HANA / RISE migration için clean-core karşılıklarını ve efor tahminini
çıkarır. ATC destekli olmayan eski sistemlerde (R/3) bile bundled static
analyzer + SAP Cloudification Repository mapping ile çalışır.

## Özellikler

- **Plug-and-play mapping havuzu**: ilk açılışta otomatik olarak SAP'nin
  resmi [`Cloudification Repository`](https://sap.github.io/abap-atc-cr-cv-s4hc/)
  ve api.sap.com kataloğundan binlerce mapping indirilir
  (`~/.cleancore/sap-release-data.json`'a cache). Müşteri sisteminin internet
  erişimi gerekmez.
- **AnalysisWizardDialog**: Full Z*/Y* scan / Package prefix / Single object
  scope. Object types (Reports, FMs, Classes, ...) checkbox.
- **Analyze Selected Package** (Project Explorer popup): Bir ADT paketine sağ
  tıkla → Clean Core → Analyze Selected Package. Paketin altındaki Z*/Y*
  source objelerini (Classes, Programs, Includes, FMs, BAdI/Exit impls, ...)
  programatik olarak açar (ADT cache priming) ve analiz eder. Dictionary /
  Texts gibi kod-olmayan kategoriler otomatik atlanır.
- **Analyze Current File** (Ctrl+Shift+K) ve **Analyze All Open Editors** —
  hızlı tek-dosya / batch workflow.
- **Background Job**: UI donmaz, Cancel butonu Eclipse Progress view'da.
- **Mapping Maintenance UI**: Arama, manuel CRUD, "Sync from SAP
  Cloudification Repo" butonu — USER override'lar korunur.
- **Export**: CSV / JSON (+ disposition & scenario alanları) ve **Project
  Report** (HTML + özet CSV, aşağıya bkz.).
- **Effort Estimation**: S/M/L/XL kategori bazlı MD tahmini (preferences'tan
  katsayılar değiştirilebilir).
- **Static analyzer fallback**: ATC yoksa bundled kurallar (SELECT *, NATIVE
  SQL, CALL SCREEN vs.)

## SAP Transformation Assessment (yeni)

Araç, tekil Z*/Y* clean-core taramasının ötesinde **eski sistem → yeni sistem**
dönüşümlerini (ECC→S/4HANA on-prem, ECC/ERP→RISE/S4HC, R/3→RISE) proje
seviyesinde değerlendirir. SAP'nin resmi *"Custom Code Adaptation to SAP
S/4HANA and ABAP Cloud"* metodolojisiyle hizalıdır.

- **Transformation Scenario**: Run Analysis wizard'ında kaynak→hedef senaryosu
  seçilir. Her senaryo kendi **efor profilini** (man-day katsayı çarpanı), rule
  pack'lerini ve özelliklerini (cloud readiness, data model simplification,
  unicode) taşır. Senaryolar `resources/scenario/scenarios.json`'dan yüklenir;
  dosya okunamazsa built-in default'a düşülür.
- **Disposition karar ağacı** (SAP kanonik sınıflandırması): her obje **RETIRE**
  (kullanılmıyor → kaldır), **RETAIN** (standart S/4 karşılıyor), **ADAPT**
  (S/4HANA için uyarla) veya **RENOVATE** (ABAP Cloud / RAP / released API'ye
  modernize) olarak etiketlenir.
- **Kullanım verisi ile scoping**: ABAP Call Monitor (SCMON/SUSG) kullanım
  sayısı 0 olan objeler otomatik **RETIRE** (efor ≈ 0) → kapsam daralır.
- **Profil bazlı efor**: aynı obje seti farklı senaryolarda farklı man-day
  verir (ör. R/3→RISE en yüksek çarpan). `EffortRules` profilleri
  `object_rules.json` `profiles` bloğunda.
- **Entegrasyon envanteri**: SM59 RFC, WE20 IDoc, SICF/OData, web servisleri,
  PI/PO ve BTP gibi entegrasyonlar ADT REST'te görünmez (Basis config). Bu yüzden
  kaynak sistemde çalıştırılan `resources/extract/Z_TRANSFORM_INVENTORY.abap.txt`
  raporu tek bir JSON üretir (şema: `resources/extract/extract_schema.json`).
  Run Analysis wizard'ında *"Integration extract"* alanından bu JSON seçilir;
  senaryonun aktif ettiği kategoriler kapsama alınır, her kalem için S/M/L/XL +
  man-day tahmini (kullanım=0 → retire ≈ 0) hesaplanır ve proje raporundaki
  *integration* kategorisine + dalga planına eklenir. Dosya yoksa/bozuksa
  graceful biçimde atlanır (run asla durmaz).
- **Project Report** (*Export Project Report* butonu): presales-dostu,
  bağımlılıksız **HTML** rapor + özet **CSV**. İçerik: toplam man-day (+
  contingency), kategori kırılımı (custom code / modifications / DDIC /
  integration), disposition kırılımı, **dalga/faz planı** (Wave0 decommission →
  Wave3 renovate), efor boyut karışımı ve rol kırılımı (developer / functional /
  basis). Ayarlar `object_rules.json` `report` bloğundan.
- **Geriye uyumluluk**: "Generic Clean Core readiness" (default) senaryosu
  bugünkü davranışı birebir korur (disposition UNDECIDED, MD değişmez).

## Kurulum (geliştirme için)

### Gereksinimler
- Eclipse IDE (2024-09 veya üstü) + SAP ABAP Development Tools (ADT)
- Java 11+

### Adımlar
1. Bu repo'yu klonla:
   ```bash
   git clone https://github.com/gzmilgar/CleanCorePlugIn.git
   ```
2. Eclipse'i aç → **File → Import → General → Existing Projects into Workspace**
3. Klonlanan dizini seç → Finish.
4. Proje workspace'e gelir. Otomatik build başlar.
5. **Run → Run As → Eclipse Application** → yeni Eclipse instance açılır,
   plugin yüklü.
6. Yeni instance'ta: **Window → Show View → Other → SAP Clean Core → Clean
   Core Analyzer**.

## Kullanım

### İlk kez
1. Plugin ilk açılışta arka planda SAP Cloudification Repo + api.sap.com
   cache'ler (30 sn).
2. **Clean Core Analyzer** view → **Connect** → ABAP project dropdown'undan
   müşteri sistemini seç → OK.
3. **Run Analysis** → wizard'da scope seç (Full / Package prefix / Single
   object) → OK.
4. Tablo dolar. Satıra tıkla → alt panelde Findings / Recommended Mappings /
   Reasoning.
5. **Export CSV/JSON** ile dış sisteme aktar.

### Package-bazlı analiz (HTTP gerekmez)
1. Project Explorer'da bir ADT paketine sağ tıkla.
2. **Clean Core → Analyze Selected Package** seç.
3. Plugin, paketteki Z*/Y* source objelerini (Classes/Programs/Includes/...)
   editör'de açar, source'larını çeker, analiz eder.
4. Sonuçlar üst main table'a + Findings / Recommended Mappings / Reasoning
   tab'larına + Current File tab'ına düşer. Export da aktif.

### Mapping aramaları
- **Mapping Maintenance** view → arama: `BSEG`, `BAPI_SALESORDER`, `VBAK`,
  `MARA` vs. — SAP'nin önerdiği successor görünür.
- Manuel mapping ekleyebilirsin (USER override).
- "Sync from SAP Cloudification Repo" butonu ile mapping havuzunu güncelle.

## Bilinen sınırlamalar

- **SAProuter-only sistemlere doğrudan erişim**: Plain HTTP (Java's
  HttpURLConnection) SAProuter tunnelling'i desteklemiyor. Bu durumda Run
  Analysis hata verir. Çözüm:
  - VPN ile müşteri network'üne bağlan, veya
  - Doğrudan erişilebilir bir sistem kullan (BTP ABAP Trial, S/4HANA Cloud
    demo), veya
  - **Analyze Selected Package** kullan — HTTP gerektirmez, sadece ADT'nin
    açabildiği objelere bakar.
- **Mapping Maintenance** her zaman offline çalışır — sistem bağlantısından
  bağımsız değerli.

## Plugin'i JAR olarak export et (dağıtım için)

1. Host Eclipse'te `com.sap.cleancore` üzerine sağ tık → **Export → Plug-in
   Development → Deployable plug-ins and fragments**
2. `com.sap.cleancore (1.0.0.qualifier)` ✓ seç
3. Destination: bir dizin (örn. `~/Desktop/cleancore-plugin-dist`)
4. Options → **Use class files compiled in the workspace** ✓
5. Finish → `plugins/com.sap.cleancore_1.0.0.YYYYMMDD.jar` üretilir
6. Başka bir Eclipse'in `dropins/` klasörüne kopyala → restart → plugin
   yüklü gelir

## Proje yapısı

```
com.sap.cleancore/
├── META-INF/MANIFEST.MF
├── plugin.xml
├── build.properties
├── .project, .classpath  (Eclipse import için gerekli)
├── resources/
│   ├── mapping/
│   │   ├── fm_mapping.json
│   │   ├── bapi_mapping.json
│   │   ├── object_rules.json   (effort rules + profiles + report config)
│   │   └── atc_variants.json
│   ├── scenario/
│   │   └── scenarios.json      (transformation scenarios)
│   ├── extract/
│   │   ├── Z_TRANSFORM_INVENTORY.abap.txt  (ABAP inventory extractor)
│   │   └── extract_schema.json (extract ↔ ingestor contract)
│   ├── data/
│   └── icons/
└── src/com/sap/cleancore/
    ├── Activator.java
    └── analyzer/
        ├── CleanCoreBootstrapper.java
        ├── analyzers/    StaticAbapAnalyzer, ObsoleteApiDetector, ...
        ├── collectors/   ZObjectCollector, AnalysisService,
        │                 WorkspaceAdtCollector, AdtResourceSourceFetcher, ...
        │     └── inventory/  IntegrationInventoryService, ExtractIngestor
        ├── data/         AdtConnectionService, CapabilityDetector,
        │                 ReleaseObject, SapReleaseDataService
        ├── effort/       EffortEstimator, EffortRules (profile-aware)
        ├── scenario/     ScenarioRegistry
        ├── report/       ProjectEstimate, ProjectEstimateBuilder,
        │                 ProjectReportExporter
        ├── handlers/     AnalyzeCurrentFileHandler,
        │                 AnalyzeAllOpenEditorsHandler,
        │                 AnalyzeSelectedPackageHandler
        ├── mapping/      MappingRepository, SapApiHubClient,
        │                 SapCloudificationBridge
        ├── model/        ZObject, AnalysisRun, MigrationItem,
        │                 MappingEntry, AnalysisFilter, Finding,
        │                 TransformationScenario, Disposition, ...
        ├── preferences/  CleanCorePreferencePage
        ├── ui/           CleanCoreAnalyzerView, MappingMaintenanceView,
        │                 ConnectionDialog, AnalysisWizardDialog
        └── utils/        SimpleJsonParser, JsonWriter, ExportUtil,
                          ResourceLoader
```

## Lisans

Tüm hakları saklıdır.
