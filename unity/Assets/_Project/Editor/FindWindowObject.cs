using System;
using UnityEngine;
using UnityEditor;

public static class FindWindowObject
{
    public static void Run()
    {
        string scenePath = "Assets/_Project/Scenes/03_Thrissur_RealisticWorld.unity";
        UnityEditor.SceneManagement.EditorSceneManager.OpenScene(scenePath, UnityEditor.SceneManagement.OpenSceneMode.Single);

        var busGo = GameObject.Find("Kerala_Bus_Prototype");
        if (busGo == null)
        {
            Debug.LogError("Kerala_Bus_Prototype not found!");
            return;
        }

        Transform visuals = busGo.transform.Find("KeralaBus12m_Visuals");
        if (visuals == null)
        {
            Debug.LogError("KeralaBus12m_Visuals not found!");
            return;
        }

        var renderers = visuals.GetComponentsInChildren<Renderer>(true);
        Debug.Log("Total renderers in visuals: " + renderers.Length);

        // Cockpit eye is around X=0.70, Y=2.16, Z=4.65
        // Right side window is around X > 0.8, Y around 1.8 to 2.8, Z between 3.0 and 5.5
        foreach (var r in renderers)
        {
            Bounds b = r.bounds;
            Vector3 busLocalMin = busGo.transform.InverseTransformPoint(b.min);
            Vector3 busLocalMax = busGo.transform.InverseTransformPoint(b.max);
            Vector3 busLocalCenter = busGo.transform.InverseTransformPoint(b.center);

            // Filter objects on the right side near the driver
            if (busLocalCenter.x > 0.5f && busLocalCenter.y > 1.2f && busLocalCenter.z > 2.5f && busLocalCenter.z < 5.8f)
            {
                Debug.Log($"[RightSideObj] Name='{r.name}', Active={r.gameObject.activeSelf}, RendererEnabled={r.enabled}, Center={busLocalCenter}, Size={b.size}, Mat={((r.sharedMaterial != null) ? r.sharedMaterial.name : "null")}");
            }
        }
    }
}
