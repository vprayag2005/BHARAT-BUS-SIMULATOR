using System;
using System.Collections.Generic;
using System.IO;
using UnityEngine;
using UnityEditor;
using BharatBusSim.Vehicles;
using BharatBusSim.UI;

namespace BharatBusSim.Editor
{
    public static class EcolineBusFactory
    {
        public const string FbxPath = "Assets/_Project/Vehicles/Models/BUS_ECOLINE.fbx";
        public const string LiveryTexPath = "Assets/_Project/Vehicles/Textures/Ecoline/BODI4.png";
        public const string InteriorTexPath = "Assets/_Project/Vehicles/Textures/Ecoline/INT.png";
        public const string LampsTexPath = "Assets/_Project/Vehicles/Textures/Ecoline/LAMPU ECOLINE.png";
        public const string CockpitTexPath = "Assets/_Project/Vehicles/Textures/Cockpit/T_Cockpit_Dashboard_Gauges.png";

        public struct WheelMeshDef
        {
            public string name;
            public int classificationIndex;
            public bool isLeftSide;
            public bool receivesSteering;
            public Vector3 hubCenter;
        }

        public static GameObject CreateEcolineBus(Vector3 spawnPos, Quaternion spawnRot, bool initializeInPark)
        {
            var busGo = new GameObject("Kerala_Bus_Prototype");
            busGo.tag = "Player";
            busGo.transform.position = spawnPos;
            busGo.transform.rotation = spawnRot;

            var rb = busGo.AddComponent<Rigidbody>();
            rb.mass = 10500f;
            rb.linearDamping = 0.05f;
            rb.angularDamping = 1.0f;
            rb.interpolation = RigidbodyInterpolation.Interpolate;

            var bodyCollider = busGo.AddComponent<BoxCollider>();
            bodyCollider.size = new Vector3(2.50f, 2.70f, 12.0f);
            bodyCollider.center = new Vector3(0f, 1.85f, -0.14f);

            GameObject busModelPrefab = AssetDatabase.LoadAssetAtPath<GameObject>(FbxPath);
            if (busModelPrefab == null)
            {
                throw new FileNotFoundException($"[EcolineBusFactory] Ecoline bus model FBX missing at '{FbxPath}'.");
            }

            GameObject visualsGo = (GameObject)PrefabUtility.InstantiatePrefab(busModelPrefab, busGo.transform);
            visualsGo.name = "KeralaBus12m_Visuals";
            visualsGo.transform.localPosition = Vector3.zero;
            visualsGo.transform.localRotation = Quaternion.identity;
            visualsGo.transform.localScale = Vector3.one;

            // Strip existing colliders and rigidbodies
            foreach (var col in visualsGo.GetComponentsInChildren<Collider>(true))
            {
                UnityEngine.Object.DestroyImmediate(col);
            }
            foreach (var extraRb in visualsGo.GetComponentsInChildren<Rigidbody>(true))
            {
                if (extraRb != rb) UnityEngine.Object.DestroyImmediate(extraRb);
            }

            // Remove non-mesh Blender objects (cameras, lights)
            string[] blenderObjNames = new[] { "Camera", "Lamp", "Lamp.001", "Lamp_001" };
            foreach (var bName in blenderObjNames)
            {
                Transform bT = visualsGo.transform.Find(bName);
                if (bT != null)
                {
                    UnityEngine.Object.DestroyImmediate(bT.gameObject);
                }
            }

            // Setup and apply realistic PBR materials
            SetupMaterials(visualsGo);

            // Separate combined ban/pelek meshes into 6 visual wheels
            var visualWheels = RigEcolineWheels(visualsGo, busGo.transform);

            var busController = busGo.AddComponent<BusController>();
            busController.initializeInPark = initializeInPark;

            // Create 6 physical WheelColliders matching Ecoline geometry with realistic heavy coach spring rates
            // Mounting point at Y = 0.62m with 0.20m suspension travel puts the resting wheel center at exactly Y = 0.52m
            var flCol = CreatePhysicsWheelCollider(busGo, "WheelCollider_FL", new Vector3(-1.05f, 0.62f, 3.49f), 180000f, 18000f);
            var frCol = CreatePhysicsWheelCollider(busGo, "WheelCollider_FR", new Vector3(1.05f, 0.62f, 3.49f), 180000f, 18000f);
            var mlCol = CreatePhysicsWheelCollider(busGo, "WheelCollider_ML", new Vector3(-1.08f, 0.62f, -2.59f), 170000f, 16000f);
            var mrCol = CreatePhysicsWheelCollider(busGo, "WheelCollider_MR", new Vector3(1.08f, 0.62f, -2.59f), 170000f, 16000f);
            var rlCol = CreatePhysicsWheelCollider(busGo, "WheelCollider_RL", new Vector3(-0.81f, 0.62f, -2.59f), 160000f, 15000f);
            var rrCol = CreatePhysicsWheelCollider(busGo, "WheelCollider_RR", new Vector3(0.81f, 0.62f, -2.59f), 160000f, 15000f);

            busController.frontLeftCollider = flCol;
            busController.frontRightCollider = frCol;
            busController.midLeftCollider = mlCol;
            busController.midRightCollider = mrCol;
            busController.rearLeftCollider = rlCol;
            busController.rearRightCollider = rrCol;

            ConfigureBusWheel(busGo, "WheelSync_FL", flCol, visualWheels["Wheel_FL"], receivesSteering: true, isLeftSide: true, new Vector3(-1.05f, 0.52f, 3.49f));
            ConfigureBusWheel(busGo, "WheelSync_FR", frCol, visualWheels["Wheel_FR"], receivesSteering: true, isLeftSide: false, new Vector3(1.05f, 0.52f, 3.49f));
            ConfigureBusWheel(busGo, "WheelSync_ML", mlCol, visualWheels["Wheel_ML"], receivesSteering: false, isLeftSide: true, new Vector3(-1.08f, 0.52f, -2.59f));
            ConfigureBusWheel(busGo, "WheelSync_MR", mrCol, visualWheels["Wheel_MR"], receivesSteering: false, isLeftSide: false, new Vector3(1.08f, 0.52f, -2.59f));
            ConfigureBusWheel(busGo, "WheelSync_RL", rlCol, visualWheels["Wheel_RL"], receivesSteering: false, isLeftSide: true, new Vector3(-0.81f, 0.52f, -2.59f));
            ConfigureBusWheel(busGo, "WheelSync_RR", rrCol, visualWheels["Wheel_RR"], receivesSteering: false, isLeftSide: false, new Vector3(0.81f, 0.52f, -2.59f));

            // Headlamps
            var headlampL = CreateHeadlamp(busGo, "Headlamp_L", new Vector3(-0.95f, 0.85f, 5.85f));
            var headlampR = CreateHeadlamp(busGo, "Headlamp_R", new Vector3(0.95f, 0.85f, 5.85f));
            busController.headlamps = new[] { headlampL, headlampR };

            // Interior Cabin Lighting
            CreateInteriorLighting(busGo);

            // Authentic Kerala Realistic FBX Cockpit (3D Steering Wheel, Dual Dials, Both Doors, Valance)
            CockpitModelSetup.AttachCockpitToBus(busGo, visualsGo);

            // Chase Camera
            var camGo = new GameObject("ChaseCamera");
            camGo.tag = "MainCamera";
            var camera = camGo.AddComponent<Camera>();
            camera.clearFlags = CameraClearFlags.Skybox;
            camGo.AddComponent<AudioListener>();

            var camFollow = camGo.AddComponent<BusCameraController>();
            camFollow.target = busGo.transform;
            camFollow.SetCockpitEyeSettings(2.35f, 0.70f, 4.65f, 6.5f);
            camFollow.SnapToTarget();

            // HUD
            var hudGo = new GameObject("BusDashboardHUD");
            var hud = hudGo.AddComponent<BusDashboardHUD>();
            hud.busController = busController;
            hud.cameraController = camFollow;

            // EventSystem
            var esGo = new GameObject("EventSystem");
            esGo.AddComponent<UnityEngine.EventSystems.EventSystem>();
            esGo.AddComponent<UnityEngine.InputSystem.UI.InputSystemUIInputModule>();

            return busGo;
        }

