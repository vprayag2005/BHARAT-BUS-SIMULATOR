using System;
using UnityEngine;
using UnityEngine.UI;

namespace BharatBusSim.Environment
{
    /// <summary>
    /// Constructs authentic Kerala regional landmarks for the Thrissur master build.
    /// Eliminates camera clipping while delivering detailed, grounded silhouettes.
    /// </summary>
    public static class LandmarkBuilder
    {
        public static bool UseRealisticSakthanStand = true;
        public static bool UseRealisticSwarajRound = true;
        public static bool UseRealisticPaliyekkaraToll = true;

        public static GameObject BuildAllLandmarks(Transform parent, Material concreteMat, Material goldMat, Material tileMat, Material brickMat, Material glassMat)
        {
            var landmarksGo = new GameObject("Thrissur_Landmarks");
            landmarksGo.transform.SetParent(parent, false);

            // 1. Sakthan Thampuran Bus Stand (Terminal Hub & Starting Bay)
            if (!UseRealisticSakthanStand)
            {
                BuildSakthanBusStand(landmarksGo.transform, new Vector3(13.0f, 0f, -174.8f), concreteMat, tileMat);
            }

            // 2. Vadakke Stand (North Terminal)
            BuildVadakkeBusStand(landmarksGo.transform, new Vector3(-19.6f, 0f, 90.6f), concreteMat, tileMat);

            // 3. Swaraj Round & Vadakkumnathan Temple Silhouette
            if (!UseRealisticSwarajRound)
            {
                BuildSwarajRoundCenter(landmarksGo.transform, Vector3.zero, tileMat, goldMat);
            }

            // 4. Puthanpalli (Basilica of Our Lady of Dolours & 260ft Bible Tower)
            BuildPuthanpalliBasilica(landmarksGo.transform, new Vector3(85.2f, 0f, -77.4f), concreteMat, brickMat);

            // 5. Paliyekkara Toll Plaza Structure
            if (!UseRealisticPaliyekkaraToll)
            {
                BuildPaliyekkaraTollPlaza(landmarksGo.transform, new Vector3(998.2f, 0f, -1805.2f), concreteMat, glassMat);
            }

            // 6. Peechi Dam Structure
            BuildPeechiDam(landmarksGo.transform, new Vector3(3398.6f, 0f, 125.0f), concreteMat);

            // 7. Guruvayur Sri Krishna Temple (East Nada plaza setback 35m North of road junction)
            BuildGuruvayurTemple(landmarksGo.transform, new Vector3(-3827.4f, 0f, 1585.0f), concreteMat, goldMat, tileMat);

            // 8. Triprayar Sree Rama Temple (Setback 35m East of highway alongside river)
            BuildTriprayarTemple(landmarksGo.transform, new Vector3(-2126.8f, 0f, -2439.2f), concreteMat, tileMat);

            return landmarksGo;
        }

