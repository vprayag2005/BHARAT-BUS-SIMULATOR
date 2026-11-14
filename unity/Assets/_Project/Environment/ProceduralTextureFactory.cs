using System;
using System.IO;
using UnityEngine;

namespace BharatBusSim.Environment
{
    /// <summary>
    /// Generates high-fidelity procedural PBR textures for realistic Kerala roads,
    /// architecture, vegetation, and terrain. Ensures zero cartoon look and ₹0 asset cost.
    /// </summary>
    public static class ProceduralTextureFactory
    {
        private const string TexturesDir = "Assets/_Project/Textures";

        public static Texture2D GetOrCreateAsphaltTexture()
        {
            return GetOrCreateTexture("Tex_Asphalt_PBR.png", 512, 512, (x, y, w, h) =>
            {
                // Multi-frequency noise for authentic aggregate asphalt with tar seams
                float n1 = Mathf.PerlinNoise(x * 0.08f, y * 0.08f);
                float n2 = Mathf.PerlinNoise(x * 0.25f, y * 0.25f);
                float n3 = Mathf.PerlinNoise(x * 0.85f, y * 0.85f);

                // Deep charcoal asphalt tone
                float baseTone = 0.12f + (n1 * 0.05f) + (n2 * 0.03f);

                // Aggregate mineral flecks (fine light grey pebbles)
                if (n3 > 0.70f)
                {
                    baseTone += 0.10f * ((n3 - 0.70f) / 0.30f);
                }
                else if (n3 < 0.22f)
                {
                    baseTone -= 0.04f;
                }

                baseTone = Mathf.Clamp(baseTone, 0.08f, 0.28f);
                return new Color(baseTone * 0.96f, baseTone, baseTone * 1.02f, 1.0f);
            });
        }

        public static Texture2D GetOrCreateAsphaltNormalTexture()
        {
            return GetOrCreateTexture("Tex_Asphalt_Normal.png", 512, 512, (x, y, w, h) =>
            {
                // Standard flat normal default
                return new Color(0.5f, 0.5f, 1.0f, 1.0f);
            });
        }

        public static Texture2D GetOrCreateCorrugatedCanopyTexture()
        {
            return GetOrCreateTexture("Tex_Corrugated_Roof.png", 512, 512, (x, y, w, h) =>
            {
                // Corrugated industrial profile sheet (repeating sinusoidal corrugation)
                int ribWidth = 16;
                int rx = x % ribWidth;
                float rib = Mathf.Sin((rx / (float)ribWidth) * Mathf.PI);

                // Shading across the rib (highlights on crest, shadow in valley)
                float shade = 0.70f + rib * 0.35f;

                // Industrial Kerala terminal blue or terracotta tone
                float noise = (Mathf.PerlinNoise(x * 0.05f, y * 0.05f) - 0.5f) * 0.08f;
                float r = Mathf.Clamp01((0.18f + noise) * shade);
                float g = Mathf.Clamp01((0.35f + noise) * shade);
                float b = Mathf.Clamp01((0.55f + noise) * shade);

                return new Color(r, g, b, 1.0f);
            });
        }

        public static Texture2D GetOrCreateClockDialTexture()
        {
            return GetOrCreateTexture("Tex_Clock_Face.png", 256, 256, (x, y, w, h) =>
            {
                float cx = w * 0.5f;
                float cy = h * 0.5f;
                float dx = x - cx;
                float dy = y - cy;
                float dist = Mathf.Sqrt(dx * dx + dy * dy);
                float r = w * 0.46f;

                if (dist > r)
                {
                    // Outer square frame
                    return new Color(0.85f, 0.82f, 0.78f, 1.0f);
                }

                // Black outer ring border
                if (dist > r - 8f)
                {
                    return new Color(0.12f, 0.12f, 0.12f, 1.0f);
                }

                // White dial face
                float angle = Mathf.Atan2(dy, dx) * Mathf.Rad2Deg;
                if (angle < 0) angle += 360f;

                // 12 hour tick marks
                float hourAngle = angle % 30f;
                if ((hourAngle < 2.5f || hourAngle > 27.5f) && dist > r - 28f)
                {
                    return new Color(0.10f, 0.10f, 0.10f, 1.0f); // Hour marker
                }

                // Clock hands (pointing to 10:10)
                // Hour hand pointing to 10 (approx 150 deg)
                if (Mathf.Abs(angle - 150f) < 4f && dist < r * 0.55f)
                {
                    return new Color(0.12f, 0.12f, 0.12f, 1.0f);
                }
                // Minute hand pointing to 2 (approx 30 deg)
                if (Mathf.Abs(angle - 30f) < 3f && dist < r * 0.75f)
                {
                    return new Color(0.12f, 0.12f, 0.12f, 1.0f);
                }

                // Off-white antique dial
                return new Color(0.96f, 0.95f, 0.92f, 1.0f);
            });
        }