        private static void SetupMaterials(GameObject visualsGo)
        {
            Shader litShader = Shader.Find("Universal Render Pipeline/Lit");
            if (litShader == null) litShader = Shader.Find("Standard");

            Texture2D liveryTex = AssetDatabase.LoadAssetAtPath<Texture2D>(LiveryTexPath);
            Texture2D intTex = AssetDatabase.LoadAssetAtPath<Texture2D>(InteriorTexPath);
            Texture2D lampsTex = AssetDatabase.LoadAssetAtPath<Texture2D>(LampsTexPath);

            var liveryMat = new Material(litShader)
            {
                name = "Mat_Ecoline_Livery",
                mainTexture = liveryTex,
                color = Color.white
            };
            liveryMat.SetFloat("_Smoothness", 0.72f);
            liveryMat.SetFloat("_Metallic", 0.05f);

            var intMat = new Material(litShader)
            {
                name = "Mat_Ecoline_Interior",
                mainTexture = intTex,
                color = Color.white
            };
            intMat.SetFloat("_Smoothness", 0.25f);
            intMat.SetFloat("_Metallic", 0.0f);

            var lampsMat = new Material(litShader)
            {
                name = "Mat_Ecoline_Lamps",
                mainTexture = lampsTex,
                color = Color.white
            };
            lampsMat.SetFloat("_Smoothness", 0.88f);
            lampsMat.SetFloat("_Metallic", 0.15f);

            var glassMat = new Material(litShader)
            {
                name = "Mat_Ecoline_Glass",
                color = new Color(0.10f, 0.18f, 0.26f, 0.35f)
            };
            glassMat.SetFloat("_Smoothness", 0.95f);
            glassMat.SetFloat("_Surface", 1.0f); // Transparent in URP
            glassMat.SetFloat("_Blend", 0.0f);   // Alpha
            glassMat.SetFloat("_SrcBlend", 5.0f);
            glassMat.SetFloat("_DstBlend", 10.0f);
            glassMat.SetFloat("_SrcBlendAlpha", 1.0f);
            glassMat.SetFloat("_DstBlendAlpha", 10.0f);
            glassMat.SetFloat("_ZWrite", 0.0f);
            glassMat.SetOverrideTag("RenderType", "Transparent");
            glassMat.renderQueue = (int)UnityEngine.Rendering.RenderQueue.Transparent;
            glassMat.EnableKeyword("_SURFACE_TYPE_TRANSPARENT");

            var chromeMat = new Material(litShader)
            {
                name = "Mat_Ecoline_Chrome",
                color = new Color(0.92f, 0.92f, 0.92f)
            };
            chromeMat.SetFloat("_Metallic", 0.85f);
            chromeMat.SetFloat("_Smoothness", 0.90f);

            var darkTrimMat = new Material(litShader)
            {
                name = "Mat_Ecoline_DarkTrim",
                color = new Color(0.08f, 0.08f, 0.08f)
            };
            darkTrimMat.SetFloat("_Smoothness", 0.30f);

            var underbodyMat = new Material(litShader)
            {
                name = "Mat_Ecoline_Underbody",
                color = new Color(0.14f, 0.14f, 0.15f)
            };
            underbodyMat.SetFloat("_Smoothness", 0.20f);

            // Assign materials by child GameObject name
            Dictionary<string, Material> assignmentMap = new Dictionary<string, Material>
            {
                { "ac", liveryMat },
                { "apion", liveryMat },
                { "bodi", liveryMat },
                { "sayap", liveryMat },
                { "tapasap", liveryMat },

                { "daleman", intMat },
                { "dasbor", intMat },
                { "dek", intMat },
                { "gorden", intMat },
                { "kursi", intMat },
                { "kursi_kernet", intMat },
                { "kursi_supir", intMat },
                { "lantai", intMat },
                { "skat", intMat },
                { "wc", intMat },

                { "lampu_blkng", lampsMat },
                { "lampu_depan", lampsMat },
                { "lampu_sein", lampsMat },

                { "kaca", glassMat },

                { "knalpot", chromeMat },
                { "krum", chromeMat },

                { "hitam", darkTrimMat },
                { "krum_hitam", darkTrimMat },
                { "lis", darkTrimMat },
                { "slap", darkTrimMat },
                { "slebor", darkTrimMat },
                { "tangga", darkTrimMat },
                { "wipper", darkTrimMat },

                { "bokong_hitam", underbodyMat },
                { "kumis", underbodyMat },
                { "kumis2", underbodyMat },
                { "plat", underbodyMat }
            };

            foreach (var kvp in assignmentMap)
            {
                Transform t = visualsGo.transform.Find(kvp.Key);
                if (t != null)
                {
                    var mr = t.GetComponent<MeshRenderer>();
                    if (mr != null)
                    {
                        mr.sharedMaterial = kvp.Value;
                    }
                }
            }
        }