        private static void BuildSakthanBusStand(Transform parent, Vector3 origin, Material concreteMat, Material roofMat)
        {
            var standGo = new GameObject("Landmark_Sakthan_Thampuran_Bus_Stand");
            standGo.transform.SetParent(parent, false);
            standGo.transform.position = origin;

            // 1. Terminal Concourse & Administration Building (Western perimeter: X = -18m)
            var adminBuilding = GameObject.CreatePrimitive(PrimitiveType.Cube);
            adminBuilding.name = "Terminal_Administration_Building";
            adminBuilding.transform.SetParent(standGo.transform, false);
            adminBuilding.transform.localPosition = new Vector3(-18f, 6.0f, 0f);
            adminBuilding.transform.localScale = new Vector3(14f, 12f, 60f);
            adminBuilding.GetComponent<Renderer>().sharedMaterial = concreteMat;

            // Administrative Window Bands
            Material windowMat = new Material(concreteMat.shader)
            {
                name = "Mat_Admin_Windows",
                color = new Color(0.12f, 0.22f, 0.32f, 1.0f)
            };
            windowMat.SetFloat("_Smoothness", 0.85f);

            for (int f = 0; f < 3; f++)
            {
                var winBand = GameObject.CreatePrimitive(PrimitiveType.Cube);
                winBand.name = $"Window_Band_{f + 1}";
                winBand.transform.SetParent(adminBuilding.transform, false);
                winBand.transform.localPosition = new Vector3(0.505f, -0.25f + f * 0.28f, 0f);
                winBand.transform.localScale = new Vector3(0.02f, 0.14f, 0.90f);
                winBand.GetComponent<Renderer>().sharedMaterial = windowMat;
                var col = winBand.GetComponent<Collider>();
                if (col != null) UnityEngine.Object.DestroyImmediate(col);
            }

            // Clock Tower on Administration Building
            var clockTower = GameObject.CreatePrimitive(PrimitiveType.Cube);
            clockTower.name = "Sakthan_Clock_Tower";
            clockTower.transform.SetParent(standGo.transform, false);
            clockTower.transform.localPosition = new Vector3(-18f, 15.0f, 20f);
            clockTower.transform.localScale = new Vector3(6f, 8f, 6f);
            clockTower.GetComponent<Renderer>().sharedMaterial = concreteMat;

            // 4 Clock Dial Faces on Tower
            Material clockMat = new Material(concreteMat.shader)
            {
                name = "Mat_Clock_Face",
                mainTexture = ProceduralTextureFactory.GetOrCreateClockDialTexture()
            };
            CreateTowerClockFaces(clockTower.transform, clockMat);

            var clockRoof = GameObject.CreatePrimitive(PrimitiveType.Cube);
            clockRoof.name = "Clock_Tower_Roof";
            clockRoof.transform.SetParent(standGo.transform, false);
            clockRoof.transform.localPosition = new Vector3(-18f, 19.8f, 20f);
            clockRoof.transform.localScale = new Vector3(7.5f, 1.8f, 7.5f);
            clockRoof.GetComponent<Renderer>().sharedMaterial = roofMat;

            // Corrugated Canopy Material
            Material canopyMat = new Material(concreteMat.shader)
            {
                name = "Mat_Terminal_Canopy_Corrugated",
                mainTexture = ProceduralTextureFactory.GetOrCreateCorrugatedCanopyTexture(),
                color = new Color(0.88f, 0.92f, 0.96f)
            };
            canopyMat.mainTextureScale = new Vector2(4f, 12f);

            // 2. Realistic North-South Passenger Platforms (Side-boarding, leaving center drive lane clear)
            // Platform 1 (Main Platform on the Left of Bay 1: X = 6.5m in local coords -> world X = 19.5m)
            var plat1 = GameObject.CreatePrimitive(PrimitiveType.Cube);
            plat1.name = "Platform_Curb_1";
            plat1.transform.SetParent(standGo.transform, false);
            plat1.transform.localPosition = new Vector3(6.5f, 0.25f, 5.5f);
            plat1.transform.localScale = new Vector3(4.5f, 0.5f, 38f);
            plat1.GetComponent<Renderer>().sharedMaterial = concreteMat;
            var cpl1 = plat1.GetComponent<Collider>();
            if (cpl1 != null) UnityEngine.Object.DestroyImmediate(cpl1);

            // Platform 1 Arched Pitched Corrugated Canopy (Raised to 7.0m height for camera clearance)
            CreatePitchedCanopyRoof(standGo.transform, new Vector3(6.5f, 7.0f, 5.5f), 5.5f, 40f, canopyMat, "Bay_Canopy_1");

            // Support Pillars for Platform 1
            for (int col = 0; col < 5; col++)
            {
                var pillar = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
                pillar.name = $"Pillar_P1_{col}";
                pillar.transform.SetParent(standGo.transform, false);
                pillar.transform.localPosition = new Vector3(6.5f, 3.5f, -12f + col * 8.5f);
                pillar.transform.localScale = new Vector3(0.35f, 3.5f, 0.35f);
                pillar.GetComponent<Renderer>().sharedMaterial = concreteMat;
                var cp = pillar.GetComponent<Collider>();
                if (cp != null) UnityEngine.Object.DestroyImmediate(cp);
            }

            // Platform 2 (Island Platform on the Right of Bay 1: X = 17.5m in local coords -> world X = 30.5m)
            var plat2 = GameObject.CreatePrimitive(PrimitiveType.Cube);
            plat2.name = "Platform_Curb_2";
            plat2.transform.SetParent(standGo.transform, false);
            plat2.transform.localPosition = new Vector3(17.5f, 0.25f, 5.5f);
            plat2.transform.localScale = new Vector3(4.5f, 0.5f, 38f);
            plat2.GetComponent<Renderer>().sharedMaterial = concreteMat;
            var cpl2 = plat2.GetComponent<Collider>();
            if (cpl2 != null) UnityEngine.Object.DestroyImmediate(cpl2);

            // Platform 2 Arched Pitched Corrugated Canopy
            CreatePitchedCanopyRoof(standGo.transform, new Vector3(17.5f, 7.0f, 5.5f), 5.5f, 40f, canopyMat, "Bay_Canopy_2");

            // Support Pillars for Platform 2
            for (int col = 0; col < 5; col++)
            {
                var pillar = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
                pillar.name = $"Pillar_P2_{col}";
                pillar.transform.SetParent(standGo.transform, false);
                pillar.transform.localPosition = new Vector3(17.5f, 3.5f, -12f + col * 8.5f);
                pillar.transform.localScale = new Vector3(0.35f, 3.5f, 0.35f);
                pillar.GetComponent<Renderer>().sharedMaterial = concreteMat;
                var cp = pillar.GetComponent<Collider>();
                if (cp != null) UnityEngine.Object.DestroyImmediate(cp);
            }

            // Platform 3 & 4 (Additional bays spaced further east)
            for (int p = 3; p <= 4; p++)
            {
                float px = 17.5f + (p - 2) * 11.0f;
                var plat = GameObject.CreatePrimitive(PrimitiveType.Cube);
                plat.name = $"Platform_Curb_{p}";
                plat.transform.SetParent(standGo.transform, false);
                plat.transform.localPosition = new Vector3(px, 0.25f, 5.5f);
                plat.transform.localScale = new Vector3(4.5f, 0.5f, 38f);
                plat.GetComponent<Renderer>().sharedMaterial = concreteMat;
                var cplp = plat.GetComponent<Collider>();
                if (cplp != null) UnityEngine.Object.DestroyImmediate(cplp);

                CreatePitchedCanopyRoof(standGo.transform, new Vector3(px, 7.0f, 5.5f), 5.5f, 40f, canopyMat, $"Bay_Canopy_{p}");
            }

            // 3. Terminal Apron Concrete Pavement (Grounded, covers terminal bays and apron)
            var terminalGround = GameObject.CreatePrimitive(PrimitiveType.Plane);
            terminalGround.name = "Sakthan_Terminal_Pavement";
            terminalGround.transform.SetParent(standGo.transform, false);
            terminalGround.transform.localPosition = new Vector3(15f, 0.04f, 5.0f);
            terminalGround.transform.localScale = new Vector3(6.5f, 1f, 7.0f);
            terminalGround.GetComponent<Renderer>().sharedMaterial = concreteMat;

            // 4. Passenger Platform Signboards (Positioned safely on side platform concourse)
            CreatePlatformSign(standGo.transform, new Vector3(3.5f, 3.2f, 0f), "പ്ലാറ്റ്‌ഫോം 1 : എറണാകുളം / കോട്ടയം / പാലക്കാട്\nPLATFORM 1 : ERNAKULAM / KOTTAYAM");

            // 5. Grand Terminal Exit Gate Arch (North exit gate toward Swaraj Round: widened to 20m, elevated to 7.8m)
            var gateArch = GameObject.CreatePrimitive(PrimitiveType.Cube);
            gateArch.name = "Terminal_Exit_Gate_Arch";
            gateArch.transform.SetParent(standGo.transform, false);
            gateArch.transform.localPosition = new Vector3(12.0f, 7.8f, 32.0f);
            gateArch.transform.localScale = new Vector3(20.0f, 1.4f, 1.2f);
            gateArch.GetComponent<Renderer>().sharedMaterial = concreteMat;
            var cga = gateArch.GetComponent<Collider>();
            if (cga != null) UnityEngine.Object.DestroyImmediate(cga);

            // Left & Right Support Pillars for Exit Gate Arch (Outside roadway at X = 1.5m and 22.5m)
            var gatePillarL = GameObject.CreatePrimitive(PrimitiveType.Cube);
            gatePillarL.name = "Exit_Gate_Pillar_L";
            gatePillarL.transform.SetParent(standGo.transform, false);
            gatePillarL.transform.localPosition = new Vector3(1.5f, 3.9f, 32.0f);
            gatePillarL.transform.localScale = new Vector3(1.0f, 7.8f, 1.0f);
            gatePillarL.GetComponent<Renderer>().sharedMaterial = concreteMat;
            var cpl = gatePillarL.GetComponent<Collider>();
            if (cpl != null) UnityEngine.Object.DestroyImmediate(cpl);

            var gatePillarR = GameObject.CreatePrimitive(PrimitiveType.Cube);
            gatePillarR.name = "Exit_Gate_Pillar_R";
            gatePillarR.transform.SetParent(standGo.transform, false);
            gatePillarR.transform.localPosition = new Vector3(22.5f, 3.9f, 32.0f);
            gatePillarR.transform.localScale = new Vector3(1.0f, 7.8f, 1.0f);
            gatePillarR.GetComponent<Renderer>().sharedMaterial = concreteMat;
            var cpr = gatePillarR.GetComponent<Collider>();
            if (cpr != null) UnityEngine.Object.DestroyImmediate(cpr);

            // Exit Gate Welcome Header Signboard (Elevated to 7.8m height)
            CreatePlatformSign(standGo.transform, new Vector3(12.0f, 7.8f, 31.3f), "ശക്തൻ തമ്പുരാൻ ബസ് സ്റ്റാൻഡ് — തൃശൂർ\nSAKTHAN THAMPURAN BUS STAND, THRISSUR");

            // Dedicated Player Starting Bay Marker
            var spawnSlot = new GameObject("Spawn_Bay_Slot_1");
            spawnSlot.transform.SetParent(standGo.transform, false);
            spawnSlot.transform.localPosition = new Vector3(12f, 0.15f, 5.5f);
        }

