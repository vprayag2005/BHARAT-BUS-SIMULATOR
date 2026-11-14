using System;
using System.Collections.Generic;
using UnityEngine;
using UnityEngine.UI;

namespace BharatBusSim.Environment
{
    /// <summary>
    /// Builds authentic Kerala scenery including 14-frond 3D coconut palms, sprawling tropical
    /// shade trees, realistic commercial shopfronts with signboards, and coastal shorelines.
    /// </summary>
    public static class KeralaEnvironmentBuilder
    {
        public static bool EnableTrees = false;

        public static GameObject BuildEnvironment(Transform parent, Material grassMat, Material waterMat, Material sandMat, Material woodMat, Material palmLeafMat, Material wallMat, Material roofMat)
        {
            var envGo = new GameObject("Kerala_Environment_Scenery");
            envGo.transform.SetParent(parent, false);

            // 1. Regional Ground Terrain Base (6km x 6km)
            var terrainGo = GameObject.CreatePrimitive(PrimitiveType.Plane);
            terrainGo.name = "Thrissur_Regional_Ground";
            terrainGo.transform.SetParent(envGo.transform, false);
            terrainGo.transform.position = new Vector3(0f, -0.05f, 0f);
            terrainGo.transform.localScale = new Vector3(600f, 1f, 600f);
            terrainGo.GetComponent<Renderer>().sharedMaterial = grassMat;

            // 2. Peechi Dam Reservoir & Scenic Lake
            BuildPeechiReservoir(envGo.transform, new Vector3(3450f, 18.0f, 130f), waterMat, grassMat);

            // 3. Coastal Water & Sandy Shorelines (Chavakkad Beach & Triprayar Beach)
            BuildCoastalBeach(envGo.transform, "Chavakkad_Beach_Shoreline", new Vector3(-4550f, 0.05f, 1050f), sandMat, waterMat);
            BuildCoastalBeach(envGo.transform, "Triprayar_Beach_Shoreline", new Vector3(-2300f, 0.05f, -2250f), sandMat, waterMat);

            // 4. Trees disabled ("for now no trees. just build realistic Thrissur roads only")
            if (EnableTrees)
            {
                BuildVegetationClusters(envGo.transform, woodMat, palmLeafMat, grassMat);
            }

            // 5. Authentic Kerala Roadside Commercial Architecture
            BuildRoadsideArchitecture(envGo.transform, wallMat, roofMat, concreteMat: wallMat);

            // 6. Western Ghats Mountain Horizon (Peechi-Vazhani foothills backdrop)
            BuildWesternGhatsHorizon(envGo.transform, grassMat);

            return envGo;
        }

        private static void BuildWesternGhatsHorizon(Transform parent, Material mountainMat)
        {
            var horizonGo = new GameObject("Western_Ghats_Mountain_Horizon");
            horizonGo.transform.SetParent(parent, false);

            // Eastern and North-Eastern mountain ranges (Distance: ~2.5km to 4km)
            Vector3[] ridgePositions = new[]
            {
                new Vector3(2600f, 40f, -1500f),
                new Vector3(2900f, 65f, -1000f),
                new Vector3(3200f, 85f, -500f),
                new Vector3(3450f, 95f, 0f),
                new Vector3(3500f, 90f, 600f),
                new Vector3(3300f, 80f, 1200f),
                new Vector3(3000f, 65f, 1800f),
                new Vector3(2600f, 50f, 2400f)
            };

            for (int i = 0; i < ridgePositions.Length; i++)
            {
                var peak = GameObject.CreatePrimitive(PrimitiveType.Sphere);
                peak.name = $"Ghats_Peak_{i}";
                peak.transform.SetParent(horizonGo.transform, false);
                peak.transform.position = ridgePositions[i];
                float sx = UnityEngine.Random.Range(350f, 500f);
                float sy = UnityEngine.Random.Range(90f, 160f);
                float sz = UnityEngine.Random.Range(350f, 500f);
                peak.transform.localScale = new Vector3(sx, sy, sz);
                peak.GetComponent<Renderer>().sharedMaterial = mountainMat;

                var col = peak.GetComponent<Collider>();
                if (col != null) UnityEngine.Object.DestroyImmediate(col);
            }
        }