        private static Dictionary<string, Transform> RigEcolineWheels(GameObject visualsGo, Transform busTransform)
        {
            Shader litShader = Shader.Find("Universal Render Pipeline/Lit");
            if (litShader == null) litShader = Shader.Find("Standard");

            var tireMat = new Material(litShader)
            {
                name = "Mat_Ecoline_Tire",
                color = new Color(0.12f, 0.12f, 0.12f)
            };
            tireMat.SetFloat("_Smoothness", 0.25f);

            var rimMat = new Material(litShader)
            {
                name = "Mat_Ecoline_Rim",
                color = new Color(0.88f, 0.88f, 0.90f)
            };
            rimMat.SetFloat("_Metallic", 0.70f);
            rimMat.SetFloat("_Smoothness", 0.80f);

            Transform banT = visualsGo.transform.Find("ban");
            Transform pelekT = visualsGo.transform.Find("pelek");

            if (banT == null || pelekT == null)
            {
                throw new MissingComponentException("[EcolineBusFactory] 'ban' or 'pelek' mesh missing from Ecoline model!");
            }

            Mesh banMesh = banT.GetComponent<MeshFilter>().sharedMesh;
            Mesh pelekMesh = pelekT.GetComponent<MeshFilter>().sharedMesh;

            WheelMeshDef[] wheelDefs = new WheelMeshDef[]
            {
                new WheelMeshDef { name = "Wheel_FL", classificationIndex = 0, isLeftSide = true,  receivesSteering = true,  hubCenter = new Vector3(-1.05f, 0.52f, 3.49f) },
                new WheelMeshDef { name = "Wheel_FR", classificationIndex = 1, isLeftSide = false, receivesSteering = true,  hubCenter = new Vector3(1.05f, 0.52f, 3.49f) },
                new WheelMeshDef { name = "Wheel_ML", classificationIndex = 2, isLeftSide = true,  receivesSteering = false, hubCenter = new Vector3(-1.08f, 0.52f, -2.59f) },
                new WheelMeshDef { name = "Wheel_RL", classificationIndex = 3, isLeftSide = true,  receivesSteering = false, hubCenter = new Vector3(-0.81f, 0.52f, -2.59f) },
                new WheelMeshDef { name = "Wheel_MR", classificationIndex = 4, isLeftSide = false, receivesSteering = false, hubCenter = new Vector3(1.08f, 0.52f, -2.59f) },
                new WheelMeshDef { name = "Wheel_RR", classificationIndex = 5, isLeftSide = false, receivesSteering = false, hubCenter = new Vector3(0.81f, 0.52f, -2.59f) }
            };

            var result = new Dictionary<string, Transform>();

            foreach (var def in wheelDefs)
            {
                GameObject wheelGo = new GameObject(def.name);
                wheelGo.transform.SetParent(visualsGo.transform, false);
                wheelGo.transform.localPosition = def.hubCenter;
                wheelGo.transform.localRotation = Quaternion.identity;
                wheelGo.transform.localScale = Vector3.one;

                Mesh wheelMesh = BuildSubWheelMesh(busTransform, banMesh, banT, pelekMesh, pelekT, def.classificationIndex, def.isLeftSide, def.hubCenter, out bool hasRim);

                var mf = wheelGo.AddComponent<MeshFilter>();
                mf.sharedMesh = wheelMesh;

                var mr = wheelGo.AddComponent<MeshRenderer>();
                if (hasRim)
                {
                    mr.sharedMaterials = new Material[] { tireMat, rimMat };
                }
                else
                {
                    mr.sharedMaterials = new Material[] { tireMat };
                }

                result[def.name] = wheelGo.transform;
            }

            // Hide original static combined meshes
            banT.gameObject.SetActive(false);
            pelekT.gameObject.SetActive(false);

            return result;
        }

        private static Mesh BuildSubWheelMesh(Transform busTransform, Mesh banMesh, Transform banT, Mesh pelekMesh, Transform pelekT, int classIdx, bool isLeftSide, Vector3 hubCenter, out bool hasRim)
        {
            List<Vector3> vertices = new List<Vector3>();
            List<Vector3> normals = new List<Vector3>();
            List<Vector2> uvs = new List<Vector2>();
            List<int> tireTris = new List<int>();
            List<int> rimTris = new List<int>();

            Quaternion leftSidePreRotation = Quaternion.Euler(0f, 180f, 0f);

            // 1. Extract Tire Triangles
            ExtractTrianglesForWheel(busTransform, banMesh, banT, classIdx, isLeftSide, hubCenter, leftSidePreRotation, vertices, normals, uvs, tireTris);

            // 2. Extract Rim Triangles
            ExtractTrianglesForWheel(busTransform, pelekMesh, pelekT, classIdx, isLeftSide, hubCenter, leftSidePreRotation, vertices, normals, uvs, rimTris);

            Mesh mesh = new Mesh();
            mesh.name = $"Mesh_Wheel_{classIdx}";
            mesh.SetVertices(vertices);
            mesh.SetNormals(normals);
            mesh.SetUVs(0, uvs);

            if (rimTris.Count > 0)
            {
                mesh.subMeshCount = 2;
                mesh.SetTriangles(tireTris, 0);
                mesh.SetTriangles(rimTris, 1);
                hasRim = true;
            }
            else
            {
                mesh.subMeshCount = 1;
                mesh.SetTriangles(tireTris, 0);
                hasRim = false;
            }

            mesh.RecalculateBounds();
            return mesh;
        }

        public static int ClassifyCentroid(Vector3 c)
        {
            if (c.z > 0f)
            {
                // Front axle
                return (c.x < 0f) ? 0 : 1; // 0=FL, 1=FR
            }
            else
            {
                // Rear axle
                if (c.x < 0f)
                {
                    // Left rear: outer vs inner
                    return (c.x < -1.02f) ? 2 : 3; // 2=ML (outer left), 3=RL (inner left)
                }
                else
                {
                    // Right rear: outer vs inner
                    return (c.x > 1.02f) ? 4 : 5; // 4=MR (outer right), 5=RR (inner right)
                }
            }
        }