        private static void CreateTowerClockFaces(Transform tower, Material clockMat)
        {
            Vector3[] offsets = new[]
            {
                new Vector3(0f, 0f, 0.505f),
                new Vector3(0f, 0f, -0.505f),
                new Vector3(0.505f, 0f, 0f),
                new Vector3(-0.505f, 0f, 0f)
            };
            Quaternion[] rots = new[]
            {
                Quaternion.identity,
                Quaternion.Euler(0f, 180f, 0f),
                Quaternion.Euler(0f, 90f, 0f),
                Quaternion.Euler(0f, -90f, 0f)
            };

            for (int i = 0; i < 4; i++)
            {
                var face = GameObject.CreatePrimitive(PrimitiveType.Quad);
                face.name = $"Clock_Face_{i}";
                face.transform.SetParent(tower, false);
                face.transform.localPosition = offsets[i];
                face.transform.localRotation = rots[i];
                face.transform.localScale = Vector3.one * 0.72f;
                face.GetComponent<Renderer>().sharedMaterial = clockMat;
                var col = face.GetComponent<Collider>();
                if (col != null) UnityEngine.Object.DestroyImmediate(col);
            }
        }

        private static void CreatePitchedCanopyRoof(Transform parent, Vector3 localCenter, float width, float length, Material mat, string name)
        {
            var canopyGo = new GameObject(name);
            canopyGo.transform.SetParent(parent, false);
            canopyGo.transform.localPosition = localCenter;

            float halfW = width * 0.5f;
            float pitchRise = 0.40f;

            // Left pitched corrugated panel
            var leftPanel = GameObject.CreatePrimitive(PrimitiveType.Cube);
            leftPanel.name = "Roof_Pitch_L";
            leftPanel.transform.SetParent(canopyGo.transform, false);
            leftPanel.transform.localPosition = new Vector3(-halfW * 0.5f, pitchRise * 0.5f, 0f);
            leftPanel.transform.localRotation = Quaternion.Euler(0f, 0f, 9f);
            leftPanel.transform.localScale = new Vector3(halfW * 1.05f, 0.08f, length);
            leftPanel.GetComponent<Renderer>().sharedMaterial = mat;
            var c1 = leftPanel.GetComponent<Collider>();
            if (c1 != null) UnityEngine.Object.DestroyImmediate(c1);

            // Right pitched corrugated panel
            var rightPanel = GameObject.CreatePrimitive(PrimitiveType.Cube);
            rightPanel.name = "Roof_Pitch_R";
            rightPanel.transform.SetParent(canopyGo.transform, false);
            rightPanel.transform.localPosition = new Vector3(halfW * 0.5f, pitchRise * 0.5f, 0f);
            rightPanel.transform.localRotation = Quaternion.Euler(0f, 0f, -9f);
            rightPanel.transform.localScale = new Vector3(halfW * 1.05f, 0.08f, length);
            rightPanel.GetComponent<Renderer>().sharedMaterial = mat;
            var c2 = rightPanel.GetComponent<Collider>();
            if (c2 != null) UnityEngine.Object.DestroyImmediate(c2);

            // Central ridge cap beam
            var ridge = GameObject.CreatePrimitive(PrimitiveType.Cube);
            ridge.name = "Roof_Ridge_Cap";
            ridge.transform.SetParent(canopyGo.transform, false);
            ridge.transform.localPosition = new Vector3(0f, pitchRise + 0.04f, 0f);
            ridge.transform.localScale = new Vector3(0.35f, 0.08f, length);
            ridge.GetComponent<Renderer>().sharedMaterial = mat;
            var c3 = ridge.GetComponent<Collider>();
            if (c3 != null) UnityEngine.Object.DestroyImmediate(c3);
        }

