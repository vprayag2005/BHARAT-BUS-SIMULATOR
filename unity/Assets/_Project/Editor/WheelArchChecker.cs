using System;
using UnityEngine;
using UnityEditor;

namespace BharatBusSim.Editor
{
    public static class WheelArchChecker
    {
        public static void CheckArches()
        {
            string path = "Assets/_Project/Vehicles/Models/BUS_ECOLINE.fbx";
            GameObject prefab = AssetDatabase.LoadAssetAtPath<GameObject>(path);
            GameObject go = (GameObject)PrefabUtility.InstantiatePrefab(prefab);

            Transform sleborT = go.transform.Find("slebor");
            Mesh sleborMesh = sleborT.GetComponent<MeshFilter>().sharedMesh;

            Transform banT = go.transform.Find("ban");
            Mesh banMesh = banT.GetComponent<MeshFilter>().sharedMesh;

            Vector3[] sVerts = sleborMesh.vertices;
            Vector3 sMin = new Vector3(float.MaxValue, float.MaxValue, float.MaxValue);
            Vector3 sMax = new Vector3(float.MinValue, float.MinValue, float.MinValue);
            foreach (var v in sVerts)
            {
                Vector3 w = sleborT.TransformPoint(v);
                sMin = Vector3.Min(sMin, w);
                sMax = Vector3.Max(sMax, w);
            }
            Debug.Log($"Slebor (Wheel Arches) bounds: min={sMin}, max={sMax}");

            // Separate arches into front (Z > 0) and rear (Z < 0)
            Vector3 sFrontMin = new Vector3(float.MaxValue, float.MaxValue, float.MaxValue);
            Vector3 sFrontMax = new Vector3(float.MinValue, float.MinValue, float.MinValue);
            Vector3 sRearMin = new Vector3(float.MaxValue, float.MaxValue, float.MaxValue);
            Vector3 sRearMax = new Vector3(float.MinValue, float.MinValue, float.MinValue);

            foreach (var v in sVerts)
            {
                Vector3 w = sleborT.TransformPoint(v);
                if (w.z > 0)
                {
                    sFrontMin = Vector3.Min(sFrontMin, w);
                    sFrontMax = Vector3.Max(sFrontMax, w);
                }
                else
                {
                    sRearMin = Vector3.Min(sRearMin, w);
                    sRearMax = Vector3.Max(sRearMax, w);
                }
            }
            Debug.Log($"Front Arch: min={sFrontMin}, max={sFrontMax}, center={(sFrontMin+sFrontMax)*0.5f}");
            Debug.Log($"Rear Arch: min={sRearMin}, max={sRearMax}, center={(sRearMin+sRearMax)*0.5f}");

            UnityEngine.Object.DestroyImmediate(go);
        }
    }
}