        private static void ExtractTrianglesForWheel(Transform busTransform, Mesh sourceMesh, Transform sourceT, int classIdx, bool isLeftSide, Vector3 hubCenter, Quaternion leftSidePreRot,
            List<Vector3> outVerts, List<Vector3> outNormals, List<Vector2> outUVs, List<int> outTris)
        {
            Vector3[] srcVerts = sourceMesh.vertices;
            Vector3[] srcNormals = sourceMesh.normals;
            Vector2[] srcUVs = sourceMesh.uv;
            int[] srcTris = sourceMesh.triangles;
            int triCount = srcTris.Length / 3;

            Dictionary<int, int> srcToOutIndex = new Dictionary<int, int>();

            for (int i = 0; i < triCount; i++)
            {
                int i0 = srcTris[i * 3];
                int i1 = srcTris[i * 3 + 1];
                int i2 = srcTris[i * 3 + 2];

                Vector3 v0 = busTransform.InverseTransformPoint(sourceT.TransformPoint(srcVerts[i0]));
                Vector3 v1 = busTransform.InverseTransformPoint(sourceT.TransformPoint(srcVerts[i1]));
                Vector3 v2 = busTransform.InverseTransformPoint(sourceT.TransformPoint(srcVerts[i2]));
                Vector3 centroid = (v0 + v1 + v2) / 3f;

                if (ClassifyCentroid(centroid) == classIdx)
                {
                    int o0 = GetOrCreateVertex(busTransform, i0, srcVerts[i0], srcNormals[i0], (srcUVs != null && srcUVs.Length > i0) ? srcUVs[i0] : Vector2.zero, sourceT, isLeftSide, hubCenter, leftSidePreRot, srcToOutIndex, outVerts, outNormals, outUVs);
                    int o1 = GetOrCreateVertex(busTransform, i1, srcVerts[i1], srcNormals[i1], (srcUVs != null && srcUVs.Length > i1) ? srcUVs[i1] : Vector2.zero, sourceT, isLeftSide, hubCenter, leftSidePreRot, srcToOutIndex, outVerts, outNormals, outUVs);
                    int o2 = GetOrCreateVertex(busTransform, i2, srcVerts[i2], srcNormals[i2], (srcUVs != null && srcUVs.Length > i2) ? srcUVs[i2] : Vector2.zero, sourceT, isLeftSide, hubCenter, leftSidePreRot, srcToOutIndex, outVerts, outNormals, outUVs);

                    outTris.Add(o0);
                    outTris.Add(o1);
                    outTris.Add(o2);
                }
            }
        }

        private static int GetOrCreateVertex(Transform busTransform, int srcIdx, Vector3 posLocal, Vector3 normLocal, Vector2 uv, Transform srcT, bool isLeftSide, Vector3 hubCenter, Quaternion leftSidePreRot,
            Dictionary<int, int> map, List<Vector3> outVerts, List<Vector3> outNormals, List<Vector2> outUVs)
        {
            if (map.TryGetValue(srcIdx, out int existing))
            {
                return existing;
            }

            Vector3 busPos = busTransform.InverseTransformPoint(srcT.TransformPoint(posLocal));
            Vector3 busNorm = busTransform.InverseTransformDirection(srcT.TransformDirection(normLocal));

            Vector3 wheelPos;
            Vector3 wheelNorm;

            if (isLeftSide)
            {
                wheelPos = leftSidePreRot * (busPos - hubCenter);
                wheelNorm = leftSidePreRot * busNorm;
            }
            else
            {
                wheelPos = busPos - hubCenter;
                wheelNorm = busNorm;
            }

            int newIdx = outVerts.Count;
            outVerts.Add(wheelPos);
            outNormals.Add(wheelNorm);
            outUVs.Add(uv);

            map[srcIdx] = newIdx;
            return newIdx;
        }

        private static WheelCollider CreatePhysicsWheelCollider(GameObject bus, string name, Vector3 localPos, float springRate = 175000f, float damperRate = 16000f)
        {
            var wheelGo = new GameObject(name);
            wheelGo.transform.SetParent(bus.transform, false);
            wheelGo.transform.localPosition = localPos;

            var col = wheelGo.AddComponent<WheelCollider>();
            col.radius = 0.52f;
            col.wheelDampingRate = 0.5f;
            col.suspensionDistance = 0.20f;

            JointSpring spring = new JointSpring
            {
                spring = springRate,
                damper = damperRate,
                targetPosition = 0.5f
            };
            col.suspensionSpring = spring;

            WheelFrictionCurve fFriction = col.forwardFriction;
            fFriction.stiffness = 1.5f;
            fFriction.extremumSlip = 0.4f;
            fFriction.extremumValue = 1.0f;
            fFriction.asymptoteSlip = 0.8f;
            fFriction.asymptoteValue = 0.65f;
            col.forwardFriction = fFriction;

            WheelFrictionCurve sFriction = col.sidewaysFriction;
            sFriction.stiffness = 1.8f;
            sFriction.extremumSlip = 0.35f;
            sFriction.extremumValue = 1.0f;
            sFriction.asymptoteSlip = 0.7f;
            sFriction.asymptoteValue = 0.6f;
            col.sidewaysFriction = sFriction;

            return col;
        }

        private static void ConfigureBusWheel(GameObject bus, string name, WheelCollider col, Transform visualWheel, bool receivesSteering, bool isLeftSide, Vector3 restPos)
        {
            var syncGo = new GameObject(name);
            syncGo.transform.SetParent(bus.transform, false);
            syncGo.transform.localPosition = restPos;

            var sync = syncGo.AddComponent<BusWheel>();
            sync.wheelCollider = col;
            sync.visualTransform = visualWheel;
            sync.receivesSteering = receivesSteering;
            sync.isLeftSide = isLeftSide;
            sync.useExplicitRestPosition = true;
            sync.explicitRestPosition = restPos;
        }

        private static Light CreateHeadlamp(GameObject bus, string name, Vector3 localPos)
        {
            var lampGo = new GameObject(name);
            lampGo.transform.SetParent(bus.transform, false);
            lampGo.transform.localPosition = localPos;
            lampGo.transform.localRotation = Quaternion.identity;

            var light = lampGo.AddComponent<Light>();
            light.type = LightType.Spot;
            light.color = new Color(1.0f, 0.95f, 0.85f);
            light.range = 50f;
            light.spotAngle = 65f;
            light.intensity = 2.5f;
            light.shadows = LightShadows.None;
            light.enabled = false;

            return light;
        }