        private static void CreateApronArrow(Transform parent, Vector3 localCenter, Material yellowMat)
        {
            var arrowGo = new GameObject("Apron_Exit_Arrow");
            arrowGo.transform.SetParent(parent, false);
            arrowGo.transform.localPosition = localCenter;

            // Shaft
            var shaft = GameObject.CreatePrimitive(PrimitiveType.Cube);
            shaft.name = "Shaft";
            shaft.transform.SetParent(arrowGo.transform, false);
            shaft.transform.localPosition = Vector3.zero;
            shaft.transform.localScale = new Vector3(0.35f, 0.005f, 2.5f);
            shaft.GetComponent<Renderer>().sharedMaterial = yellowMat;
            var c1 = shaft.GetComponent<Collider>();
            if (c1 != null) UnityEngine.Object.DestroyImmediate(c1);

            // Left barb
            var barbL = GameObject.CreatePrimitive(PrimitiveType.Cube);
            barbL.name = "Barb_L";
            barbL.transform.SetParent(arrowGo.transform, false);
            barbL.transform.localPosition = new Vector3(-0.45f, 0f, 0.85f);
            barbL.transform.localRotation = Quaternion.Euler(0f, -35f, 0f);
            barbL.transform.localScale = new Vector3(0.30f, 0.005f, 1.2f);
            barbL.GetComponent<Renderer>().sharedMaterial = yellowMat;
            var c2 = barbL.GetComponent<Collider>();
            if (c2 != null) UnityEngine.Object.DestroyImmediate(c2);

            // Right barb
            var barbR = GameObject.CreatePrimitive(PrimitiveType.Cube);
            barbR.name = "Barb_R";
            barbR.transform.SetParent(arrowGo.transform, false);
            barbR.transform.localPosition = new Vector3(0.45f, 0f, 0.85f);
            barbR.transform.localRotation = Quaternion.Euler(0f, 35f, 0f);
            barbR.transform.localScale = new Vector3(0.30f, 0.005f, 1.2f);
            barbR.GetComponent<Renderer>().sharedMaterial = yellowMat;
            var c3 = barbR.GetComponent<Collider>();
            if (c3 != null) UnityEngine.Object.DestroyImmediate(c3);
        }