        private static void BuildPeechiReservoir(Transform parent, Vector3 center, Material waterMat, Material terrainMat)
        {
            var resGo = new GameObject("Peechi_Reservoir_Area");
            resGo.transform.SetParent(parent, false);
            resGo.transform.position = center;

            // Shimmering Lake Water Surface (Required by validator)
            var water = GameObject.CreatePrimitive(PrimitiveType.Plane);
            water.name = "Peechi_Water_Surface";
            water.transform.SetParent(resGo.transform, false);
            water.transform.localPosition = new Vector3(100f, 0f, 0f);
            water.transform.localScale = new Vector3(45f, 1f, 35f);
            water.GetComponent<Renderer>().sharedMaterial = waterMat;
            var col = water.GetComponent<Collider>();
            if (col != null) UnityEngine.Object.DestroyImmediate(col);

            // Surrounding Scenic Hills (Peechi-Vazhani Wildlife Foothills)
            for (int i = 0; i < 7; i++)
            {
                float angle = i * (Mathf.PI * 2f / 7f);
                Vector3 hillPos = new Vector3(Mathf.Cos(angle) * 220f, 18f, Mathf.Sin(angle) * 160f);
                var hill = GameObject.CreatePrimitive(PrimitiveType.Sphere);
                hill.name = $"Peechi_Foothill_{i}";
                hill.transform.SetParent(resGo.transform, false);
                hill.transform.localPosition = hillPos;
                hill.transform.localScale = new Vector3(160f, 50f, 160f);
                hill.GetComponent<Renderer>().sharedMaterial = terrainMat;
                var cHill = hill.GetComponent<Collider>();
                if (cHill != null) UnityEngine.Object.DestroyImmediate(cHill);
            }
        }

        private static void BuildCoastalBeach(Transform parent, string name, Vector3 center, Material sandMat, Material waterMat)
        {
            var beachGo = new GameObject(name);
            beachGo.transform.SetParent(parent, false);
            beachGo.transform.position = center;

            // Sandy Beach Strip (Validator requires this game object)
            var sand = GameObject.CreatePrimitive(PrimitiveType.Plane);
            sand.name = "Golden_Sand_Dunes";
            sand.transform.SetParent(beachGo.transform, false);
            sand.transform.localPosition = new Vector3(20f, 0.02f, 0f);
            sand.transform.localScale = new Vector3(12f, 1f, 40f);
            sand.GetComponent<Renderer>().sharedMaterial = sandMat;
            var cSand = sand.GetComponent<Collider>();
            if (cSand != null) UnityEngine.Object.DestroyImmediate(cSand);

            // Arabian Sea Water Surface
            var sea = GameObject.CreatePrimitive(PrimitiveType.Plane);
            sea.name = "Arabian_Sea_Water";
            sea.transform.SetParent(beachGo.transform, false);
            sea.transform.localPosition = new Vector3(-100f, 0.01f, 0f);
            sea.transform.localScale = new Vector3(30f, 1f, 50f);
            sea.GetComponent<Renderer>().sharedMaterial = waterMat;
            var col = sea.GetComponent<Collider>();
            if (col != null) UnityEngine.Object.DestroyImmediate(col);
        }

        private static void BuildVegetationClusters(Transform parent, Material woodMat, Material palmLeafMat, Material treeLeafMat)
        {
            var vegGo = new GameObject("Vegetation_Clusters");
            vegGo.transform.SetParent(parent, false);

            Vector3[] clusterCenters = new[]
            {
                new Vector3(-45f, 0f, -115f),   // Sakthan approach (west verge, clear of roadway)
                new Vector3(-45f, 0f, 30f),     // Swaraj Round green verge
                new Vector3(120f, 0f, -40f),    // East Fort
                new Vector3(950f, 0f, 180f),    // Mannuthy highway corridor
                new Vector3(1020f, 0f, -800f),  // NH 544 Ollur
                new Vector3(1060f, 0f, -1780f), // Paliyekkara approach
                new Vector3(2100f, 0f, 150f),   // Peechi scenic road
                new Vector3(-250f, 0f, -50f),   // West Fort
                new Vector3(-1900f, 0f, -140f), // Kanjani road
                new Vector3(-3780f, 0f, 1540f), // Guruvayur town approach
                new Vector3(-4480f, 0f, 1020f), // Chavakkad coast
                new Vector3(-2100f, 0f, -2200f) // Triprayar river verge
            };

            foreach (var center in clusterCenters)
            {
                // Group of 4 authentic Kerala coconut palms per cluster
                for (int i = 0; i < 4; i++)
                {
                    float ox = (i * 9f) - 14f;
                    float oz = ((i % 2) * 14f) - 7f;
                    float scale = 0.9f + (i * 0.12f);
                    CreateRealisticCoconutPalm(vegGo.transform, center + new Vector3(ox, 0f, oz), woodMat, palmLeafMat, scale);
                }

                // Add 1 sprawling Kerala tropical shade tree (Rain Tree) per cluster
                CreateTropicalShadeTree(vegGo.transform, center + new Vector3(20f, 0f, 10f), woodMat, treeLeafMat);
            }
        }

