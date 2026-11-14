using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using UnityEditor;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.SceneManagement;

namespace BharatBusSim.Editor
{
    /// <summary>Renders delivery screenshots of the rebuilt Mannuthy-Ollur corridor.</summary>
    public static class NewPackRebuildShots
    {
        private const string ScenePath = "Assets/_Project/Scenes/03_Thrissur_RealisticWorld.unity";
        private const string OutDir = "C:/Project/BHARAT BUS SIMULATOR/artifacts/newpack_shots";

        public static void RunFromCommandLine()
        {
            try { Run(); EditorApplication.Exit(0); }
            catch (Exception e) { Debug.LogException(e); EditorApplication.Exit(1); }
        }

        [MenuItem("Tools/New Pack/Render Verification Shots")]
        public static void Run()
        {
            EditorSceneManager.OpenScene(ScenePath, OpenSceneMode.Single);
            Directory.CreateDirectory(OutDir);

            var junc = new Vector3(1352.0f, 1.30f, -472.0f);
            var gore = new Vector3(2178f, 1.10f, -1481f);
            var rampKerbs = new Vector3(2187.8f, 1.30f, -1531.8f);
            var nhMid = new Vector3(1747.4f, 1.15f, -958.9f);
            var connMid = new Vector3(1311.5f, 1.05f, -1274.9f);
            var approach = new Vector3(1368.9f, 1.30f, -406.2f);
            var roundabout = new Vector3(421.4f, 0.9f, -942.1f);

            // top-down plans
            Render("np_mannuthy_top.png", junc + Vector3.up * 70f, junc, 45f);
            Render("np_ollur_top.png", gore + Vector3.up * 70f, gore, 45f);
            Render("np_roundabout_top.png", roundabout + Vector3.up * 65f, roundabout, 45f);
            Render("np_corridor_top.png", nhMid + Vector3.up * 750f, nhMid, 55f);

            // oblique close-ups
            Render("np_mannuthy_oblique.png", junc + new Vector3(38f, 26f, 44f), junc, 50f);
            Render("np_ollur_oblique.png", gore + new Vector3(34f, 24f, 40f), gore, 50f);
            Render("np_taper_wide.png", gore + new Vector3(0f, 38f, 0f), gore, 45f);
            Render("np_ollur_area.png", new Vector3(2178f, 1.1f, -1481f) + Vector3.up * 75f, new Vector3(2178f, 1.1f, -1481f), 55f);
            Render("np_taper_side.png", gore + new Vector3(30f, 9f, 26f), gore, 50f);
            Render("np_taper_close.png", gore + new Vector3(-14f, 11f, -20f), gore + Vector3.up * 0.5f, 55f);
            Render("np_taper_from_mdr.png", gore + new Vector3(14f, 11f, 22f), gore + Vector3.up * 0.5f, 55f);
            Render("np_roundabout_oblique.png", roundabout + new Vector3(28f, 24f, 32f), roundabout, 50f);
            Render("np_mannuthy_close.png", junc + new Vector3(8f, 26f, 12f), junc, 45f);
            Render("np_ollur_gore_close.png", gore + new Vector3(6f, 28f, 9f), gore, 45f);

            // Ollur terminus. The turning head is the last thing the generator moves, so frame it from
            // its own geometry rather than from coordinates that go stale as soon as it is rebuilt.
            var nose = NoseOf("Ollur_Exit_MDR_High_Road_Connector");
            Render("np_nose_top.png", nose + Vector3.up * 70f, nose, 45f);
            Render("np_nose_oblique.png", nose + new Vector3(-32f, 20f, -40f), nose, 50f);
            Render("np_loop_top.png", new Vector3(2170f, 1.1f, -1540f) + Vector3.up * 170f, new Vector3(2170f, 1.1f, -1540f), 45f);
            Render("np_mannuthy_bend_top.png", new Vector3(1400f, 1.1f, -250f) + Vector3.up * 300f, new Vector3(1400f, 1.1f, -250f), 45f);

            // driver-eye views
            Render("np_mannuthy_driver.png", approach + Vector3.up * 2.4f + new Vector3(3f, 0f, 0f), junc + Vector3.up * 1.2f, 60f);
            Render("np_nh544_driver.png", nhMid + Vector3.up * 2.4f, nhMid + new Vector3(120f, 1f, -140f), 60f);
            Render("np_ollur_driver.png", gore + new Vector3(-45f, 2.4f, 52f), gore + Vector3.up * 1.0f, 60f);
            Render("np_ramp_driver.png", rampKerbs + new Vector3(0f, 2.4f, 34f), rampKerbs + new Vector3(0f, 1f, -30f), 60f);
            Render("np_sliproad_driver.png", gore + new Vector3(-6f, 2.4f, -12f), rampKerbs + new Vector3(0f, 1f, -40f), 60f);
            Render("np_connector_driver.png", connMid + new Vector3(80f, 2.4f, -10f), connMid + new Vector3(-120f, 1f, 60f), 60f);

            Debug.Log("NEWPACK_SHOTS_DONE " + OutDir);
        }

        /// <summary>The southernmost point of a generated carriageway - the nose of the Ollur turning head.</summary>
        private static Vector3 NoseOf(string roadName)
        {
            var mf = UnityEngine.Object.FindObjectsByType<MeshFilter>(FindObjectsInactive.Exclude, FindObjectsSortMode.None)
                .First(f => f.name == roadName && f.sharedMesh != null);
            var verts = mf.sharedMesh.vertices;
            var t = mf.transform;
            var best = t.TransformPoint(verts[0]);
            foreach (var v in verts)
            {
                var p = t.TransformPoint(v);
                if (p.z < best.z) best = p;
            }
            return best;
        }

        private static void Render(string name, Vector3 pos, Vector3 lookAt, float fov)
        {
            var camGo = new GameObject("ShotCam");
            var cam = camGo.AddComponent<Camera>();
            cam.clearFlags = CameraClearFlags.SolidColor;
            cam.backgroundColor = new Color(0.55f, 0.68f, 0.82f);
            cam.fieldOfView = fov;
            cam.nearClipPlane = 0.5f;
            cam.farClipPlane = 4000f;
            camGo.transform.position = pos;
            var dir = (lookAt - pos).normalized;
            // A top-down camera with Vector3.up as the hint is degenerate and rolls to an arbitrary
            // angle, so ask for north-up instead.
            camGo.transform.rotation = Quaternion.LookRotation(dir, Mathf.Abs(dir.y) > 0.98f ? Vector3.forward : Vector3.up);

            var rt = new RenderTexture(1600, 900, 24, RenderTextureFormat.ARGB32) { antiAliasing = 2 };
            cam.targetTexture = rt;
            cam.Render();
            RenderTexture.active = rt;
            var tex = new Texture2D(1600, 900, TextureFormat.RGB24, false);
            tex.ReadPixels(new Rect(0, 0, 1600, 900), 0, 0);
            tex.Apply();
            RenderTexture.active = null;
            cam.targetTexture = null;
            File.WriteAllBytes(Path.Combine(OutDir, name), tex.EncodeToPNG());
            UnityEngine.Object.DestroyImmediate(tex);
            UnityEngine.Object.DestroyImmediate(rt);
            UnityEngine.Object.DestroyImmediate(camGo);
        }
    }
}