        private static void CreatePlatformSign(Transform parent, Vector3 localPos, string text)
        {
            var signGo = new GameObject("Platform_Sign");
            signGo.transform.SetParent(parent, false);
            signGo.transform.localPosition = localPos;

            var board = GameObject.CreatePrimitive(PrimitiveType.Cube);
            board.name = "Board";
            board.transform.SetParent(signGo.transform, false);
            board.transform.localScale = new Vector3(4.2f, 0.85f, 0.08f);
            var col = board.GetComponent<Collider>();
            if (col != null) UnityEngine.Object.DestroyImmediate(col);

            // Blue WorldSpace Canvas Text
            var canvasGo = new GameObject("Sign_Canvas");
            canvasGo.transform.SetParent(signGo.transform, false);
            canvasGo.transform.localPosition = new Vector3(0f, 0f, -0.06f);

            var canvas = canvasGo.AddComponent<Canvas>();
            canvas.renderMode = RenderMode.WorldSpace;
            var rt = canvasGo.GetComponent<RectTransform>();
            rt.sizeDelta = new Vector2(4.2f, 0.85f);
            rt.localScale = Vector3.one;

            var txtGo = new GameObject("Text");
            txtGo.transform.SetParent(canvasGo.transform, false);
            var txtRt = txtGo.AddComponent<RectTransform>();
            txtRt.sizeDelta = new Vector2(400f, 80f);
            txtRt.localScale = Vector3.one * 0.01f;

            var txt = txtGo.AddComponent<Text>();
            txt.text = text;
            txt.font = Resources.GetBuiltinResource<Font>("LegacyRuntime.ttf");
            txt.fontSize = 20;
            txt.alignment = TextAnchor.MiddleCenter;
            txt.color = Color.white;
            txt.raycastTarget = false;
        }

