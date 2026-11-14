using System;
using System.Collections.Generic;
using UnityEngine;
using UnityEngine.UI;

namespace BharatBusSim.Signs
{
    public enum SignType
    {
        AdvanceDirectionBlue,
        HighwayDistanceGreen,
        SpeedLimitCircular,
        StopOctagonal,
        CautionaryTriangular
    }

    /// <summary>
    /// IRC:67-2012 compliant Indian road signs with realistic metallic tubular/I-beam structures,
    /// high-contrast reflective blue & green panels, crisp white border insets, and bilingual typography.
    /// </summary>
    public static class IndianRoadSigns
    {
        public static GameObject BuildAllSignage(Transform parent, Material blueMat, Material greenMat, Material redMat, Material yellowMat, Material whiteMat, Material poleMat)
        {
            var signsGo = new GameObject("Indian_Traffic_Signs");
            signsGo.transform.SetParent(parent, false);

            // 1. Swaraj Round Approaches (Bilingual Direction Guidance)
            CreateDirectionSign(signsGo.transform,
                new Vector3(36f, 0f, -135f), Quaternion.Euler(0, -10f, 0),
                "↑ സ്വരാജ് റൗണ്ട് / Swaraj Round\n→ പുത്തൻപള്ളി / Puthanpalli",
                blueMat, whiteMat, poleMat, 4.8f, 2.3f);

            CreateDirectionSign(signsGo.transform,
                new Vector3(-45f, 0f, 35f), Quaternion.Euler(0, 90, 0),
                "↑ പടിഞ്ഞാറേക്കോട്ട / West Fort (SH 69)\n← ഗുരുവായൂർ / Guruvayur",
                blueMat, whiteMat, poleMat, 5.0f, 2.3f);

            CreateDirectionSign(signsGo.transform,
                new Vector3(45f, 0f, 25f), Quaternion.Euler(0, 270, 0),
                "↑ കിഴക്കേക്കോട്ട / East Fort\n→ NH 544 മണ്ണുത്തി / Mannuthy",
                blueMat, whiteMat, poleMat, 5.0f, 2.3f);

            CreateDirectionSign(signsGo.transform,
                new Vector3(-5f, 0f, 65f), Quaternion.Euler(0, 180, 0),
                "↑ വടക്കേ ബസ് സ്റ്റാൻഡ് / Vadakke Stand\n← ഷൊർണ്ണൂർ / Shoranur",
                blueMat, whiteMat, poleMat, 5.0f, 2.3f);

            // 2. NH 544 Highway Corridor & Toll Advance Signs
            CreateHighwayDistanceSign(signsGo.transform,
                new Vector3(970f, 0f, -400f), Quaternion.Euler(0, 180, 0),
                "NH 544 കൊച്ചി / KOCHI : 68 km\nപാലിയേക്കര ടോൾ / TOLL : 7 km",
                greenMat, whiteMat, poleMat, 5.2f, 2.5f);

            CreateHighwayDistanceSign(signsGo.transform,
                new Vector3(980f, 0f, -1200f), Quaternion.Euler(0, 180, 0),
                "NH 544 പാലിയേക്കര ടോൾ പ്ലാസ / PALIYEKKARA TOLL : 3 km\nവേഗപരിധി / SPEED LIMIT : 80 km/h",
                greenMat, whiteMat, poleMat, 5.6f, 2.6f);

            // 500m & 200m Toll Advance Warning
            CreateCautionSign(signsGo.transform,
                new Vector3(992f, 0f, -1650f), Quaternion.Euler(0, 180, 0),
                "ടോൾ പ്ലാസ 500m\nTOLL PLAZA 500m",
                yellowMat, redMat, whiteMat, poleMat);

            CreateCautionSign(signsGo.transform,
                new Vector3(995f, 0f, -1740f), Quaternion.Euler(0, 180, 0),
                "STOP & PAY TOLL\nടോൾ നൽകുക ₹50",
                yellowMat, redMat, whiteMat, poleMat);

            // 3. Peechi Dam Route Signs
            CreateDirectionSign(signsGo.transform,
                new Vector3(1050f, 0f, 195f), Quaternion.Euler(0, 90, 0),
                "↑ പീച്ചി ഡാം / Peechi Dam (SH 51)\nWestern Ghats Sanctuary",
                blueMat, whiteMat, poleMat, 4.8f, 2.2f);

            // 4. Guruvayur & Chavakkad Beach Route Signs
            CreateDirectionSign(signsGo.transform,
                new Vector3(-1800f, 0f, -125f), Quaternion.Euler(0, 270, 0),
                "↑ ചൂണ്ടൽ / Choondal\n→ ഗുരുവായൂർ ക്ഷേത്രം / Guruvayur Temple",
                blueMat, whiteMat, poleMat, 5.2f, 2.4f);

            CreateDirectionSign(signsGo.transform,
                new Vector3(-3750f, 0f, 1530f), Quaternion.Euler(0, 270, 0),
                "↑ ഗുരുവായൂർ കിഴക്കേ നട / East Nada\n← ചാവക്കാട് ബീച്ച് / Chavakkad Beach (4 km)",
                blueMat, whiteMat, poleMat, 5.4f, 2.5f);

            // 5. Coastal NH 66 & Triprayar Signs
            CreateDirectionSign(signsGo.transform,
                new Vector3(-4490f, 0f, 1030f), Quaternion.Euler(0, 180, 0),
                "↑ ചാവക്കാട് ബീച്ച് / Chavakkad Beach\n↓ NH 66 തൃപ്രയാർ / Triprayar",
                blueMat, whiteMat, poleMat, 5.0f, 2.3f);

            CreateDirectionSign(signsGo.transform,
                new Vector3(-2140f, 0f, -2380f), Quaternion.Euler(0, 0, 0),
                "↑ തൃപ്രയാർ ശ്രീരാമസ്വാമി ക്ഷേത്രം\nTriprayar Sree Rama Temple\n→ തൃപ്രയാർ ബീച്ച് / Triprayar Beach",
                blueMat, whiteMat, poleMat, 5.6f, 2.6f);

            // 6. Regulatory Speed Limit Signs
            CreateSpeedLimitSign(signsGo.transform, new Vector3(34.5f, 0f, -130f), Quaternion.Euler(0, 0, 0), "30", redMat, whiteMat, poleMat);
            CreateSpeedLimitSign(signsGo.transform, new Vector3(988f, 0f, -600f), Quaternion.Euler(0, 180, 0), "80", redMat, whiteMat, poleMat);
            CreateSpeedLimitSign(signsGo.transform, new Vector3(-280f, 0f, -60f), Quaternion.Euler(0, 270, 0), "50", redMat, whiteMat, poleMat);

            // Strip any remaining colliders on signs to prevent physical vehicle obstruction
            var cols = signsGo.GetComponentsInChildren<Collider>();
            for (int c = 0; c < cols.Length; c++)
            {
                UnityEngine.Object.DestroyImmediate(cols[c]);
            }

            return signsGo;
        }

