using System;
using System.Collections.Generic;
using System.IO;
using System.Text;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.SceneManagement;

public static class NewPackGroundProbe
{
    const string ScenePath = "Assets/_Project/Scenes/03_Thrissur_RealisticWorld.unity";
    const string OutPath = "C:/Project/BHARAT BUS SIMULATOR/artifacts/newpack_ground_probe.json";

    [Serializable] public class HitInfo
    {
        public string label;
        public Vector3 origin;
        public bool hit;
        public string objectName;
        public string objectPath;
        public Vector3 point;
        public Vector3 normal;
        public float distance;
        public string collider;
    }

    [Serializable] public class ProbeDump
    {
        public string generatedUtc;
        public List<HitInfo> hits = new List<HitInfo>();
    }

    [MenuItem("Tools/New Pack/Probe Ground")]
    public static void Probe()
    {
        try
        {
            Debug.Log("NEWPACK_PROBE_START");
            var scene = EditorSceneManager.OpenScene(ScenePath, OpenSceneMode.Single);
            var dump = new ProbeDump { generatedUtc = DateTime.UtcNow.ToString("o") };

            // Sample points around the Mannuthy junction, NH544, Ollur exit and connector.
            var points = new List<(string, Vector3)>
            {
                ("Mannuthy apron centre", new Vector3(1346.6f, 1.3f, -469.4f)),
                ("Mannuthy apron +X 30", new Vector3(1376.6f, 1.3f, -469.4f)),
                ("Mannuthy apron -X 30", new Vector3(1316.6f, 1.3f, -469.4f)),
                ("Mannuthy apron +Z 40", new Vector3(1346.6f, 1.3f, -429.4f)),
                ("Mannuthy apron -Z 40", new Vector3(1346.6f, 1.3f, -509.4f)),
                ("NH544 mid", new Vector3(1747.4f, 1.7f, -958.9f)),
                ("NH544 mid +X 40", new Vector3(1787.4f, 1.7f, -958.9f)),
                ("NH544 mid -X 40", new Vector3(1707.4f, 1.7f, -958.9f)),
                ("Ollur gore centre", new Vector3(2184.0f, 1.1f, -1482.7f)),
                ("Ollur gore +X 30", new Vector3(2214.0f, 1.1f, -1482.7f)),
                ("Ollur gore -X 30", new Vector3(2154.0f, 1.1f, -1482.7f)),
                ("Ollur ramp kerb centre", new Vector3(2187.8f, 1.4f, -1531.8f)),
                ("Connector mid", new Vector3(1311.5f, 1.2f, -1274.9f)),
                ("Turnaround loop", new Vector3(429.4f, 0.9f, -945.1f)),
                ("World origin", new Vector3(0f, 0f, 0f)),
                ("Bus start", new Vector3(25f, 0.3f, -169.3f))
            };
            for (int i = 0; i < 8; i++)
            {
                float a = i / 8f * Mathf.PI * 2f;
                points.Add(($"Roundabout ring {i * 45}", new Vector3(421.4f + Mathf.Cos(a) * 14f, 0.9f,
                    -942.1f + Mathf.Sin(a) * 14f)));
            }
            points.Add(("Roundabout island", new Vector3(421.4f, 0.9f, -942.1f)));
            points.Add(("Ollur taper middle", new Vector3(2157.3f, 1.1f, -1449.6f)));
            points.Add(("Ollur dark wedge", new Vector3(2192f, 1.1f, -1490f)));

            foreach (var (label, p) in points)
            {
                var down = new HitInfo { label = label, origin = p + Vector3.up * 200f };
                if (Physics.Raycast(down.origin, Vector3.down, out var hit, 400f, ~0, QueryTriggerInteraction.Ignore))
                {
                    down.hit = true;
                    down.objectName = hit.collider.gameObject.name;
                    down.objectPath = GetPath(hit.collider.transform);
                    down.point = hit.point;
                    down.normal = hit.normal;
                    down.distance = hit.distance;
                    down.collider = hit.collider.GetType().Name;
                }
                dump.hits.Add(down);
            }

            Directory.CreateDirectory(Path.GetDirectoryName(OutPath));
            File.WriteAllText(OutPath, JsonUtility.ToJson(dump, true), new UTF8Encoding(false));
            Debug.Log("NEWPACK_PROBE_DONE hits=" + dump.hits.Count);
        }
        catch (Exception e)
        {
            Debug.LogException(e);
            EditorApplication.Exit(1);
        }
    }

    static string GetPath(Transform t)
    {
        var parts = new List<string>();
        while (t != null) { parts.Add(t.name); t = t.parent; }
        parts.Reverse();
        return string.Join("/", parts);
    }
}