        private static void CreateInteriorLighting(GameObject bus)
        {
            Transform existing = bus.transform.Find("InteriorCabinLights");
            if (existing != null) UnityEngine.Object.DestroyImmediate(existing.gameObject);

            var interiorLightingGo = new GameObject("InteriorCabinLights");
            interiorLightingGo.transform.SetParent(bus.transform, false);

            float[] zPositions = new float[] { 2.5f, 0.0f, -2.5f, -4.5f };
            for (int i = 0; i < zPositions.Length; i++)
            {
                var lightGo = new GameObject($"CabinCeilingLight_{i + 1}");
                lightGo.transform.SetParent(interiorLightingGo.transform, false);
                lightGo.transform.localPosition = new Vector3(0f, 2.75f, zPositions[i]);

                var light = lightGo.AddComponent<Light>();
                light.type = LightType.Point;
                light.color = new Color(1.0f, 0.94f, 0.86f);
                light.range = 5.0f;
                light.intensity = 1.8f;
                light.shadows = LightShadows.None;
            }
        }

        public static GameObject CreateRhdCockpit(GameObject busGo, GameObject visualsGo)
        {
            var realisticCockpit = CockpitModelSetup.AttachCockpitToBus(busGo, visualsGo);
            if (realisticCockpit != null)
            {
                return realisticCockpit;
            }

            Transform existing = visualsGo.transform.Find("RHD_DriverCockpit");
            if (existing != null) UnityEngine.Object.DestroyImmediate(existing.gameObject);

            var cockpitGo = new GameObject("RHD_DriverCockpit");
            cockpitGo.transform.SetParent(visualsGo.transform, false);
            cockpitGo.transform.localPosition = Vector3.zero;
            cockpitGo.transform.localRotation = Quaternion.identity;

            Shader litShader = Shader.Find("Universal Render Pipeline/Lit");
            if (litShader == null) litShader = Shader.Find("Standard");

            Texture2D cockpitTex = AssetDatabase.LoadAssetAtPath<Texture2D>(CockpitTexPath);
            if (cockpitTex != null)
            {
                cockpitTex.filterMode = FilterMode.Trilinear;
                cockpitTex.anisoLevel = 16;
            }

            var matDashboard = new Material(litShader)
            {
                name = "Mat_Cockpit_Dashboard",
                mainTexture = cockpitTex,
                color = Color.white
            };
            matDashboard.SetFloat("_Smoothness", 0.40f);
            matDashboard.SetFloat("_Metallic", 0.05f);

            var matNeedleRed = new Material(litShader)
            {
                name = "Mat_Cockpit_NeedleRed",
                color = new Color(1.0f, 0.20f, 0.12f)
            };
            matNeedleRed.SetFloat("_Smoothness", 0.80f);
            if (matNeedleRed.HasProperty("_EmissionColor"))
            {
                matNeedleRed.EnableKeyword("_EMISSION");
                matNeedleRed.SetColor("_EmissionColor", new Color(1.0f, 0.20f, 0.12f) * 1.5f);
            }

            var matNeedleOrange = new Material(litShader)
            {
                name = "Mat_Cockpit_NeedleOrange",
                color = new Color(1.0f, 0.60f, 0.10f)
            };
            matNeedleOrange.SetFloat("_Smoothness", 0.80f);
            if (matNeedleOrange.HasProperty("_EmissionColor"))
            {
                matNeedleOrange.EnableKeyword("_EMISSION");
                matNeedleOrange.SetColor("_EmissionColor", new Color(1.0f, 0.60f, 0.10f) * 1.5f);
            }

            var matWheel = new Material(litShader)
            {
                name = "Mat_Cockpit_SteeringWheel",
                color = new Color(0.12f, 0.13f, 0.14f)
            };
            matWheel.SetFloat("_Smoothness", 0.15f); // Matte anti-glare finish

            // 1. Instrument Binnacle (Angled toward driver eye)
            var binnacleGo = new GameObject("InstrumentBinnacle");
            binnacleGo.transform.SetParent(cockpitGo.transform, false);
            binnacleGo.transform.localPosition = new Vector3(0.68f, 1.44f, 5.24f);
            binnacleGo.transform.localRotation = Quaternion.Euler(-22f, 0f, 0f);

            // Binnacle hood / cowl (Curved dark leather cowl over dials)
            var hoodGo = new GameObject("BinnacleHood");
            hoodGo.transform.SetParent(binnacleGo.transform, false);
            hoodGo.transform.localPosition = new Vector3(0f, 0.13f, 0.04f);
            hoodGo.transform.localRotation = Quaternion.Euler(-35f, 0f, 0f);
            var mfHood = hoodGo.AddComponent<MeshFilter>();
            mfHood.sharedMesh = CreateQuadMesh("Mesh_BinnacleHood", 0.58f, 0.12f, new Rect(0.0f, 0.0f, 0.5f, 0.25f));
            var mrHood = hoodGo.AddComponent<MeshRenderer>();
            mrHood.sharedMaterial = matWheel;

            // Binnacle bezel (Rich Kerala Teak wood backing)
            var bezelGo = new GameObject("BinnacleBezel");
            bezelGo.transform.SetParent(binnacleGo.transform, false);
            bezelGo.transform.localPosition = Vector3.zero;
            bezelGo.transform.localRotation = Quaternion.identity;
            var mfBezel = bezelGo.AddComponent<MeshFilter>();
            mfBezel.sharedMesh = CreateQuadMesh("Mesh_BinnacleBezel", 0.54f, 0.26f, new Rect(0.0f, 0.5f, 0.5f, 0.5f));
            var mrBezel = bezelGo.AddComponent<MeshRenderer>();
            mrBezel.sharedMaterial = matDashboard;

            // Speedometer Gauge (left dial on binnacle)
            var speedoGo = new GameObject("SpeedometerGauge");
            speedoGo.transform.SetParent(binnacleGo.transform, false);
            speedoGo.transform.localPosition = new Vector3(-0.13f, 0f, -0.008f);
            speedoGo.transform.localRotation = Quaternion.identity;
            var mfSpeedo = speedoGo.AddComponent<MeshFilter>();
            mfSpeedo.sharedMesh = CreateQuadMesh("Mesh_SpeedoDial", 0.22f, 0.22f, new Rect(0.5f, 0.75f, 0.25f, 0.25f));
            var mrSpeedo = speedoGo.AddComponent<MeshRenderer>();
            mrSpeedo.sharedMaterial = matDashboard;

            // Speedometer Needle
            var speedoNeedleGo = new GameObject("SpeedometerNeedle");
            speedoNeedleGo.transform.SetParent(speedoGo.transform, false);
            speedoNeedleGo.transform.localPosition = new Vector3(0f, 0f, -0.005f);
            speedoNeedleGo.transform.localRotation = Quaternion.Euler(0f, 0f, 120f); // 0 km/h position
            var mfSpeedoNeedle = speedoNeedleGo.AddComponent<MeshFilter>();
            mfSpeedoNeedle.sharedMesh = CreateNeedleMesh("Mesh_SpeedoNeedle", 0.08f, 0.008f);
            var mrSpeedoNeedle = speedoNeedleGo.AddComponent<MeshRenderer>();
            mrSpeedoNeedle.sharedMaterial = matNeedleRed;

            // Tachometer Gauge (right dial on binnacle)
            var tachGo = new GameObject("TachometerGauge");
            tachGo.transform.SetParent(binnacleGo.transform, false);
            tachGo.transform.localPosition = new Vector3(0.13f, 0f, -0.008f);
            tachGo.transform.localRotation = Quaternion.identity;
            var mfTach = tachGo.AddComponent<MeshFilter>();
            mfTach.sharedMesh = CreateQuadMesh("Mesh_TachDial", 0.22f, 0.22f, new Rect(0.75f, 0.75f, 0.25f, 0.25f));
            var mrTach = tachGo.AddComponent<MeshRenderer>();
            mrTach.sharedMaterial = matDashboard;

            // Tachometer Needle
            var tachNeedleGo = new GameObject("TachometerNeedle");
            tachNeedleGo.transform.SetParent(tachGo.transform, false);
            tachNeedleGo.transform.localPosition = new Vector3(0f, 0f, -0.005f);
            tachNeedleGo.transform.localRotation = Quaternion.Euler(0f, 0f, 120f); // 0 RPM position
            var mfTachNeedle = tachNeedleGo.AddComponent<MeshFilter>();
            mfTachNeedle.sharedMesh = CreateNeedleMesh("Mesh_TachNeedle", 0.08f, 0.008f);
            var mrTachNeedle = tachNeedleGo.AddComponent<MeshRenderer>();
            mrTachNeedle.sharedMaterial = matNeedleOrange;

            // 3. GPS Navigation Screen (to the left of instrument cluster, angled towards driver)
            var gpsGo = new GameObject("GPS_NavigationScreen");
            gpsGo.transform.SetParent(cockpitGo.transform, false);
            gpsGo.transform.localPosition = new Vector3(0.24f, 1.42f, 5.28f);
            gpsGo.transform.localRotation = Quaternion.Euler(-15f, 18f, 0f);
            var mfGps = gpsGo.AddComponent<MeshFilter>();
            mfGps.sharedMesh = CreateQuadMesh("Mesh_GpsScreen", 0.32f, 0.20f, new Rect(0.5f, 0.5f, 0.5f, 0.25f));
            var mrGps = gpsGo.AddComponent<MeshRenderer>();
            mrGps.sharedMaterial = matDashboard;

            // 4. Switch Console & Air Louvers
            var swGo = new GameObject("SwitchConsole");
            swGo.transform.SetParent(cockpitGo.transform, false);
            swGo.transform.localPosition = new Vector3(0.24f, 1.20f, 5.22f);
            swGo.transform.localRotation = Quaternion.Euler(-10f, 18f, 0f);
            var mfSw = swGo.AddComponent<MeshFilter>();
            mfSw.sharedMesh = CreateQuadMesh("Mesh_SwitchConsole", 0.32f, 0.20f, new Rect(0.0f, 0.25f, 0.5f, 0.25f));
            var mrSw = swGo.AddComponent<MeshRenderer>();
            mrSw.sharedMaterial = matDashboard;

            // 5. Steering Column & 3D Steering Wheel
            var columnGo = new GameObject("SteeringColumn");
            columnGo.transform.SetParent(cockpitGo.transform, false);
            columnGo.transform.localPosition = new Vector3(0.68f, 1.08f, 5.02f);
            columnGo.transform.localRotation = Quaternion.Euler(24f, 0f, 0f);
            var mfCol = columnGo.AddComponent<MeshFilter>();
            mfCol.sharedMesh = CreateBoxMesh("Mesh_SteeringColumn", new Vector3(0.10f, 0.50f, 0.10f),
                new Rect(0.0f, 0.0f, 0.5f, 0.25f),
                new Rect(0.0f, 0.0f, 0.5f, 0.25f));
            var mrCol = columnGo.AddComponent<MeshRenderer>();
            mrCol.sharedMaterial = matWheel;

            // Steering Wheel Hub (pivots dynamically with steering)
            var wheelHubGo = new GameObject("SteeringWheel_3D");
            wheelHubGo.transform.SetParent(cockpitGo.transform, false);
            wheelHubGo.transform.localPosition = new Vector3(0.68f, 1.32f, 4.94f);
            wheelHubGo.transform.localRotation = Quaternion.Euler(-24f, 0f, 0f);

            // Wheel Rim & Spokes
            var rimGo = new GameObject("WheelRimAndSpokes");
            rimGo.transform.SetParent(wheelHubGo.transform, false);
            rimGo.transform.localPosition = Vector3.zero;
            rimGo.transform.localRotation = Quaternion.identity;
            var mfRim = rimGo.AddComponent<MeshFilter>();
            mfRim.sharedMesh = CreateSteeringWheelMesh("Mesh_SteeringWheelRim", 0.25f, 0.018f, 24);
            var mrRim = rimGo.AddComponent<MeshRenderer>();
            mrRim.sharedMaterial = matWheel;

            // Center Horn Pad with Gold Crest
            var hornPadGo = new GameObject("HornPadEmblem");
            hornPadGo.transform.SetParent(wheelHubGo.transform, false);
            hornPadGo.transform.localPosition = new Vector3(0f, 0f, -0.015f);
            hornPadGo.transform.localRotation = Quaternion.identity;
            var mfHorn = hornPadGo.AddComponent<MeshFilter>();
            mfHorn.sharedMesh = CreateQuadMesh("Mesh_HornEmblem", 0.14f, 0.14f, new Rect(0.5f, 0.0f, 0.25f, 0.25f));
            var mrHorn = hornPadGo.AddComponent<MeshRenderer>();
            mrHorn.sharedMaterial = matDashboard;

            // 6. Cockpit Ambient Light (Soft illumination on dashboard)
            var dashLightGo = new GameObject("DashboardBacklight");
            dashLightGo.transform.SetParent(cockpitGo.transform, false);
            dashLightGo.transform.localPosition = new Vector3(0.68f, 1.60f, 4.90f);
            var dashLight = dashLightGo.AddComponent<Light>();
            dashLight.type = LightType.Point;
            dashLight.color = new Color(1.0f, 0.96f, 0.88f);
            dashLight.range = 1.8f;
            dashLight.intensity = 1.20f;
            dashLight.shadows = LightShadows.None;

            // 7. Wire Up CockpitDashboardSync
            var sync = cockpitGo.AddComponent<CockpitDashboardSync>();
            sync.busController = busGo.GetComponent<BusController>();
            sync.steeringWheel = wheelHubGo.transform;
            sync.speedoNeedle = speedoNeedleGo.transform;
            sync.tachNeedle = tachNeedleGo.transform;
            sync.speedoMinAngle = 120f;
            sync.speedoMaxAngle = -120f;
            sync.tachMinAngle = 120f;
            sync.tachMaxAngle = -120f;
            sync.maxSteerWheelRotation = 450f;

            return cockpitGo;
        }