        public static Texture2D GetOrCreateKerbTexture()
        {
            return GetOrCreateTexture("Tex_Kerb_YellowBlack.png", 256, 64, (x, y, w, h) =>
            {
                // Alternating Kerala standard yellow & black concrete curb blocks
                int block = (x / 64) % 2;
                float noise = (Mathf.PerlinNoise(x * 0.15f, y * 0.15f) - 0.5f) * 0.08f;
                float bevel = 1.0f;
                if (y < 4 || y > h - 4) bevel = 0.75f;

                if (block == 0)
                {
                    // Kerala highway safety yellow with weathering
                    float r = Mathf.Clamp01((0.92f + noise) * bevel);
                    float g = Mathf.Clamp01((0.74f + noise) * bevel);
                    float b = Mathf.Clamp01((0.08f + noise) * bevel);
                    return new Color(r, g, b, 1.0f);
                }
                else
                {
                    // Worn charcoal black
                    float tone = Mathf.Clamp01((0.15f + noise) * bevel);
                    return new Color(tone, tone, tone, 1.0f);
                }
            });
        }

        public static Texture2D GetOrCreateKeralaTileTexture()
        {
            return GetOrCreateTexture("Tex_Kerala_ClayTile.png", 512, 512, (x, y, w, h) =>
            {
                // Mangalore clay terracotta tiles with curved interlocking barrel profiles
                int tileWidth = 32;
                int tileHeight = 64;

                int tx = x % tileWidth;
                int ty = y % tileHeight;

                // Horizontal lap shadow
                float lapShadow = 1.0f;
                if (ty < 6) lapShadow = Mathf.Lerp(0.55f, 1.0f, ty / 6f);

                // Barrel curvature shading
                float curve = Mathf.Sin((tx / (float)tileWidth) * Mathf.PI);
                float shade = (0.75f + curve * 0.35f) * lapShadow;

                // Terracotta red-orange hue with natural clay variation
                float noise = (Mathf.PerlinNoise(x * 0.05f, y * 0.05f) - 0.5f) * 0.1f;
                float r = Mathf.Clamp01((0.76f + noise) * shade);
                float g = Mathf.Clamp01((0.26f + noise * 0.6f) * shade);
                float b = Mathf.Clamp01((0.14f + noise * 0.3f) * shade);

                return new Color(r, g, b, 1.0f);
            });
        }

        public static Texture2D GetOrCreateConcreteTexture()
        {
            return GetOrCreateTexture("Tex_Concrete_Terminal.png", 512, 512, (x, y, w, h) =>
            {
                // Weathered terminal slab concrete with expansion joint grid
                float n = Mathf.PerlinNoise(x * 0.04f, y * 0.04f) * 0.15f +
                          Mathf.PerlinNoise(x * 0.18f, y * 0.18f) * 0.08f;

                float baseTone = 0.68f + n;

                // Expansion joints every 128 pixels
                if (x % 128 < 3 || y % 128 < 3)
                {
                    baseTone *= 0.55f;
                }

                baseTone = Mathf.Clamp01(baseTone);
                return new Color(baseTone * 0.98f, baseTone, baseTone * 0.97f, 1.0f);
            });
        }