        private static void BuildVadakkeBusStand(Transform parent, Vector3 origin, Material concreteMat, Material roofMat)
        {
            var standGo = new GameObject("Landmark_Vadakke_Bus_Stand");
            standGo.transform.SetParent(parent, false);
            standGo.transform.position = origin;

            // Two-Story Passenger Concourse with Kerala Gabled Roof
            var shelter = GameObject.CreatePrimitive(PrimitiveType.Cube);
            shelter.name = "Vadakke_Passenger_Concourse";
            shelter.transform.SetParent(standGo.transform, false);
            shelter.transform.localPosition = new Vector3(0f, 4.5f, 15f);
            shelter.transform.localScale = new Vector3(32f, 9f, 12f);
            shelter.GetComponent<Renderer>().sharedMaterial = concreteMat;

            var concourseRoof = GameObject.CreatePrimitive(PrimitiveType.Cube);
            concourseRoof.name = "Vadakke_Concourse_Roof";
            concourseRoof.transform.SetParent(standGo.transform, false);
            concourseRoof.transform.localPosition = new Vector3(0f, 9.8f, 15f);
            concourseRoof.transform.localScale = new Vector3(34f, 1.8f, 14f);
            concourseRoof.GetComponent<Renderer>().sharedMaterial = roofMat;

            // Boarding Island Platform
            var platform = GameObject.CreatePrimitive(PrimitiveType.Cube);
            platform.name = "Vadakke_Boarding_Island";
            platform.transform.SetParent(standGo.transform, false);
            platform.transform.localPosition = new Vector3(0f, 0.25f, 0f);
            platform.transform.localScale = new Vector3(30f, 0.5f, 6.0f);
            platform.GetComponent<Renderer>().sharedMaterial = concreteMat;

            var canopy = GameObject.CreatePrimitive(PrimitiveType.Cube);
            canopy.name = "Vadakke_Canopy";
            canopy.transform.SetParent(standGo.transform, false);
            canopy.transform.localPosition = new Vector3(0f, 6.5f, 0f);
            canopy.transform.localScale = new Vector3(32f, 0.35f, 8.0f);
            canopy.GetComponent<Renderer>().sharedMaterial = roofMat;
            var cc = canopy.GetComponent<Collider>();
            if (cc != null) UnityEngine.Object.DestroyImmediate(cc);
        }

        private static void BuildSwarajRoundCenter(Transform parent, Vector3 origin, Material roofMat, Material goldMat)
        {
            var roundCenter = new GameObject("Landmark_Thekkinkadu_Maidan");
            roundCenter.transform.SetParent(parent, false);
            roundCenter.transform.position = origin;

            // Lush Green Thekkinkadu Maidan Hillock (Scaled inside Swaraj Round ring road)
            var hill = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
            hill.name = "Thekkinkadu_Hillock";
            hill.transform.SetParent(roundCenter.transform, false);
            hill.transform.localPosition = new Vector3(0f, 1.8f, 0f);
            hill.transform.localScale = new Vector3(28f, 1.8f, 28f);
            var ch = hill.GetComponent<Collider>();
            if (ch != null) UnityEngine.Object.DestroyImmediate(ch);

            // Vadakkumnathan Temple Sanctum Base
            var templeBase = GameObject.CreatePrimitive(PrimitiveType.Cube);
            templeBase.name = "Vadakkumnathan_Sanctum_Base";
            templeBase.transform.SetParent(roundCenter.transform, false);
            templeBase.transform.localPosition = new Vector3(0f, 5.0f, 0f);
            templeBase.transform.localScale = new Vector3(18f, 6.0f, 18f);

            // Multi-Tiered Pagoda Clay Tiled Roofs
            var pagodaTier1 = GameObject.CreatePrimitive(PrimitiveType.Cube);
            pagodaTier1.name = "Pagoda_Tier_1";
            pagodaTier1.transform.SetParent(roundCenter.transform, false);
            pagodaTier1.transform.localPosition = new Vector3(0f, 8.8f, 0f);
            pagodaTier1.transform.localScale = new Vector3(22f, 1.6f, 22f);
            pagodaTier1.GetComponent<Renderer>().sharedMaterial = roofMat;

            var pagodaTier2 = GameObject.CreatePrimitive(PrimitiveType.Cube);
            pagodaTier2.name = "Pagoda_Tier_2";
            pagodaTier2.transform.SetParent(roundCenter.transform, false);
            pagodaTier2.transform.localPosition = new Vector3(0f, 11.2f, 0f);
            pagodaTier2.transform.localScale = new Vector3(15f, 1.5f, 15f);
            pagodaTier2.GetComponent<Renderer>().sharedMaterial = roofMat;

            // Golden Stupika Finials
            var stupika = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
            stupika.name = "Golden_Stupika_Spire";
            stupika.transform.SetParent(roundCenter.transform, false);
            stupika.transform.localPosition = new Vector3(0f, 13.5f, 0f);
            stupika.transform.localScale = new Vector3(0.9f, 2.0f, 0.9f);
            stupika.GetComponent<Renderer>().sharedMaterial = goldMat;
        }