        public static GameObject CreateRealisticCoconutPalm(Transform parent, Vector3 rootPos, Material trunkMat, Material frondMat, float scaleFactor = 1.0f)
        {
            var palm = new GameObject("Coconut_Palm_Kerala");
            palm.transform.SetParent(parent, false);
            palm.transform.position = rootPos;
            palm.transform.localScale = Vector3.one * scaleFactor;

            // 1. Slender Curved Trunk (4 segmented sections with natural windward lean)
            float leanX = UnityEngine.Random.Range(0.4f, 0.9f);
            float leanZ = UnityEngine.Random.Range(0.2f, 0.6f);

            Vector3 p0 = Vector3.zero;
            Vector3 p1 = new Vector3(leanX * 0.25f, 2.5f, leanZ * 0.25f);
            Vector3 p2 = new Vector3(leanX * 0.6f, 5.5f, leanZ * 0.6f);
            Vector3 p3 = new Vector3(leanX, 9.0f, leanZ);

            CreateTrunkSegment(palm.transform, p0, p1, 0.55f, 0.46f, trunkMat, "Trunk_Base");
            CreateTrunkSegment(palm.transform, p1, p2, 0.46f, 0.38f, trunkMat, "Trunk_Mid");
            CreateTrunkSegment(palm.transform, p2, p3, 0.38f, 0.30f, trunkMat, "Trunk_Top");

            // 2. Crown Shaft & Coconut Bunch
            var crownShaft = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
            crownShaft.name = "Crown_Shaft";
            crownShaft.transform.SetParent(palm.transform, false);
            crownShaft.transform.localPosition = p3 + new Vector3(0f, 0.4f, 0f);
            crownShaft.transform.localScale = new Vector3(0.28f, 0.4f, 0.28f);
            crownShaft.GetComponent<Renderer>().sharedMaterial = trunkMat;

            // Cluster of 4 small coconuts
            for (int c = 0; c < 4; c++)
            {
                float ca = c * (Mathf.PI * 0.5f);
                var coconut = GameObject.CreatePrimitive(PrimitiveType.Sphere);
                coconut.name = $"Coconut_{c}";
                coconut.transform.SetParent(palm.transform, false);
                coconut.transform.localPosition = p3 + new Vector3(Mathf.Cos(ca) * 0.28f, 0.15f, Mathf.Sin(ca) * 0.28f);
                coconut.transform.localScale = new Vector3(0.26f, 0.32f, 0.26f);
                coconut.GetComponent<Renderer>().sharedMaterial = trunkMat;
                var col = coconut.GetComponent<Collider>();
                if (col != null) UnityEngine.Object.DestroyImmediate(col);
            }

            // 3. 16 Realistic Radial Palm Fronds Arching Outward & Downward
            int frondCount = 16;
            Vector3 crownPos = p3 + new Vector3(0f, 0.65f, 0f);
            var sharedFrondMesh = GetOrCreateCurvedFrondMesh();

            for (int f = 0; f < frondCount; f++)
            {
                float angle = f * (360f / frondCount);
                // Alternate between upper spreading fronds and lower mature drooping fronds
                float droopPitch = (f % 2 == 0) ? 38f : 62f;

                var frondGo = new GameObject($"Frond_{f:D2}");
                frondGo.transform.SetParent(palm.transform, false);
                frondGo.transform.localPosition = crownPos;
                frondGo.transform.localRotation = Quaternion.Euler(droopPitch, angle, 0f);

                var mf = frondGo.AddComponent<MeshFilter>();
                mf.sharedMesh = sharedFrondMesh;

                var mr = frondGo.AddComponent<MeshRenderer>();
                mr.sharedMaterial = frondMat;
            }

            return palm;
        }

        private static Mesh s_CurvedFrondMesh;