        public static Texture2D GetOrCreateLateriteSoilTexture()
        {
            return GetOrCreateTexture("Tex_Laterite_Soil.png", 512, 512, (x, y, w, h) =>
            {
                // Rich Kerala red laterite soil (ചുവന്ന മണ്ണ്) with gravel bits
                float n1 = Mathf.PerlinNoise(x * 0.06f, y * 0.06f);
                float n2 = Mathf.PerlinNoise(x * 0.3f, y * 0.3f);

                float r = 0.58f + n1 * 0.14f + n2 * 0.05f;
                float g = 0.24f + n1 * 0.08f + n2 * 0.03f;
                float b = 0.15f + n1 * 0.05f;

                // Occasional dark laterite pebbles
                if (n2 > 0.75f)
                {
                    r *= 0.7f; g *= 0.7f; b *= 0.7f;
                }

                return new Color(Mathf.Clamp01(r), Mathf.Clamp01(g), Mathf.Clamp01(b), 1.0f);
            });
        }

        public static Texture2D GetOrCreatePalmBarkTexture()
        {
            return GetOrCreateTexture("Tex_Palm_Bark.png", 256, 512, (x, y, w, h) =>
            {
                // Fibrous horizontal ringed coconut palm trunk
                int ringHeight = 24;
                int ry = y % ringHeight;
                float ringProfile = Mathf.Sin((ry / (float)ringHeight) * Mathf.PI);

                float n = Mathf.PerlinNoise(x * 0.1f, y * 0.02f) * 0.12f;
                float tone = (0.42f + ringProfile * 0.18f + n);

                return new Color(tone * 1.05f, tone * 0.92f, tone * 0.75f, 1.0f);
            });
        }

        public static Texture2D GetOrCreatePalmFrondTexture()
        {
            return GetOrCreateTexture("Tex_Palm_Frond.png", 512, 512, (x, y, w, h) =>
            {
                // Feathered pinnate coconut palm leaf with alpha cutout
                float u = x / (float)w; // 0 = base, 1 = tip
                float v = (y / (float)h) - 0.5f; // -0.5 to +0.5 across leaf width

                // Spine down center
                float spineWidth = 0.025f * (1.0f - u * 0.6f);
                float leafWidth = Mathf.Sin(u * Mathf.PI) * 0.42f; // Tapers at base and tip

                float distFromSpine = Mathf.Abs(v);

                if (distFromSpine > leafWidth)
                {
                    return new Color(0, 0, 0, 0); // Transparent outside leaf contour
                }

                // Individual leaflet serration
                float serration = Mathf.Sin((u * 48f) + (v * 12f));
                if (serration < -0.3f && distFromSpine > spineWidth * 1.5f && u > 0.1f && u < 0.9f)
                {
                    return new Color(0, 0, 0, 0); // Gap between leaflets
                }

                if (distFromSpine < spineWidth)
                {
                    // Yellow-green central rachis/spine
                    return new Color(0.68f, 0.78f, 0.28f, 1.0f);
                }

                // Lush tropical leaflet green with subtle gradient toward tips
                float greenTone = Mathf.Lerp(0.55f, 0.38f, u);
                float yellowHighlight = Mathf.Lerp(0.18f, 0.08f, distFromSpine / leafWidth);

                return new Color(yellowHighlight, greenTone, yellowHighlight * 0.6f, 1.0f);
            });
        }

        public static Texture2D GetOrCreateTropicalGrassTexture()
        {
            return GetOrCreateTexture("Tex_Tropical_Grass.png", 512, 512, (x, y, w, h) =>
            {
                // Multi-tone lush Kerala roadside grass with soil undertones
                float n1 = Mathf.PerlinNoise(x * 0.05f, y * 0.05f);
                float n2 = Mathf.PerlinNoise(x * 0.25f, y * 0.25f);

                float r = 0.18f + n1 * 0.08f + n2 * 0.04f;
                float g = 0.42f + n1 * 0.15f + n2 * 0.06f;
                float b = 0.12f + n1 * 0.06f;

                return new Color(Mathf.Clamp01(r), Mathf.Clamp01(g), Mathf.Clamp01(b), 1.0f);
            });
        }