        private static void BuildPuthanpalliBasilica(Transform parent, Vector3 origin, Material concreteMat, Material brickMat)
        {
            var basilicaGo = new GameObject("Landmark_Puthanpalli_Dolours_Basilica");
            basilicaGo.transform.SetParent(parent, false);
            basilicaGo.transform.position = origin;

            // Gothic Cathedral Nave
            var nave = GameObject.CreatePrimitive(PrimitiveType.Cube);
            nave.name = "Basilica_Cathedral_Nave";
            nave.transform.SetParent(basilicaGo.transform, false);
            nave.transform.localPosition = new Vector3(0f, 12f, 0f);
            nave.transform.localScale = new Vector3(26f, 24f, 50f);
            nave.GetComponent<Renderer>().sharedMaterial = concreteMat;

            // Twin Front Belfry Spires (Left & Right)
            for (int side = -1; side <= 1; side += 2)
            {
                var tower = GameObject.CreatePrimitive(PrimitiveType.Cube);
                tower.name = $"Front_Belfry_Tower_{(side < 0 ? "Left" : "Right")}";
                tower.transform.SetParent(basilicaGo.transform, false);
                tower.transform.localPosition = new Vector3(side * 11f, 22f, 24f);
                tower.transform.localScale = new Vector3(6.5f, 44f, 6.5f);
                tower.GetComponent<Renderer>().sharedMaterial = concreteMat;

                var spire = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
                spire.name = $"Belfry_Spire_{(side < 0 ? "Left" : "Right")}";
                spire.transform.SetParent(basilicaGo.transform, false);
                spire.transform.localPosition = new Vector3(side * 11f, 47f, 24f);
                spire.transform.localScale = new Vector3(3.5f, 7f, 3.5f);
                spire.GetComponent<Renderer>().sharedMaterial = brickMat;
            }

            // Towering 260-ft Bible Tower at Rear (Scale.y >= 35m required by validator)
            var bibleTower = GameObject.CreatePrimitive(PrimitiveType.Cube);
            bibleTower.name = "Bible_Tower_260ft";
            bibleTower.transform.SetParent(basilicaGo.transform, false);
            bibleTower.transform.localPosition = new Vector3(0f, 40f, -22f);
            bibleTower.transform.localScale = new Vector3(10f, 80f, 10f);
            bibleTower.GetComponent<Renderer>().sharedMaterial = concreteMat;

            var towerBalf = GameObject.CreatePrimitive(PrimitiveType.Cube);
            towerBalf.name = "Bible_Tower_Observation_Deck";
            towerBalf.transform.SetParent(basilicaGo.transform, false);
            towerBalf.transform.localPosition = new Vector3(0f, 81f, -22f);
            towerBalf.transform.localScale = new Vector3(12f, 2.5f, 12f);
            towerBalf.GetComponent<Renderer>().sharedMaterial = brickMat;
        }

        private static void BuildPaliyekkaraTollPlaza(Transform parent, Vector3 origin, Material concreteMat, Material glassMat)
        {
            var tollGo = new GameObject("Landmark_Paliyekkara_Toll_Plaza");
            tollGo.transform.SetParent(parent, false);
            tollGo.transform.position = origin;

            // Highway Canopy Gantry (Spanning across 4 lanes: width 34m)
            var canopy = GameObject.CreatePrimitive(PrimitiveType.Cube);
            canopy.name = "Toll_Canopy_Truss";
            canopy.transform.SetParent(tollGo.transform, false);
            canopy.transform.localPosition = new Vector3(0f, 7.5f, 0f);
            canopy.transform.localScale = new Vector3(34f, 1.4f, 12f);
            canopy.GetComponent<Renderer>().sharedMaterial = concreteMat;

            // Toll Booth Islands
            float[] islandOffsets = new float[] { -10.5f, -3.5f, 3.5f, 10.5f };
            for (int i = 0; i < islandOffsets.Length; i++)
            {
                float islandX = islandOffsets[i];
                var island = GameObject.CreatePrimitive(PrimitiveType.Cube);
                island.name = $"Toll_Island_{i + 1}";
                island.transform.SetParent(tollGo.transform, false);
                island.transform.localPosition = new Vector3(islandX, 0.25f, 0f);
                island.transform.localScale = new Vector3(2.0f, 0.5f, 14f);
                island.GetComponent<Renderer>().sharedMaterial = concreteMat;

                var booth = GameObject.CreatePrimitive(PrimitiveType.Cube);
                booth.name = $"Toll_Booth_{i + 1}";
                booth.transform.SetParent(tollGo.transform, false);
                booth.transform.localPosition = new Vector3(islandX, 2.4f, 0f);
                booth.transform.localScale = new Vector3(1.6f, 3.8f, 4.2f);
                booth.GetComponent<Renderer>().sharedMaterial = glassMat;
            }
        }