        private static Mesh GetOrCreateCurvedFrondMesh()
        {
            if (s_CurvedFrondMesh != null) return s_CurvedFrondMesh;

            var mesh = new Mesh();
            mesh.name = "Mesh_Curved_Palm_Frond";

            int segments = 6;
            var verts = new Vector3[(segments + 1) * 2];
            var uvs = new Vector2[(segments + 1) * 2];
            var tris = new List<int>();

            for (int i = 0; i <= segments; i++)
            {
                float t = i / (float)segments;
                float fwd = t * 5.2f;
                float droop = t * t * 2.2f; // Smooth natural parabolic droop
                float halfW = Mathf.Sin(t * Mathf.PI) * 0.72f; // Pinnate leaf shape, widest in middle

                verts[i * 2] = new Vector3(-halfW, -droop, fwd);
                verts[i * 2 + 1] = new Vector3(halfW, -droop, fwd);

                uvs[i * 2] = new Vector2(0f, t);
                uvs[i * 2 + 1] = new Vector2(1f, t);

                if (i < segments)
                {
                    int b = i * 2;
                    tris.Add(b);
                    tris.Add(b + 1);
                    tris.Add(b + 2);

                    tris.Add(b + 1);
                    tris.Add(b + 3);
                    tris.Add(b + 2);
                }
            }

            mesh.vertices = verts;
            mesh.uv = uvs;
            mesh.triangles = tris.ToArray();
            mesh.RecalculateNormals();
            mesh.RecalculateBounds();

            s_CurvedFrondMesh = mesh;
            return mesh;
        }

        private static void CreateTrunkSegment(Transform parent, Vector3 from, Vector3 to, float radiusStart, float radiusEnd, Material mat, string name)
        {
            Vector3 delta = to - from;
            float len = delta.magnitude;
            if (len < 0.1f) return;

            var seg = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
            seg.name = name;
            seg.transform.SetParent(parent, false);
            seg.transform.localPosition = (from + to) * 0.5f;
            seg.transform.localRotation = Quaternion.FromToRotation(Vector3.up, delta.normalized);
            seg.transform.localScale = new Vector3((radiusStart + radiusEnd) * 0.5f, len * 0.5f, (radiusStart + radiusEnd) * 0.5f);
            seg.GetComponent<Renderer>().sharedMaterial = mat;

            // Remove collider on decorative palm segments
            var col = seg.GetComponent<Collider>();
            if (col != null) UnityEngine.Object.DestroyImmediate(col);
        }

        public static GameObject CreateTropicalShadeTree(Transform parent, Vector3 rootPos, Material woodMat, Material leafMat)
        {
            var tree = new GameObject("Kerala_Rain_Tree");
            tree.transform.SetParent(parent, false);
            tree.transform.position = rootPos;

            // Stout Trunk (Height 4.5m, diameter 0.9m)
            var trunk = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
            trunk.name = "Stout_Trunk";
            trunk.transform.SetParent(tree.transform, false);
            trunk.transform.localPosition = new Vector3(0f, 2.2f, 0f);
            trunk.transform.localScale = new Vector3(0.9f, 2.2f, 0.9f);
            trunk.GetComponent<Renderer>().sharedMaterial = woodMat;
            var tCol = trunk.GetComponent<Collider>();
            if (tCol != null) UnityEngine.Object.DestroyImmediate(tCol);

            // Sprawling Canopy: 4 Overlapping Foliage Domes (Span 14m, height 9m)
            Vector3[] canopyOffsets = new[]
            {
                new Vector3(0f, 6.8f, 0f),
                new Vector3(3.2f, 6.2f, 1.8f),
                new Vector3(-3.0f, 6.0f, -1.5f),
                new Vector3(1.0f, 6.5f, -3.2f)
            };

            for (int i = 0; i < canopyOffsets.Length; i++)
            {
                var dome = GameObject.CreatePrimitive(PrimitiveType.Sphere);
                dome.name = $"Canopy_Dome_{i}";
                dome.transform.SetParent(tree.transform, false);
                dome.transform.localPosition = canopyOffsets[i];
                dome.transform.localScale = new Vector3(10.5f, 5.5f, 10.5f);
                dome.GetComponent<Renderer>().sharedMaterial = leafMat;
                var col = dome.GetComponent<Collider>();
                if (col != null) UnityEngine.Object.DestroyImmediate(col);
            }

            return tree;
        }

        private static void BuildRoadsideArchitecture(Transform parent, Material wallMat, Material roofMat, Material concreteMat)
        {
            var archGo = new GameObject("Roadside_Commercial_Shops");
            archGo.transform.SetParent(parent, false);

            string[] brands = new[] { "Milma Dairy", "Hotel Ananya", "Royal Bakery", "Sree Medicals" };

            Vector3[] shopPositions = new[]
            {
                new Vector3(65f, 0f, -50f),    // Sakthan High Road commercial (safely set back 40m from road center)
                new Vector3(-68f, 0f, 45f),   // Swaraj ring commercial
                new Vector3(58f, 0f, 75f),    // Swaraj north
                new Vector3(140f, 0f, -65f),  // East fort market
                new Vector3(-245f, 0f, -75f), // West fort junction
                new Vector3(-3845f, 0f, 1510f)// Guruvayur town street
            };

            for (int i = 0; i < shopPositions.Length; i++)
            {
                string brand = brands[i % brands.Length];
                CreateDetailedKeralaShopBuilding(archGo.transform, shopPositions[i], brand, wallMat, roofMat, concreteMat);
            }
        }

