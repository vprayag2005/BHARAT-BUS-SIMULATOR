using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;

namespace BharatBusSim.Editor
{
    /// <summary>
    /// Temporary probe: what is still switched on around the corridor that is not part of the rebuilt
    /// route. The renders show road tiles floating in the fields and a blue sliver beside the Ollur MDR.
    /// </summary>
    public static class NewPackRouteProbe
    {
        private const string ScenePath = "Assets/_Project/Scenes/03_Thrissur_RealisticWorld.unity";
        private const string CorridorName = "Mannuthy_Ollur_Mapped_Complete";
        private const string OutPath = "C:/Project/BHARAT BUS SIMULATOR/artifacts/route_probe.txt";

        private static readonly (float x0, float x1, float z0, float z1)[] Windows =
        {
            (1300f, 1700f, -460f, -50f),    // Mannuthy bend and junction
            (2050f, 2300f, -1700f, -1300f), // Ollur MDR, taper and the highway behind it
        };

        public static void RunFromCommandLine()
        {
            try { Run(); EditorApplication.Exit(0); }
            catch (Exception e) { Debug.LogException(e); EditorApplication.Exit(1); }
        }

        public static void Run()
        {
            EditorSceneManager.OpenScene(ScenePath, OpenSceneMode.Single);
            var corridor = FindCorridor(CorridorName);
            if (corridor == null) { File.WriteAllText(OutPath, "corridor not found"); return; }
            var sb = new StringBuilder();

            var clean = FindDeep(corridor, "Clean_Modular_Route");
            sb.AppendLine("=== GEOMETRY OVERLAPPING THE TWO DEFECT WINDOWS (whole scene) ===");

            var rows = new List<(string key, string line)>();
            int listed = 0;
            foreach (var r in UnityEngine.Object.FindObjectsByType<MeshRenderer>(FindObjectsSortMode.None))
            {
                if (!r.enabled || !r.gameObject.activeInHierarchy) continue;
                var b = r.bounds;
                int hit = -1;
                for (int w = 0; w < Windows.Length; w++)
                {
                    var win = Windows[w];
                    if (b.max.x < win.x0 || b.min.x > win.x1 || b.max.z < win.z0 || b.min.z > win.z1) continue;
                    hit = w; break;
                }
                if (hit < 0) continue;
                string mats = string.Join("+", r.sharedMaterials.Select(m => m == null ? "null" : m.name));
                if (mats.Contains("Palm", StringComparison.OrdinalIgnoreCase)) continue;
                listed++;
                bool generated = clean != null && IsUnder(r.transform, clean);
                rows.Add((Path(r.transform),
                    $"[{hit}] {(generated ? "GEN" : "OUT")} {Path(r.transform)} | mats={mats} | " +
                    $"centre=({b.center.x:F1},{b.center.y:F2},{b.center.z:F1}) size=({b.size.x:F1},{b.size.y:F2},{b.size.z:F1})"));
            }
            foreach (var row in rows.OrderBy(x => x.key)) sb.AppendLine(row.line);
            sb.AppendLine($"listed {listed} renderers overlapping the windows");

            File.WriteAllText(OutPath, sb.ToString());
            Debug.Log("ROUTE_PROBE_DONE");
        }

        private static string Path(Transform t)
        {
            var parts = new List<string>();
            for (var p = t; p != null; p = p.parent) parts.Insert(0, p.name);
            return string.Join("/", parts);
        }

        private static bool IsUnder(Transform t, Transform root)
        {
            for (var p = t; p != null; p = p.parent) if (p == root) return true;
            return false;
        }

        private static Transform FindDeep(Transform root, string name)
        {
            if (root.name == name) return root;
            foreach (Transform child in root)
            {
                var found = FindDeep(child, name);
                if (found != null) return found;
            }
            return null;
        }

        private static Transform FindCorridor(string name)
        {
            foreach (var root in UnityEngine.SceneManagement.SceneManager.GetActiveScene().GetRootGameObjects())
                foreach (var t in root.GetComponentsInChildren<Transform>(true))
                    if (t.name == name) return t;
            return null;
        }
    }
}