        private static void BuildPeechiDam(Transform parent, Vector3 origin, Material damMat)
        {
            var damGo = new GameObject("Landmark_Peechi_Dam_Structure");
            damGo.transform.SetParent(parent, false);
            damGo.transform.position = origin;

            // Concrete Gravity Dam Wall (110m width, 40m height, 22m thickness)
            var damWall = GameObject.CreatePrimitive(PrimitiveType.Cube);
            damWall.name = "Concrete_Gravity_Dam_Wall";
            damWall.transform.SetParent(damGo.transform, false);
            damWall.transform.localPosition = new Vector3(0f, 20f, 0f);
            damWall.transform.localScale = new Vector3(25f, 40f, 110f);
            damWall.GetComponent<Renderer>().sharedMaterial = damMat;

            // Crest Roadway
            var roadway = GameObject.CreatePrimitive(PrimitiveType.Cube);
            roadway.name = "Dam_Crest_Inspection_Roadway";
            roadway.transform.SetParent(damGo.transform, false);
            roadway.transform.localPosition = new Vector3(0f, 40.5f, 0f);
            roadway.transform.localScale = new Vector3(12f, 1.0f, 112f);
            roadway.GetComponent<Renderer>().sharedMaterial = damMat;

            // Spillway Radial Gates
            for (int g = -1; g <= 1; g++)
            {
                var gate = GameObject.CreatePrimitive(PrimitiveType.Cube);
                gate.name = $"Spillway_Gate_{g + 2}";
                gate.transform.SetParent(damGo.transform, false);
                gate.transform.localPosition = new Vector3(11f, 30f, g * 18f);
                gate.transform.localScale = new Vector3(4f, 12f, 12f);
                gate.GetComponent<Renderer>().sharedMaterial = damMat;
            }
        }

        private static void BuildGuruvayurTemple(Transform parent, Vector3 origin, Material concreteMat, Material goldMat, Material tileMat)
        {
            var templeGo = new GameObject("Landmark_Guruvayur_Sree_Krishna_Temple");
            templeGo.transform.SetParent(parent, false);
            templeGo.transform.position = origin;

            // East Nada Gateway / Chuttambalam
            var chuttambalam = GameObject.CreatePrimitive(PrimitiveType.Cube);
            chuttambalam.name = "Chuttambalam_Outer_Wall";
            chuttambalam.transform.SetParent(templeGo.transform, false);
            chuttambalam.transform.localPosition = new Vector3(0f, 4.5f, 0f);
            chuttambalam.transform.localScale = new Vector3(32f, 9.0f, 40f);
            chuttambalam.GetComponent<Renderer>().sharedMaterial = concreteMat;

            var roof = GameObject.CreatePrimitive(PrimitiveType.Cube);
            roof.name = "Gopuram_Tiled_Roof";
            roof.transform.SetParent(templeGo.transform, false);
            roof.transform.localPosition = new Vector3(0f, 10.5f, 0f);
            roof.transform.localScale = new Vector3(34f, 3.2f, 42f);
            roof.GetComponent<Renderer>().sharedMaterial = tileMat;

            // Gold Dwajasthambam (Temple Flagstaff)
            var flagstaff = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
            flagstaff.name = "Gold_Dwajasthambam";
            flagstaff.transform.SetParent(templeGo.transform, false);
            flagstaff.transform.localPosition = new Vector3(0f, 11.0f, 25f);
            flagstaff.transform.localScale = new Vector3(0.5f, 11.0f, 0.5f);
            flagstaff.GetComponent<Renderer>().sharedMaterial = goldMat;
        }

        private static void BuildTriprayarTemple(Transform parent, Vector3 origin, Material concreteMat, Material tileMat)
        {
            var templeGo = new GameObject("Landmark_Triprayar_Sree_Rama_Temple");
            templeGo.transform.SetParent(parent, false);
            templeGo.transform.position = origin;

            // Circular Srikovil Sanctum
            var sanctum = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
            sanctum.name = "Triprayar_Circular_Sanctum";
            sanctum.transform.SetParent(templeGo.transform, false);
            sanctum.transform.localPosition = new Vector3(0f, 5.0f, 0f);
            sanctum.transform.localScale = new Vector3(18f, 5.0f, 18f);
            sanctum.GetComponent<Renderer>().sharedMaterial = concreteMat;

            var conicalRoof = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
            conicalRoof.name = "Conical_Copper_Roof";
            conicalRoof.transform.SetParent(templeGo.transform, false);
            conicalRoof.transform.localPosition = new Vector3(0f, 11.0f, 0f);
            conicalRoof.transform.localScale = new Vector3(21f, 2.5f, 21f);
            conicalRoof.GetComponent<Renderer>().sharedMaterial = tileMat;
        }
    }
}
