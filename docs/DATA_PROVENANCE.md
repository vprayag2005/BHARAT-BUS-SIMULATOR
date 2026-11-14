# Data Provenance and Licensing Register

This register records all external data sources, digital elevation models, 3D models, textures, audio, and code libraries used in Bharat Bus Simulator.

## 1. Geographic & Elevation Data Sources

| ID | Dataset / Source | Coverage / Region | License | Terms & Attribution Requirements | Verification Status | Usage Phase |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **GEO-01** | OpenStreetMap (OSM) | Kerala (Thrissur pilot district) | Open Database License (ODbL 1.0) | Requires clear in-game and documentation attribution: *"© OpenStreetMap contributors"*. Database share-alike rules apply to derived spatial databases. | Verified source | Master Build |
| **DEM-01** | Copernicus DEM GLO-30 | South India (Kerala, TN, KA) | Open / Copernicus License | 30m resolution digital surface model. Free for commercial/non-commercial use with citation. | Documented | Master Build |
| **REF-01** | Survey of India / Official Road Gazettes | Kerala State / District boundaries | Public reference / Government of India | Authoritative classification reference for National Highways (NH 544, NH 66) and State Highways (SH 69, SH 61, SH 51). | Verified | Master Build |
| **REF-02** | Kerala State Road Transport Corporation (KSRTC) & Local Municipality Records | Thrissur District Terminals | Public reference | Spatial positions for Sakthan Thampuran Private Bus Stand and Vadakke (Priyadarshini) Bus Stand. | Verified | Master Build |

---

## 2. Verified Landmark Register (Thrissur Master Build)

| Landmark Identifier | Real-World Name | Malayalam Name | Coordinates | Elevation | Verification Notes |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `DEST_SAKTHAN_STAND` | Sakthan Thampuran Bus Stand | ശക്തൻ തമ്പുരാൻ ബസ് സ്റ്റാൻഡ് | 10.5165° N, 76.2150° E | 11.5 m | Primary departure hub for south-bound transit; terminal departure bay spawn location. |
| `DEST_VADAKKE_STAND` | Vadakke Stand | വടക്കേ ബസ് സ്റ്റാൻഡ് | 10.5285° N, 76.2135° E | 13.0 m | Northern regional bus terminal connecting north Thrissur routes. |
| `DEST_SWARAJ_ROUND` | Swaraj Round (Thrissur Round) | സ്വരാജ് റൗണ്ട് | 10.5244° N, 76.2144° E | 12.0 m | Central circular municipal artery enclosing Thekkinkadu Maidan & Vadakkumnathan Temple. Anchor origin. |
| `DEST_PUTHANPALLI` | Basilica of Our Lady of Dolours | പുത്തൻപള്ളി | 10.5209° N, 76.2183° E | 12.0 m | Major Catholic basilica in Indo-Gothic style with iconic 260-foot (79.2 m) Bible Tower. |
| `DEST_PALIYEKKARA_TOLL` | Paliyekkara Toll Plaza (NH 544) | പാലിയേക്കര ടോൾ പ്ലാസ | 10.4428° N, 76.2600° E | 14.0 m | 4-lane national highway toll plaza with automated barrier and FASTag lanes. |
| `DEST_PEECHI_DAM` | Peechi Dam & Reservoir | പീച്ചി ഡാം | 10.5300° N, 76.3698° E | 75.0 m | Concrete gravity dam, scenic reservoir lake and Western Ghats wildlife sanctuary foothill. |
| `DEST_GURUVAYUR_TEMPLE` | Guruvayur Sri Krishna Temple | ഗുരുവായൂർ ശ്രീകൃഷ്ണ ക്ഷേത്രം | 10.5945° N, 76.0394° E | 8.0 m | Famous Hindu pilgrimage temple town with East Nada pedestrian approach street. |
| `DEST_CHAVAKKAD_BEACH` | Chavakkad Beach | ചാവക്കാട് ബീച്ച് | 10.5720° N, 76.0080° E | 2.0 m | Coastal beach destination on the Arabian Sea with golden sands and coconut palms. |
| `DEST_TRIPRAYAR_TEMPLE` | Triprayar Sree Rama Temple & Beach | തൃപ്രയാർ ശ്രീരാമസ്വാമി ക്ഷേത്രം | 10.4141° N, 76.1155° E | 4.0 m | Ancient Sree Rama temple on the banks of Theevra river and nearby Triprayar coastal beach. |

---

## 3. Strict Compliance & Software Attribution Guidelines
1. **No Proprietary BUSSID Assets:** Never extract, adapt, or copy 3D meshes, textures, sounds, or UI from *Bus Simulator Indonesia (BUSSID)* or any third-party simulator.
2. **Attribution Display:** In-game credits and HUD diagnostics acknowledge: *"Map data © OpenStreetMap contributors"*.
3. **Traceability:** Every road segment retains its topological identifier and road classification for verification and automated scene validation.