        public static GameObject CreateDirectionSign(Transform parent, Vector3 pos, Quaternion rot, string text, Material boardMat, Material whiteBorderMat, Material poleMat, float width = 4.8f, float height = 2.3f)
        {
            var signGo = new GameObject("Sign_Direction_Advance");
            signGo.transform.SetParent(parent, false);
            signGo.transform.position = pos;
            signGo.transform.rotation = rot;

            // Two Galvanized Steel Mounting Posts with Base Concrete Footings
            for (int p = -1; p <= 1; p += 2)
            {
                float px = p * (width * 0.38f);

                // Concrete Foundation Footing
                var footing = GameObject.CreatePrimitive(PrimitiveType.Cube);
                footing.name = $"Footing_{(p < 0 ? "L" : "R")}";
                footing.transform.SetParent(signGo.transform, false);
                footing.transform.localPosition = new Vector3(px, 0.15f, 0f);
                footing.transform.localScale = new Vector3(0.5f, 0.3f, 0.5f);
                footing.GetComponent<Renderer>().sharedMaterial = whiteBorderMat;

                // Steel Post (Height 3.6m)
                var pole = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
                pole.name = $"Pole_{(p < 0 ? "L" : "R")}";
                pole.transform.SetParent(signGo.transform, false);
                pole.transform.localPosition = new Vector3(px, 1.8f, 0f);
                pole.transform.localScale = new Vector3(0.12f, 1.8f, 0.12f);
                pole.GetComponent<Renderer>().sharedMaterial = poleMat;
            }

            // Horizontal Support Rails behind board
            for (int r = 0; r < 2; r++)
            {
                var rail = GameObject.CreatePrimitive(PrimitiveType.Cube);
                rail.name = $"Support_Rail_{r}";
                rail.transform.SetParent(signGo.transform, false);
                rail.transform.localPosition = new Vector3(0f, 2.6f + r * 1.2f, 0.05f);
                rail.transform.localScale = new Vector3(width * 0.95f, 0.08f, 0.06f);
                rail.GetComponent<Renderer>().sharedMaterial = poleMat;
                var col = rail.GetComponent<Collider>();
                if (col != null) UnityEngine.Object.DestroyImmediate(col);
            }

            // White Border Inset Frame (Outer Rim)
            var outerFrame = GameObject.CreatePrimitive(PrimitiveType.Cube);
            outerFrame.name = "Board_White_Border";
            outerFrame.transform.SetParent(signGo.transform, false);
            outerFrame.transform.localPosition = new Vector3(0f, 3.2f, 0f);
            outerFrame.transform.localScale = new Vector3(width, height, 0.08f);
            outerFrame.GetComponent<Renderer>().sharedMaterial = whiteBorderMat;

            // Blue IRC Direction Board Face (Inner panel)
            var board = GameObject.CreatePrimitive(PrimitiveType.Cube);
            board.name = "Board_Face";
            board.transform.SetParent(signGo.transform, false);
            board.transform.localPosition = new Vector3(0f, 3.2f, -0.015f);
            board.transform.localScale = new Vector3(width - 0.12f, height - 0.12f, 0.08f);
            board.GetComponent<Renderer>().sharedMaterial = boardMat;

            // World Space Canvas Text (Bilingual Malayalam & English, URP UI/Default shader)
            AttachWorldSpaceSignText(signGo, new Vector3(0f, 3.2f, -0.07f), new Vector2(width - 0.3f, height - 0.3f), text, Color.white, 24);

            return signGo;
        }