        private static Mesh CreateQuadMesh(string name, float width, float height, Rect uvRect)
        {
            var mesh = new Mesh { name = name };
            float hw = width * 0.5f;
            float hh = height * 0.5f;

            Vector3[] vertices = new Vector3[]
            {
                new Vector3(-hw, -hh, 0f),
                new Vector3( hw, -hh, 0f),
                new Vector3( hw,  hh, 0f),
                new Vector3(-hw,  hh, 0f)
            };

            Vector2[] uvs = new Vector2[]
            {
                new Vector2(uvRect.xMin, uvRect.yMin),
                new Vector2(uvRect.xMax, uvRect.yMin),
                new Vector2(uvRect.xMax, uvRect.yMax),
                new Vector2(uvRect.xMin, uvRect.yMax)
            };

            Vector3[] normals = new Vector3[]
            {
                new Vector3(0f, 0f, -1f),
                new Vector3(0f, 0f, -1f),
                new Vector3(0f, 0f, -1f),
                new Vector3(0f, 0f, -1f)
            };

            int[] triangles = new int[]
            {
                0, 1, 2,  0, 2, 3,
                0, 2, 1,  0, 3, 2
            };

            mesh.vertices = vertices;
            mesh.uv = uvs;
            mesh.normals = normals;
            mesh.triangles = triangles;
            mesh.RecalculateBounds();
            return mesh;
        }