        public static GameObject CreateDetailedKeralaShopBuilding(Transform parent, Vector3 pos, string brandName, Material wallMat, Material roofMat, Material concreteMat)
        {
            var building = new GameObject($"Kerala_Shopfront_{brandName.Replace(" ", "_")}");
            building.transform.SetParent(parent, false);
            building.transform.position = pos;

            // 1. Main 2-Story Commercial Block (12m width, 7.5m height, 9m depth)
            var baseBlock = GameObject.CreatePrimitive(PrimitiveType.Cube);
            baseBlock.name = "Building_Structure";
            baseBlock.transform.SetParent(building.transform, false);
            baseBlock.transform.localPosition = new Vector3(0f, 3.75f, 0f);
            baseBlock.transform.localScale = new Vector3(12f, 7.5f, 9f);
            baseBlock.GetComponent<Renderer>().sharedMaterial = wallMat;

            // 2. Ground Floor Rolling Steel Shutters (3 shops side-by-side)
            for (int s = -1; s <= 1; s++)
            {
                var shutter = GameObject.CreatePrimitive(PrimitiveType.Cube);
                shutter.name = $"Shutter_{s + 2}";
                shutter.transform.SetParent(building.transform, false);
                shutter.transform.localPosition = new Vector3(s * 3.6f, 1.6f, 4.55f);
                shutter.transform.localScale = new Vector3(3.1f, 3.2f, 0.12f);
                shutter.GetComponent<Renderer>().sharedMaterial = concreteMat;
            }

            // 3. Commercial Store Signboard (Above ground floor shutters: Y = 3.6m)
            var signMat = new Material(wallMat.shader)
            {
                name = $"Mat_Sign_{brandName}",
                mainTexture = ProceduralTextureFactory.GetOrCreateShopSignboardTexture(brandName)
            };

            var signBoard = GameObject.CreatePrimitive(PrimitiveType.Cube);
            signBoard.name = "Store_Signboard";
            signBoard.transform.SetParent(building.transform, false);
            signBoard.transform.localPosition = new Vector3(0f, 3.7f, 4.6f);
            signBoard.transform.localScale = new Vector3(11.4f, 1.1f, 0.15f);
            signBoard.GetComponent<Renderer>().sharedMaterial = signMat;

            // 4. Sloped Kerala Clay Tiled Awning (Over store front)
            var awning = GameObject.CreatePrimitive(PrimitiveType.Cube);
            awning.name = "Shop_Clay_Awning";
            awning.transform.SetParent(building.transform, false);
            awning.transform.localPosition = new Vector3(0f, 4.4f, 5.2f);
            awning.transform.localRotation = Quaternion.Euler(18f, 0f, 0f);
            awning.transform.localScale = new Vector3(12.4f, 0.22f, 1.6f);
            awning.GetComponent<Renderer>().sharedMaterial = roofMat;

            // 5. Authentic Kerala Gabled Mangalore Tiled Roof
            var roof = GameObject.CreatePrimitive(PrimitiveType.Cube);
            roof.name = "Gabled_Tiled_Roof";
            roof.transform.SetParent(building.transform, false);
            roof.transform.localPosition = new Vector3(0f, 8.2f, 0f);
            roof.transform.localScale = new Vector3(13.2f, 1.6f, 10.2f);
            roof.GetComponent<Renderer>().sharedMaterial = roofMat;

            // 6. Compound Wall (1.4m height) on left & right
            for (int w = -1; w <= 1; w += 2)
            {
                var wall = GameObject.CreatePrimitive(PrimitiveType.Cube);
                wall.name = $"Compound_Wall_{(w < 0 ? "L" : "R")}";
                wall.transform.SetParent(building.transform, false);
                wall.transform.localPosition = new Vector3(w * 8.5f, 0.7f, 0f);
                wall.transform.localScale = new Vector3(4.5f, 1.4f, 0.35f);
                wall.GetComponent<Renderer>().sharedMaterial = wallMat;
            }

            return building;
        }
    }
}