        public static GameObject CreateHighwayDistanceSign(Transform parent, Vector3 pos, Quaternion rot, string text, Material greenMat, Material whiteBorderMat, Material poleMat, float width = 5.2f, float height = 2.5f)
        {
            var signGo = new GameObject("Sign_Highway_Green");
            signGo.transform.SetParent(parent, false);
            signGo.transform.position = pos;
            signGo.transform.rotation = rot;

            for (int p = -1; p <= 1; p += 2)
            {
                float px = p * (width * 0.38f);
                var pole = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
                pole.name = $"Pole_{(p < 0 ? "L" : "R")}";
                pole.transform.SetParent(signGo.transform, false);
                pole.transform.localPosition = new Vector3(px, 2.0f, 0f);
                pole.transform.localScale = new Vector3(0.14f, 2.0f, 0.14f);
                pole.GetComponent<Renderer>().sharedMaterial = poleMat;
            }

            // White Border Frame
            var outerFrame = GameObject.CreatePrimitive(PrimitiveType.Cube);
            outerFrame.name = "Green_Board_Border";
            outerFrame.transform.SetParent(signGo.transform, false);
            outerFrame.transform.localPosition = new Vector3(0f, 3.5f, 0f);
            outerFrame.transform.localScale = new Vector3(width, height, 0.08f);
            outerFrame.GetComponent<Renderer>().sharedMaterial = whiteBorderMat;

            // Green NH Board Face
            var board = GameObject.CreatePrimitive(PrimitiveType.Cube);
            board.name = "Green_Board_Face";
            board.transform.SetParent(signGo.transform, false);
            board.transform.localPosition = new Vector3(0f, 3.5f, -0.015f);
            board.transform.localScale = new Vector3(width - 0.12f, height - 0.12f, 0.08f);
            board.GetComponent<Renderer>().sharedMaterial = greenMat;

            AttachWorldSpaceSignText(signGo, new Vector3(0f, 3.5f, -0.07f), new Vector2(width - 0.3f, height - 0.3f), text, Color.white, 25);

            return signGo;
        }