        private static Mesh CreateNeedleMesh(string name, float length, float width)
        {
            var mesh = new Mesh { name = name };
            float hw = width * 0.5f;

            Vector3[] vertices = new Vector3[]
            {
                new Vector3(-hw, -length * 0.15f, 0f),
                new Vector3( hw, -length * 0.15f, 0f),
                new Vector3(0f, length, 0f)
            };

            Vector3[] normals = new Vector3[]
            {
                new Vector3(0f, 0f, -1f),
                new Vector3(0f, 0f, -1f),
                new Vector3(0f, 0f, -1f)
            };

            int[] triangles = new int[]
            {
                0, 1, 2,
                0, 2, 1
            };

            mesh.vertices = vertices;
            mesh.normals = normals;
            mesh.triangles = triangles;
            mesh.RecalculateBounds();
            return mesh;
        }

        private static Mesh CreateBoxMesh(string name, Vector3 size, Rect frontUv, Rect sideUv)
        {
            var mesh = new Mesh { name = name };
            float hx = size.x * 0.5f;
            float hy = size.y * 0.5f;
            float hz = size.z * 0.5f;

            List<Vector3> verts = new List<Vector3>();
            List<Vector3> norms = new List<Vector3>();
            List<Vector2> uvs = new List<Vector2>();
            List<int> tris = new List<int>();

            void AddFace(Vector3 p0, Vector3 p1, Vector3 p2, Vector3 p3, Vector3 norm, Rect uv)
            {
                int baseIdx = verts.Count;
                verts.Add(p0); verts.Add(p1); verts.Add(p2); verts.Add(p3);
                norms.Add(norm); norms.Add(norm); norms.Add(norm); norms.Add(norm);
                uvs.Add(new Vector2(uv.xMin, uv.yMin));
                uvs.Add(new Vector2(uv.xMax, uv.yMin));
                uvs.Add(new Vector2(uv.xMax, uv.yMax));
                uvs.Add(new Vector2(uv.xMin, uv.yMax));
                tris.Add(baseIdx); tris.Add(baseIdx + 1); tris.Add(baseIdx + 2);
                tris.Add(baseIdx); tris.Add(baseIdx + 2); tris.Add(baseIdx + 3);
            }

            // Front face (towards driver / -Z)
            AddFace(new Vector3(-hx, -hy, -hz), new Vector3(hx, -hy, -hz), new Vector3(hx, hy, -hz), new Vector3(-hx, hy, -hz), Vector3.back, frontUv);
            // Top face (+Y)
            AddFace(new Vector3(-hx, hy, -hz), new Vector3(hx, hy, -hz), new Vector3(hx, hy, hz), new Vector3(-hx, hy, hz), Vector3.up, frontUv);
            // Back face (+Z)
            AddFace(new Vector3(hx, -hy, hz), new Vector3(-hx, -hy, hz), new Vector3(-hx, hy, hz), new Vector3(hx, hy, hz), Vector3.forward, sideUv);
            // Bottom face (-Y)
            AddFace(new Vector3(-hx, -hy, hz), new Vector3(hx, -hy, hz), new Vector3(hx, -hy, -hz), new Vector3(-hx, -hy, -hz), Vector3.down, sideUv);
            // Left face (-X)
            AddFace(new Vector3(-hx, -hy, hz), new Vector3(-hx, -hy, -hz), new Vector3(-hx, hy, -hz), new Vector3(-hx, hy, hz), Vector3.left, sideUv);
            // Right face (+X)
            AddFace(new Vector3(hx, -hy, -hz), new Vector3(hx, -hy, hz), new Vector3(hx, hy, hz), new Vector3(hx, hy, -hz), Vector3.right, sideUv);

            mesh.SetVertices(verts);
            mesh.SetNormals(norms);
            mesh.SetUVs(0, uvs);
            mesh.SetTriangles(tris, 0);
            mesh.RecalculateBounds();
            return mesh;
        }

