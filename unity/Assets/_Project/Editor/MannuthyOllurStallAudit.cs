using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.SceneManagement;

namespace BharatBusSim.Editor
{
    /// <summary>
    /// Read-only diagnostic: reports every collider and surface height around a world
    /// position so an unexplained bus stall can be traced without changing the scene.
    /// </summary>
    public static class MannuthyOllurStallAudit
    {
        private const string ScenePath = "Assets/_Project/Scenes/03_Thrissur_RealisticWorld.unity";

        public static void RunFromCommandLine()
        {
            var scene = EditorSceneManager.OpenScene(ScenePath, OpenSceneMode.Single);
            var points = new List<Vector3>
            {
                new Vector3(666.56f, 0.97f, -1115.08f),
                new Vector3(419.56f, 0.90f, -948.84f)
            };
            var args = System.Environment.GetCommandLineArgs();
            for (int i = 0; i < args.Length - 2; i++)
            {
                if (args[i] != "-auditX") continue;
                points.Clear();
                points.Add(new Vector3(float.Parse(args[i + 1]), 0.9f, float.Parse(args[i + 2])));
            }
            foreach (var centre in points) Audit(scene, centre);
            if (!EditorSceneManager.CloseScene(scene, true))
                Debug.LogWarning("[StallAudit] Could not close the scene without saving.");
        }

        [MenuItem("Tools/Kerala Bus Simulator/Audit Mannuthy-Ollur Stall Point")]
        public static void RunFromMenu()
        {
            var scene = EditorSceneManager.OpenScene(ScenePath, OpenSceneMode.Single);
            Audit(scene, new Vector3(666.56f, 0.97f, -1115.08f));
        }

        private static void Audit(Scene scene, Vector3 centre)
        {
            Debug.Log($"[StallAudit] Auditing {centre} in {scene.path}");

            var hits = Physics.OverlapBox(centre + Vector3.up * 4f, new Vector3(30f, 6f, 30f),
                Quaternion.identity, ~0, QueryTriggerInteraction.Collide);
            var ordered = hits.Where(c => c != null)
                .OrderBy(c => c.bounds.center.y)
                .ToList();
            Debug.Log($"[StallAudit] {ordered.Count} colliders within 30 m of the stall point.");
            foreach (var collider in ordered)
            {
                var bounds = collider.bounds;
                string path = collider.transform.name;
                for (var parent = collider.transform.parent; parent != null; parent = parent.parent)
                    path = parent.name + "/" + path;
                string prefab = PrefabUtility.GetPrefabAssetPathOfNearestInstanceRoot(collider.gameObject);
                bool visible = collider.GetComponent<Renderer>() != null;
                Debug.Log($"[StallAudit] {collider.GetType().Name} '{collider.name}' " +
                          $"active={collider.gameObject.activeInHierarchy} trigger={collider.isTrigger} " +
                          $"visible={visible} layer={LayerMask.LayerToName(collider.gameObject.layer)} " +
                          $"y=[{bounds.min.y:F2},{bounds.max.y:F2}] " +
                          $"x=[{bounds.min.x:F1},{bounds.max.x:F1}] z=[{bounds.min.z:F1},{bounds.max.z:F1}] " +
                          $"prefab='{prefab}' path='{path}'");
            }

            Debug.Log("[StallAudit] Surface-height grid (topmost hit, name@height) within 18 m:");
            var report = new StringBuilder();
            for (float dz = -18f; dz <= 18.01f; dz += 3f)
            {
                report.Clear();
                for (float dx = -18f; dx <= 18.01f; dx += 3f)
                {
                    Vector3 origin = new Vector3(centre.x + dx, centre.y + 30f, centre.z + dz);
                    if (!Physics.Raycast(origin, Vector3.down, out RaycastHit hit, 60f, ~0,
                            QueryTriggerInteraction.Ignore))
                    {
                        report.Append("      ---      ");
                        continue;
                    }
                    report.Append($"{hit.point.y,5:F2}:{Abbreviate(hit.collider.name),-10}");
                }
                Debug.Log($"[StallAudit] dz={dz,6:F1} | {report}");
            }

            Debug.Log("[StallAudit] Column probes straight along the drive direction:");
            for (float t = -60f; t <= 60.01f; t += 6f)
            {
                var point = centre + new Vector3(-0.98f, 0f, 0.18f) * t;
                Vector3 origin = new Vector3(point.x, point.y + 30f, point.z);
                if (!Physics.Raycast(origin, Vector3.down, out RaycastHit hit, 60f, ~0,
                        QueryTriggerInteraction.Ignore))
                {
                    Debug.Log($"[StallAudit] t={t,6:F1} ({point.x:F1},{point.z:F1}) no surface");
                    continue;
                }
                int stack = Physics.RaycastAll(origin, Vector3.down, 60f, ~0, QueryTriggerInteraction.Ignore)
                    .OrderByDescending(h => h.point.y).Count();
                var names = Physics.RaycastAll(origin, Vector3.down, 60f, ~0, QueryTriggerInteraction.Ignore)
                    .OrderByDescending(h => h.point.y)
                    .Select(h => $"{Abbreviate(h.collider.name)}@{h.point.y:F2}");
                Debug.Log($"[StallAudit] t={t,6:F1} ({point.x:F1},{point.z:F1}) top={Abbreviate(hit.collider.name)}@{hit.point.y:F2} " +
                          $"layers={stack} [{string.Join(", ", names)}]");
            }
        }

        private static string Abbreviate(string name)
        {
            if (string.IsNullOrEmpty(name)) return "?";
            return name.Length <= 22 ? name : name.Substring(0, 22);
        }
    }
}