        public static GameObject CreateCautionSign(Transform parent, Vector3 pos, Quaternion rot, string text, Material yellowMat, Material redMat, Material whiteBorderMat, Material poleMat)
        {
            var signGo = new GameObject("Sign_Cautionary_Diamond");
            signGo.transform.SetParent(parent, false);
            signGo.transform.position = pos;
            signGo.transform.rotation = rot;

            var pole = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
            pole.name = "Pole";
            pole.transform.SetParent(signGo.transform, false);
            pole.transform.localPosition = new Vector3(0f, 1.8f, 0f);
            pole.transform.localScale = new Vector3(0.10f, 1.8f, 0.10f);
            pole.GetComponent<Renderer>().sharedMaterial = poleMat;

            // Diamond Yellow Caution Sign
            var diamond = GameObject.CreatePrimitive(PrimitiveType.Cube);
            diamond.name = "Diamond_Face";
            diamond.transform.SetParent(signGo.transform, false);
            diamond.transform.localPosition = new Vector3(0f, 3.4f, 0f);
            diamond.transform.localRotation = Quaternion.Euler(0f, 0f, 45f);
            diamond.transform.localScale = new Vector3(1.8f, 1.8f, 0.06f);
            diamond.GetComponent<Renderer>().sharedMaterial = yellowMat;

            AttachWorldSpaceSignText(signGo, new Vector3(0f, 3.4f, -0.06f), new Vector2(2.4f, 1.2f), text, Color.black, 20);

            return signGo;
        }

        public static GameObject CreateSpeedLimitSign(Transform parent, Vector3 pos, Quaternion rot, string speed, Material redRingMat, Material whiteFaceMat, Material poleMat)
        {
            var signGo = new GameObject($"Sign_SpeedLimit_{speed}");
            signGo.transform.SetParent(parent, false);
            signGo.transform.position = pos;
            signGo.transform.rotation = rot;

            var pole = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
            pole.name = "Pole";
            pole.transform.SetParent(signGo.transform, false);
            pole.transform.localPosition = new Vector3(0f, 1.5f, 0f);
            pole.transform.localScale = new Vector3(0.09f, 1.5f, 0.09f);
            pole.GetComponent<Renderer>().sharedMaterial = poleMat;

            // Circular Red Border Ring
            var ring = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
            ring.name = "Red_Border";
            ring.transform.SetParent(signGo.transform, false);
            ring.transform.localPosition = new Vector3(0f, 2.7f, 0f);
            ring.transform.localRotation = Quaternion.Euler(90f, 0f, 0f);
            ring.transform.localScale = new Vector3(1.15f, 0.03f, 1.15f);
            ring.GetComponent<Renderer>().sharedMaterial = redRingMat;

            // White Inner Center Face
            var face = GameObject.CreatePrimitive(PrimitiveType.Cylinder);
            face.name = "White_Face";
            face.transform.SetParent(signGo.transform, false);
            face.transform.localPosition = new Vector3(0f, 2.7f, -0.02f);
            face.transform.localRotation = Quaternion.Euler(90f, 0f, 0f);
            face.transform.localScale = new Vector3(0.92f, 0.035f, 0.92f);
            face.GetComponent<Renderer>().sharedMaterial = whiteFaceMat;

            AttachWorldSpaceSignText(signGo, new Vector3(0f, 2.7f, -0.05f), new Vector2(1.0f, 0.8f), speed, Color.black, 40);

            return signGo;
        }

        private static void AttachWorldSpaceSignText(GameObject parentGo, Vector3 localPos, Vector2 size, string text, Color textColor, int fontSize)
        {
            var canvasGo = new GameObject("WorldSpace_Sign_Canvas");
            canvasGo.transform.SetParent(parentGo.transform, false);
            canvasGo.transform.localPosition = localPos;
            canvasGo.transform.localRotation = Quaternion.identity;

            var canvas = canvasGo.AddComponent<Canvas>();
            canvas.renderMode = RenderMode.WorldSpace;

            var rt = canvasGo.GetComponent<RectTransform>();
            rt.sizeDelta = size;
            rt.localScale = Vector3.one;

            var textGo = new GameObject("Sign_Text");
            textGo.transform.SetParent(canvasGo.transform, false);

            var textRt = textGo.AddComponent<RectTransform>();
            textRt.anchorMin = Vector2.zero;
            textRt.anchorMax = Vector2.one;
            textRt.offsetMin = new Vector2(10f, 10f);
            textRt.offsetMax = new Vector2(-10f, -10f);
            textRt.localScale = Vector3.one * 0.01f;

            var uiText = textGo.AddComponent<Text>();
            uiText.text = text;
            uiText.font = Resources.GetBuiltinResource<Font>("LegacyRuntime.ttf");
            uiText.fontSize = fontSize * 10;
            uiText.fontStyle = FontStyle.Bold;
            uiText.alignment = TextAnchor.MiddleCenter;
            uiText.color = textColor;
            uiText.raycastTarget = false;
            uiText.horizontalOverflow = HorizontalWrapMode.Wrap;
            uiText.verticalOverflow = VerticalWrapMode.Overflow;
        }
    }
}