        private static Mesh CreateSteeringWheelMesh(string name, float outerRadius, float tubeRadius, int segments)
        {
            var mesh = new Mesh { name = name };
            List<Vector3> verts = new List<Vector3>();
            List<Vector3> norms = new List<Vector3>();
            List<int> tris = new List<int>();

            int minorSegs = 8;
            for (int i = 0; i < segments; i++)
            {
                float theta1 = (float)i / segments * Mathf.PI * 2f;
                float theta2 = (float)(i + 1) / segments * Mathf.PI * 2f;

                for (int j = 0; j < minorSegs; j++)
                {
                    float phi1 = (float)j / minorSegs * Mathf.PI * 2f;
                    float phi2 = (float)(j + 1) / minorSegs * Mathf.PI * 2f;

                    Vector3 SampleTorus(float theta, float phi)
                    {
                        float r = outerRadius + tubeRadius * Mathf.Cos(phi);
                        return new Vector3(r * Mathf.Cos(theta), r * Mathf.Sin(theta), tubeRadius * Mathf.Sin(phi));
                    }

                    Vector3 p0 = SampleTorus(theta1, phi1);
                    Vector3 p1 = SampleTorus(theta2, phi1);
                    Vector3 p2 = SampleTorus(theta2, phi2);
                    Vector3 p3 = SampleTorus(theta1, phi2);

                    int baseIdx = verts.Count;
                    verts.Add(p0); verts.Add(p1); verts.Add(p2); verts.Add(p3);
                    Vector3 norm = Vector3.Cross(p1 - p0, p3 - p0).normalized;
                    norms.Add(norm); norms.Add(norm); norms.Add(norm); norms.Add(norm);

                    tris.Add(baseIdx); tris.Add(baseIdx + 1); tris.Add(baseIdx + 2);
                    tris.Add(baseIdx); tris.Add(baseIdx + 2); tris.Add(baseIdx + 3);
                    tris.Add(baseIdx); tris.Add(baseIdx + 2); tris.Add(baseIdx + 1);
                    tris.Add(baseIdx); tris.Add(baseIdx + 3); tris.Add(baseIdx + 2);
                }
            }

            // Add 3 Spokes (left: 180°, right: 0°, bottom: 270°)
            float[] spokeAngles = new float[] { 0f, Mathf.PI, -Mathf.PI * 0.5f };
            float spokeWidth = 0.024f;
            float spokeThickness = 0.014f;

            for (int s = 0; s < spokeAngles.Length; s++)
            {
                float ang = spokeAngles[s];
                Vector3 dir = new Vector3(Mathf.Cos(ang), Mathf.Sin(ang), 0f);
                Vector3 perp = new Vector3(-Mathf.Sin(ang), Mathf.Cos(ang), 0f);

                Vector3 startCenter = dir * 0.04f;
                Vector3 endCenter = dir * (outerRadius - tubeRadius * 0.5f);

                Vector3 p0 = startCenter - perp * (spokeWidth * 0.5f) - Vector3.forward * (spokeThickness * 0.5f);
                Vector3 p1 = endCenter - perp * (spokeWidth * 0.5f) - Vector3.forward * (spokeThickness * 0.5f);
                Vector3 p2 = endCenter + perp * (spokeWidth * 0.5f) - Vector3.forward * (spokeThickness * 0.5f);
                Vector3 p3 = startCenter + perp * (spokeWidth * 0.5f) - Vector3.forward * (spokeThickness * 0.5f);

                int baseIdx = verts.Count;
                verts.Add(p0); verts.Add(p1); verts.Add(p2); verts.Add(p3);
                norms.Add(Vector3.back); norms.Add(Vector3.back); norms.Add(Vector3.back); norms.Add(Vector3.back);

                tris.Add(baseIdx); tris.Add(baseIdx + 1); tris.Add(baseIdx + 2);
                tris.Add(baseIdx); tris.Add(baseIdx + 2); tris.Add(baseIdx + 3);
                tris.Add(baseIdx); tris.Add(baseIdx + 2); tris.Add(baseIdx + 1);
                tris.Add(baseIdx); tris.Add(baseIdx + 3); tris.Add(baseIdx + 2);
            }

            mesh.SetVertices(verts);
            mesh.SetNormals(norms);
            mesh.SetTriangles(tris, 0);
            mesh.RecalculateBounds();
            return mesh;
        }

        [MenuItem("BharatBusSim/Setup/Enhance Bus Realism & Lighting")]
        public static void EnhanceThrissurBusRealism()
        {
            string scenePath = "Assets/_Project/Scenes/03_Thrissur_RealisticWorld.unity";
            var scene = UnityEditor.SceneManagement.EditorSceneManager.OpenScene(scenePath, UnityEditor.SceneManagement.OpenSceneMode.Single);

            var busGo = GameObject.Find("Kerala_Bus_Prototype");
            if (busGo != null)
            {
                CreateInteriorLighting(busGo);

                var visualsGo = busGo.transform.Find("KeralaBus12m_Visuals");
                if (visualsGo != null)
                {
                    SetupMaterials(visualsGo.gameObject);
                    CockpitModelSetup.AttachCockpitToBus(busGo, visualsGo.gameObject);
                }

                // Also update ChaseCamera presets to ensure latest settings are saved into scene
                var camGo = GameObject.Find("ChaseCamera");
                if (camGo != null)
                {
                    var camCtrl = camGo.GetComponent<BusCameraController>();
                    if (camCtrl != null)
                    {
                        camCtrl.ResetDefaultPresets();
                        camCtrl.SetCockpitEyeSettings(2.35f, 0.70f, 4.65f, 6.5f);
                        camCtrl.SnapToTarget();
                    }
                }

                UnityEditor.SceneManagement.EditorSceneManager.MarkSceneDirty(scene);
                UnityEditor.SceneManagement.EditorSceneManager.SaveScene(scene);
                Debug.Log("[EcolineBusFactory] Thrissur bus realism, RHD cockpit & interior lighting applied successfully!");
            }
            else
            {
                Debug.LogError("[EcolineBusFactory] Kerala_Bus_Prototype not found in Thrissur scene!");
            }
        }
    }
}