        public static Texture2D GetOrCreateShopSignboardTexture(string brand)
        {
            string fileName = $"Tex_Sign_{brand.Replace(" ", "_")}.png";
            return GetOrCreateTexture(fileName, 256, 128, (x, y, w, h) =>
            {
                float border = 6f;
                bool isBorder = x < border || x > w - border || y < border || y > h - border;

                if (brand.Contains("Milma"))
                {
                    // Iconic Milma Dairy Blue/White Kerala branding
                    if (isBorder) return new Color(0.95f, 0.95f, 0.95f, 1f);
                    if (y > h * 0.65f) return new Color(0.08f, 0.32f, 0.75f, 1f); // Blue header
                    return new Color(0.92f, 0.95f, 1.0f, 1f);
                }
                else if (brand.Contains("Hotel"))
                {
                    // Kerala Vegetarian/Non-Veg Hotel (Green/Yellow border)
                    if (isBorder) return new Color(0.95f, 0.85f, 0.15f, 1f);
                    return new Color(0.12f, 0.45f, 0.22f, 1f);
                }
                else if (brand.Contains("Bakery"))
                {
                    // Kerala Traditional Bakery (Warm Red/Cream)
                    if (isBorder) return new Color(0.98f, 0.92f, 0.85f, 1f);
                    return new Color(0.72f, 0.16f, 0.14f, 1f);
                }
                else
                {
                    // Medical Store / General Store (Teal/White)
                    if (isBorder) return new Color(0.15f, 0.65f, 0.55f, 1f);
                    return new Color(0.95f, 0.97f, 0.98f, 1f);
                }
            });
        }

        private static Texture2D GetOrCreateTexture(string fileName, int width, int height, Func<int, int, int, int, Color> pixelShader)
        {
            string projectPath = Application.dataPath.Substring(0, Application.dataPath.Length - "Assets".Length);
            string fullDir = Path.Combine(projectPath, TexturesDir);
            string fullPath = Path.Combine(fullDir, fileName);

            if (!Directory.Exists(fullDir))
            {
                Directory.CreateDirectory(fullDir);
            }

            // Prefer photorealistic CC0 .jpg if present on disk
            string baseName = Path.GetFileNameWithoutExtension(fileName);
            string jpgPath = Path.Combine(fullDir, baseName + ".jpg");
            if (File.Exists(jpgPath))
            {
                try
                {
                    byte[] bytes = File.ReadAllBytes(jpgPath);
                    var diskTex = new Texture2D(2, 2, TextureFormat.RGBA32, true);
                    diskTex.name = baseName;
                    diskTex.wrapMode = TextureWrapMode.Repeat;
                    diskTex.filterMode = FilterMode.Bilinear;
                    if (diskTex.LoadImage(bytes))
                    {
                        return diskTex;
                    }
                }
                catch (Exception ex)
                {
                    Debug.LogWarning($"[ProceduralTextureFactory] Failed to read {jpgPath}: {ex.Message}");
                }
            }

            // Generate texture pixels
            var tex = new Texture2D(width, height, TextureFormat.RGBA32, true);
            tex.name = Path.GetFileNameWithoutExtension(fileName);
            tex.wrapMode = TextureWrapMode.Repeat;
            tex.filterMode = FilterMode.Bilinear;

            var pixels = new Color[width * height];
            for (int y = 0; y < height; y++)
            {
                int rowOffset = y * width;
                for (int x = 0; x < width; x++)
                {
                    pixels[rowOffset + x] = pixelShader(x, y, width, height);
                }
            }

            tex.SetPixels(pixels);
            tex.Apply(true);

            // Persist as PNG for standalone builds
            try
            {
                byte[] pngBytes = tex.EncodeToPNG();
                File.WriteAllBytes(fullPath, pngBytes);
            }
            catch (Exception ex)
            {
                Debug.LogWarning($"[ProceduralTextureFactory] Failed to save {fileName} to disk: {ex.Message}");
            }

            return tex;
        }
    }
}
