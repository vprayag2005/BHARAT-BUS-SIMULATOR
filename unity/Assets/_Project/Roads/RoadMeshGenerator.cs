using System;
using System.Collections.Generic;
using UnityEngine;
using BharatBusSim.Environment;

namespace BharatBusSim.Roads
{
    /// <summary>
    /// Generates realistic Kerala road networks with PBR asphalt, cambered cross-sections,
    /// laterite gravel shoulders, authentic IRC dashed/solid markings, and hazard-striped curbs.
    /// </summary>
    public static class RoadMeshGenerator
    {
        /// <summary>
        /// Densifies mapped route waypoints with a centripetal Catmull-Rom curve that passes
        /// through every source point. Distance-based knot spacing avoids loops and cusps where
        /// mapped waypoint intervals vary sharply.
        /// </summary>
        public static List<Vector3> SmoothMappedWaypoints(IList<Vector3> source, int subdivisions = 4)
        {
            var result = new List<Vector3>();
            if (source == null || source.Count == 0) return result;
            if (source.Count < 3 || subdivisions < 1)
            {
                result.AddRange(source);
                return result;
            }

            subdivisions = Mathf.Clamp(subdivisions, 2, 8);
            for (int i = 0; i < source.Count - 1; i++)
            {
                Vector3 p0 = source[Mathf.Max(0, i - 1)];
                Vector3 p1 = source[i];
                Vector3 p2 = source[i + 1];
                Vector3 p3 = source[Mathf.Min(source.Count - 1, i + 2)];
                for (int j = 0; j < subdivisions; j++)
                {
                    float t = j / (float)subdivisions;
                    Vector3 point = CentripetalCatmullRom(p0, p1, p2, p3, t);
                    if (result.Count == 0 || Vector3.Distance(result[result.Count - 1], point) > 0.01f)
                        result.Add(point);
                }
            }
            result.Add(source[source.Count - 1]);
            return result;
        }

        private static Vector3 CentripetalCatmullRom(Vector3 p0, Vector3 p1, Vector3 p2, Vector3 p3, float u)
        {
            float t0 = 0f;
            float t1 = t0 + Mathf.Max(0.0001f, Mathf.Sqrt(Vector3.Distance(p0, p1)));
            float t2 = t1 + Mathf.Max(0.0001f, Mathf.Sqrt(Vector3.Distance(p1, p2)));
            float t3 = t2 + Mathf.Max(0.0001f, Mathf.Sqrt(Vector3.Distance(p2, p3)));
            float t = Mathf.Lerp(t1, t2, Mathf.Clamp01(u));

            Vector3 a1 = Vector3.LerpUnclamped(p0, p1, (t - t0) / (t1 - t0));
            Vector3 a2 = Vector3.LerpUnclamped(p1, p2, (t - t1) / (t2 - t1));
            Vector3 a3 = Vector3.LerpUnclamped(p2, p3, (t - t2) / (t3 - t2));
            Vector3 b1 = Vector3.LerpUnclamped(a1, a2, (t - t0) / (t2 - t0));
            Vector3 b2 = Vector3.LerpUnclamped(a2, a3, (t - t1) / (t3 - t1));
            return Vector3.LerpUnclamped(b1, b2, (t - t1) / (t2 - t1));
        }

        /// <summary>
        /// Builds a mapped polyline road without changing its plan alignment. The route-specific
        /// builder passes OSM/route waypoints here; straight edges continue to use the legacy path.
        /// The road surface and both shoulders share one mesh and one collider so collision cannot
        /// drift away from its rendered pavement.
        /// </summary>
        public static GameObject BuildRoadPath(Transform parent, string roadName, IList<Vector3> centerline,
            float asphaltWidth, float shoulderWidth, int lanes, bool dividedCarriageway,
            Material asphalt, Material shoulderMaterial, Material whiteMarking, Material yellowMarking,
            bool edgeLinesFollowPath = false)
        {
            if (parent == null || centerline == null || centerline.Count < 2 || asphaltWidth <= 0.1f)
                return null;

            var points = new List<Vector3>(centerline.Count);
            for (int i = 0; i < centerline.Count; i++)
            {
                Vector3 point = centerline[i];
                if (points.Count == 0 || Vector3.Distance(points[points.Count - 1], point) > 0.05f)
                    points.Add(point);
            }
            if (points.Count < 2) return null;

            var road = new GameObject(roadName);
            road.transform.SetParent(parent, false);
            road.transform.localPosition = Vector3.zero;
            road.transform.localRotation = Quaternion.identity;
            road.transform.localScale = Vector3.one;

            float halfAsphalt = asphaltWidth * 0.5f;
            float shoulder = Mathf.Max(0f, shoulderWidth);
            var vertices = new List<Vector3>(points.Count * 4);
            var uvs = new List<Vector2>(points.Count * 4);
            var roadTriangles = new List<int>((points.Count - 1) * 6);
            var shoulderTriangles = new List<int>((points.Count - 1) * 12);
            float distance = 0f;

            for (int i = 0; i < points.Count; i++)
            {
                Vector3 tangent = i == 0 ? points[1] - points[0] :
                    (i == points.Count - 1 ? points[i] - points[i - 1] : points[i + 1] - points[i - 1]);
                tangent.y = 0f;
                if (tangent.sqrMagnitude < 0.0001f) tangent = Vector3.forward;
                tangent.Normalize();
                Vector3 side = Vector3.Cross(Vector3.up, tangent).normalized;

                if (i > 0) distance += Vector3.Distance(points[i - 1], points[i]);
                Vector3 center = parent.InverseTransformPoint(points[i]);
                Vector3 localSide = parent.InverseTransformVector(side).normalized;

                vertices.Add(center - localSide * (halfAsphalt + shoulder) + Vector3.down * 0.025f);
                vertices.Add(center - localSide * halfAsphalt + Vector3.up * 0.035f);
                vertices.Add(center + localSide * halfAsphalt + Vector3.up * 0.035f);
                vertices.Add(center + localSide * (halfAsphalt + shoulder) + Vector3.down * 0.025f);

                float v = distance * 0.25f;
                uvs.Add(new Vector2(0f, v));
                uvs.Add(new Vector2(0.05f, v));
                uvs.Add(new Vector2(1.95f, v));
                uvs.Add(new Vector2(2f, v));

                if (i >= points.Count - 1) continue;
                int b = i * 4;
                shoulderTriangles.AddRange(new[] { b, b + 4, b + 1, b + 1, b + 4, b + 5,
                    b + 2, b + 6, b + 3, b + 3, b + 6, b + 7 });
                roadTriangles.AddRange(new[] { b + 1, b + 5, b + 2, b + 2, b + 5, b + 6 });
            }

            var mesh = new Mesh { name = $"Mesh_{roadName}" };
            if (vertices.Count > 65000) mesh.indexFormat = UnityEngine.Rendering.IndexFormat.UInt32;
            mesh.subMeshCount = 2;
            mesh.SetVertices(vertices);
            mesh.SetUVs(0, uvs);
            mesh.SetTriangles(roadTriangles, 0);
            mesh.SetTriangles(shoulderTriangles, 1);
            mesh.RecalculateNormals();
            mesh.RecalculateBounds();

            var filter = road.AddComponent<MeshFilter>();
            filter.sharedMesh = mesh;
            var renderer = road.AddComponent<MeshRenderer>();
            renderer.sharedMaterials = new[] { asphalt, shoulderMaterial != null ? shoulderMaterial : asphalt };
            var collider = road.AddComponent<MeshCollider>();
            collider.sharedMesh = mesh;
            collider.convex = false;

            float edgeOffset = Mathf.Max(0f, halfAsphalt - 0.24f);
            CreatePathStripe(road.transform, points, -edgeOffset, 0.12f, whiteMarking, "EdgeLine_Left", edgeLinesFollowPath);
            CreatePathStripe(road.transform, points, edgeOffset, 0.12f, whiteMarking, "EdgeLine_Right", edgeLinesFollowPath);

            if (dividedCarriageway)
            {
                for (int lane = 1; lane < lanes; lane++)
                {
                    float offset = -halfAsphalt + asphaltWidth * lane / Mathf.Max(1, lanes);
                    CreatePathDashes(road.transform, points, offset, 0.13f, 2.4f, 3.0f, whiteMarking, $"LaneDivider_{lane}");
                }
            }
            else if (lanes >= 2)
            {
                CreatePathDashes(road.transform, points, 0f, 0.15f, 2.0f, 2.5f, yellowMarking, "Centerline_Dashed");
            }

            return road;
        }

        private static void CreatePathStripe(Transform parent, IList<Vector3> points, float lateralOffset,
            float width, Material material, string name, bool followPath = false)
        {
            if (followPath)
            {
                CreatePathRibbon(parent, points, lateralOffset, width, material, name);
                return;
            }
            CreatePathMarkingMesh(parent, points, lateralOffset, width, material, name, false, 0f, 0f);
        }

        /// <summary>
        /// Solid edge line that follows every station of its centreline instead of spanning the
        /// whole path as one chord. Used where a straight chord would cut across the pavement,
        /// such as the curved High Road turnaround loop.
        /// </summary>
        private static void CreatePathRibbon(Transform parent, IList<Vector3> points, float lateralOffset,
            float width, Material material, string name)
        {
            if (material == null || points == null || points.Count < 2) return;
            int count = points.Count;
            var vertices = new List<Vector3>(count * 2);
            var uvs = new List<Vector2>(count * 2);
            var triangles = new List<int>((count - 1) * 6);
            float halfWidth = width * 0.5f;
            float distance = 0f;

            for (int i = 0; i < count; i++)
            {
                Vector3 tangent = i == 0 ? points[1] - points[0] :
                    (i == count - 1 ? points[i] - points[i - 1] : points[i + 1] - points[i - 1]);
                tangent.y = 0f;
                if (tangent.sqrMagnitude < 0.0001f) continue;
                tangent.Normalize();
                Vector3 side = Vector3.Cross(Vector3.up, tangent).normalized;

                if (i > 0) distance += Vector3.Distance(points[i - 1], points[i]);
                Vector3 centre = points[i] + side * lateralOffset + Vector3.up * 0.13f;
                Vector3 left = parent.InverseTransformPoint(centre - side * halfWidth);
                Vector3 right = parent.InverseTransformPoint(centre + side * halfWidth);
                int index = vertices.Count;
                vertices.Add(left);
                vertices.Add(right);
                uvs.Add(new Vector2(0f, distance * 0.25f));
                uvs.Add(new Vector2(1f, distance * 0.25f));

                if (index < 2) continue;
                int previous = index - 2;
                triangles.AddRange(new[] { previous, index, previous + 1, previous + 1, index, index + 1 });
            }
            if (triangles.Count == 0) return;

            var go = new GameObject(name);
            go.transform.SetParent(parent, false);
            var mesh = new Mesh { name = $"Mesh_{name}" };
            if (vertices.Count > 65000) mesh.indexFormat = UnityEngine.Rendering.IndexFormat.UInt32;
            mesh.SetVertices(vertices);
            mesh.SetUVs(0, uvs);
            mesh.SetTriangles(triangles, 0);
            mesh.RecalculateNormals();
            mesh.RecalculateBounds();
            go.AddComponent<MeshFilter>().sharedMesh = mesh;
            go.AddComponent<MeshRenderer>().sharedMaterial = material;
        }

        private static void CreatePathDashes(Transform parent, IList<Vector3> points, float lateralOffset,
            float width, float dashLength, float gapLength, Material material, string name)
        {
            CreatePathMarkingMesh(parent, points, lateralOffset, width, material, name, true, dashLength, gapLength);
        }

        private static void CreatePathMarkingMesh(Transform parent, IList<Vector3> points, float lateralOffset,
            float width, Material material, string name, bool dashed, float dashLength, float gapLength)
        {
            if (material == null || points == null || points.Count < 2) return;
            var cumulative = new float[points.Count];
            for (int i = 1; i < points.Count; i++) cumulative[i] = cumulative[i - 1] + Vector3.Distance(points[i - 1], points[i]);
            float total = cumulative[points.Count - 1];
            if (total < 0.1f) return;

            var vertices = new List<Vector3>();
            var uvs = new List<Vector2>();
            var triangles = new List<int>();
            float step = dashed ? dashLength + gapLength : total;
            for (float start = 0f; start < total; start += step)
            {
                float end = dashed ? Mathf.Min(start + dashLength, total) : total;
                if (dashed && end - start < dashLength * 0.45f) continue;
                Vector3 a = SampleOffsetPoint(points, cumulative, start, lateralOffset);
                Vector3 b = SampleOffsetPoint(points, cumulative, end, lateralOffset);
                Vector3 direction = b - a;
                direction.y = 0f;
                if (direction.sqrMagnitude < 0.0001f) continue;
                Vector3 side = Vector3.Cross(Vector3.up, direction.normalized).normalized * (width * 0.5f);
                a = parent.InverseTransformPoint(a + Vector3.up * 0.13f);
                b = parent.InverseTransformPoint(b + Vector3.up * 0.13f);
                side = parent.InverseTransformVector(side);
                int index = vertices.Count;
                vertices.Add(a - side); vertices.Add(a + side); vertices.Add(b + side); vertices.Add(b - side);
                uvs.Add(new Vector2(0f, 0f)); uvs.Add(new Vector2(1f, 0f));
                uvs.Add(new Vector2(1f, Vector3.Distance(a, b))); uvs.Add(new Vector2(0f, Vector3.Distance(a, b)));
                triangles.AddRange(new[] { index, index + 2, index + 1, index, index + 3, index + 2 });
                if (!dashed) break;
            }
            if (vertices.Count == 0) return;
            var go = new GameObject(name);
            go.transform.SetParent(parent, false);
            var mesh = new Mesh { name = $"Mesh_{name}" };
            mesh.SetVertices(vertices); mesh.SetUVs(0, uvs); mesh.SetTriangles(triangles, 0);
            mesh.RecalculateNormals(); mesh.RecalculateBounds();
            go.AddComponent<MeshFilter>().sharedMesh = mesh;
            go.AddComponent<MeshRenderer>().sharedMaterial = material;
        }

        private static Vector3 SampleOffsetPoint(IList<Vector3> points, float[] cumulative, float distance, float lateralOffset)
        {
            int index = 0;
            while (index < cumulative.Length - 2 && cumulative[index + 1] < distance) index++;
            float span = cumulative[index + 1] - cumulative[index];
            float t = span > 0.0001f ? Mathf.Clamp01((distance - cumulative[index]) / span) : 0f;
            Vector3 point = Vector3.Lerp(points[index], points[index + 1], t);
            Vector3 tangent = points[index + 1] - points[index];
            tangent.y = 0f;
            if (tangent.sqrMagnitude > 0.0001f)
                point += Vector3.Cross(Vector3.up, tangent.normalized) * lateralOffset;
            return point;
        }

        public static GameObject BuildRoadNetwork(Transform parent, Dictionary<string, RoadNode> nodes, List<RoadEdge> edges, Material roadMat, Material curbMat, Material markingMat, Material medianMat, bool useKitForHighways = false)
        {
            var networkGo = new GameObject("Thrissur_Road_Network");
            networkGo.transform.SetParent(parent, false);

            // Create gravel/laterite shoulder material
            Shader litShader = roadMat.shader;
            Material shoulderMat = new Material(litShader)
            {
                name = "Mat_Road_Shoulder_Laterite",
                mainTexture = ProceduralTextureFactory.GetOrCreateLateriteSoilTexture(),
                color = new Color(0.95f, 0.92f, 0.88f)
            };
            shoulderMat.mainTextureScale = new Vector2(2f, 30f);

            // Create yellow marking material
            Material yellowMarkingMat = new Material(litShader)
            {
                name = "Mat_Yellow_Centerline",
                color = new Color(0.96f, 0.78f, 0.10f)
            };

            // Calculate node connectivity to handle seamless intersections and clean marking setbacks
            var nodeDegree = new Dictionary<string, int>();
            var nodeMaxWidth = new Dictionary<string, float>();
            foreach (var edge in edges)
            {
                if (!nodeDegree.ContainsKey(edge.fromNodeId)) nodeDegree[edge.fromNodeId] = 0;
                if (!nodeDegree.ContainsKey(edge.toNodeId)) nodeDegree[edge.toNodeId] = 0;
                nodeDegree[edge.fromNodeId]++;
                nodeDegree[edge.toNodeId]++;

                if (!nodeMaxWidth.ContainsKey(edge.fromNodeId) || edge.width > nodeMaxWidth[edge.fromNodeId])
                    nodeMaxWidth[edge.fromNodeId] = edge.width;
                if (!nodeMaxWidth.ContainsKey(edge.toNodeId) || edge.width > nodeMaxWidth[edge.toNodeId])
                    nodeMaxWidth[edge.toNodeId] = edge.width;
            }

            // Build Junction Hubs at intersections with 2 or more connections to seal all grass gaps
            int junctionIdx = 0;
            foreach (var kvp in nodeDegree)
            {
                if (kvp.Value >= 2 && nodes.ContainsKey(kvp.Key))
                {
                    var node = nodes[kvp.Key];
                    float r = nodeMaxWidth.ContainsKey(kvp.Key) ? nodeMaxWidth[kvp.Key] * 0.58f : 8.0f;
                    BuildJunctionHub(networkGo.transform, node.unityPosition, r, roadMat, junctionIdx++);
                }
            }

            int edgeIndex = 0;
            foreach (var edge in edges)
            {
                if (useKitForHighways && (edge.classification == RoadClassification.NationalHighway || edge.classification == RoadClassification.TollPlaza))
                    continue;
                if (!nodes.ContainsKey(edge.fromNodeId) || !nodes.ContainsKey(edge.toNodeId))
                    continue;

                RoadNode fromNode = nodes[edge.fromNodeId];
                RoadNode toNode = nodes[edge.toNodeId];

                float fromOffset = (nodeDegree.ContainsKey(edge.fromNodeId) && nodeDegree[edge.fromNodeId] >= 2) ? edge.width * 0.45f : 0f;
                float toOffset = (nodeDegree.ContainsKey(edge.toNodeId) && nodeDegree[edge.toNodeId] >= 2) ? edge.width * 0.45f : 0f;

                BuildRoadSegment(networkGo.transform, edge, fromNode, toNode, roadMat, shoulderMat, curbMat, markingMat, yellowMarkingMat, medianMat, edgeIndex++, fromOffset, toOffset);
            }

            return networkGo;
        }

        private static void BuildJunctionHub(Transform parent, Vector3 center, float radius, Material roadMat, int index)
        {
            var hubGo = new GameObject($"Road_Junction_{index:D2}");
            hubGo.transform.SetParent(parent, false);

            int segments = 16;
            var vertices = new Vector3[segments + 1];
            var uvs = new Vector2[segments + 1];
            var tris = new int[segments * 3];

            Vector3 hubCenter = center + Vector3.up * 0.12f;
            vertices[0] = hubCenter;
            uvs[0] = new Vector2(hubCenter.x * 0.25f, hubCenter.z * 0.25f);

            for (int i = 0; i < segments; i++)
            {
                float angle = i * Mathf.PI * 2f / segments;
                Vector3 p = hubCenter + new Vector3(Mathf.Cos(angle) * radius, 0f, Mathf.Sin(angle) * radius);
                vertices[i + 1] = p;
                uvs[i + 1] = new Vector2(p.x * 0.25f, p.z * 0.25f);

                int next = (i + 1) % segments;
                tris[i * 3] = 0;
                tris[i * 3 + 1] = i + 1;
                tris[i * 3 + 2] = next + 1;
            }

            var mesh = new Mesh();
            mesh.name = $"Mesh_Junction_{index:D2}";
            mesh.vertices = vertices;
            mesh.uv = uvs;
            mesh.triangles = tris;
            mesh.RecalculateNormals();
            mesh.RecalculateBounds();

            var mf = hubGo.AddComponent<MeshFilter>();
            mf.sharedMesh = mesh;

            var mr = hubGo.AddComponent<MeshRenderer>();
            mr.sharedMaterial = roadMat;

            var mc = hubGo.AddComponent<MeshCollider>();
            mc.sharedMesh = mesh;
        }

        private static void BuildRoadSegment(Transform parent, RoadEdge edge, RoadNode from, RoadNode to,
            Material roadMat, Material shoulderMat, Material curbMat, Material whiteMarkMat, Material yellowMarkMat, Material medianMat, int index, float fromOffset, float toOffset)
        {
            Vector3 start = from.unityPosition;
            Vector3 end = to.unityPosition;
            Vector3 delta = end - start;
            float length = delta.magnitude;
            if (length < 0.1f) return;

            Vector3 dir = delta.normalized;
            Vector3 normal = Vector3.up;
            Vector3 side = Vector3.Cross(normal, dir).normalized;

            var segmentGo = new GameObject($"Road_{index:D2}_{edge.id}");
            segmentGo.transform.SetParent(parent, false);

            float halfWidth = edge.width * 0.5f;
            float shoulderWidth = 1.0f;

            // 1. Build Multi-Section Cambered Road Mesh with Shoulders
            // Subdivide into smooth 16m steps
            int steps = Mathf.Max(2, Mathf.CeilToInt(length / 16f));

            var vertices = new List<Vector3>();
            var uvs = new List<Vector2>();
            var roadTris = new List<int>();
            var shoulderTris = new List<int>();

            // Each cross-section has 4 vertices:
            // 0: Left Shoulder Outer (Y - 0.04m)
            // 1: Left Asphalt Edge (Y = 0.06m)
            // 2: Right Asphalt Edge (Y = 0.06m)
            // 3: Right Shoulder Outer (Y - 0.04m)

            for (int i = 0; i <= steps; i++)
            {
                float t = (float)i / steps;
                Vector3 center = Vector3.Lerp(start, end, t);

                Vector3 vShoulderL = center - side * (halfWidth + shoulderWidth) + Vector3.up * 0.01f;
                Vector3 vAsphaltL = center - side * halfWidth + Vector3.up * 0.12f;
                Vector3 vAsphaltR = center + side * halfWidth + Vector3.up * 0.12f;
                Vector3 vShoulderR = center + side * (halfWidth + shoulderWidth) + Vector3.up * 0.01f;

                vertices.Add(vShoulderL);
                vertices.Add(vAsphaltL);
                vertices.Add(vAsphaltR);
                vertices.Add(vShoulderR);

                float vCoord = (length * t) * 0.25f; // UV tiling: 1 repeat per 4 meters along length
                uvs.Add(new Vector2(0f, vCoord));
                uvs.Add(new Vector2(0.05f, vCoord));
                uvs.Add(new Vector2(1.95f, vCoord));
                uvs.Add(new Vector2(2f, vCoord));

                if (i < steps)
                {
                    int b = i * 4;

                    // Left Shoulder Quad: (b, b+1, b+4) & (b+1, b+5, b+4)
                    shoulderTris.Add(b);
                    shoulderTris.Add(b + 1);
                    shoulderTris.Add(b + 4);
                    shoulderTris.Add(b + 1);
                    shoulderTris.Add(b + 5);
                    shoulderTris.Add(b + 4);

                    // Asphalt Main Road Quad: (b+1, b+2, b+5) & (b+2, b+6, b+5)
                    roadTris.Add(b + 1);
                    roadTris.Add(b + 2);
                    roadTris.Add(b + 5);
                    roadTris.Add(b + 2);
                    roadTris.Add(b + 6);
                    roadTris.Add(b + 5);

                    // Right Shoulder Quad: (b+2, b+3, b+6) & (b+3, b+7, b+6)
                    shoulderTris.Add(b + 2);
                    shoulderTris.Add(b + 3);
                    shoulderTris.Add(b + 6);
                    shoulderTris.Add(b + 3);
                    shoulderTris.Add(b + 7);
                    shoulderTris.Add(b + 6);
                }
            }

            var mesh = new Mesh();
            mesh.name = $"Mesh_{edge.id}";
            mesh.subMeshCount = 2;
            mesh.SetVertices(vertices);
            mesh.SetUVs(0, uvs);
            mesh.SetTriangles(roadTris, 0);       // Sub-mesh 0: Asphalt
            mesh.SetTriangles(shoulderTris, 1);   // Sub-mesh 1: Gravel shoulders
            mesh.RecalculateNormals();
            mesh.RecalculateBounds();

            var mf = segmentGo.AddComponent<MeshFilter>();
            mf.sharedMesh = mesh;

            var mr = segmentGo.AddComponent<MeshRenderer>();
            mr.sharedMaterials = new Material[] { roadMat, shoulderMat };

            // Accurate MeshCollider for stable wheel physics
            var mc = segmentGo.AddComponent<MeshCollider>();
            mc.sharedMesh = mesh;

            // 2. Setback-Aware Corridor Span for Curbs, Median Barriers, and Road Markings
            // Prevents line crossover, overlap, and road clutter at intersections
            Vector3 mStart = start + dir * fromOffset;
            Vector3 mEnd = end - dir * toOffset;
            float mLength = Vector3.Dot(mEnd - mStart, dir);
            bool validMarkRange = mLength >= 2.0f;

            // Curbs for Urban & Terminal Segments
            bool isUrban = edge.classification == RoadClassification.Roundabout ||
                           edge.classification == RoadClassification.CityArterial ||
                           edge.classification == RoadClassification.TerminalLane;

            if (isUrban && validMarkRange)
            {
                CreateKerbStrip(segmentGo.transform, mStart - side * (halfWidth + 0.15f), mEnd - side * (halfWidth + 0.15f), 0.35f, 0.16f, curbMat, "Kerb_L");
                CreateKerbStrip(segmentGo.transform, mStart + side * (halfWidth + 0.15f), mEnd + side * (halfWidth + 0.15f), 0.35f, 0.16f, curbMat, "Kerb_R");
            }

            // 3. Realistic IRC Pavement Markings (Terminated cleanly outside junctions)
            if (validMarkRange)
            {
                if (edge.classification == RoadClassification.NationalHighway && edge.lanes >= 4)
                {
                    // Concrete New Jersey Median Barrier on NH 544
                    CreateMedianJerseyBarrier(segmentGo.transform, mStart, mEnd, 0.85f, 0.80f, medianMat, "NH544_Jersey_Barrier");

                    // White Solid Edge Lines on outer shoulders (15cm)
                    float outerLineOffset = halfWidth - 0.25f;
                    CreateContinuousStripe(segmentGo.transform, mStart - side * outerLineOffset, mEnd - side * outerLineOffset, 0.15f, whiteMarkMat, "EdgeLine_L");
                    CreateContinuousStripe(segmentGo.transform, mStart + side * outerLineOffset, mEnd + side * outerLineOffset, 0.15f, whiteMarkMat, "EdgeLine_R");

                    // Dashed Lane Dividers (4m stripe, 6m gap) for each carriage way
                    float laneDividerOffset = halfWidth * 0.5f;
                    CreateDashedLaneLine(segmentGo.transform, mStart - side * laneDividerOffset, mEnd - side * laneDividerOffset, 0.14f, 4.0f, 6.0f, whiteMarkMat, "LaneDash_L");
                    CreateDashedLaneLine(segmentGo.transform, mStart + side * laneDividerOffset, mEnd + side * laneDividerOffset, 0.14f, 4.0f, 6.0f, whiteMarkMat, "LaneDash_R");
                }
                else
                {
                    // Solid White Edge Lines (12cm)
                    float edgeOffset = halfWidth - 0.20f;
                    CreateContinuousStripe(segmentGo.transform, mStart - side * edgeOffset, mEnd - side * edgeOffset, 0.12f, whiteMarkMat, "EdgeLine_L");
                    CreateContinuousStripe(segmentGo.transform, mStart + side * edgeOffset, mEnd + side * edgeOffset, 0.12f, whiteMarkMat, "EdgeLine_R");

                    // Centerline:
                    // Double yellow for Swaraj Round and terminal approaches, dashed yellow for rural state highways
                    if (edge.classification == RoadClassification.Roundabout || edge.classification == RoadClassification.TerminalLane)
                    {
                        CreateContinuousStripe(segmentGo.transform, mStart - side * 0.12f, mEnd - side * 0.12f, 0.10f, yellowMarkMat, "DoubleYellow_L");
                        CreateContinuousStripe(segmentGo.transform, mStart + side * 0.12f, mEnd + side * 0.12f, 0.10f, yellowMarkMat, "DoubleYellow_R");
                    }
                    else
                    {
                        // 2-Lane Highway Dashed Centerline (3.5m line, 5.0m gap)
                        CreateDashedLaneLine(segmentGo.transform, mStart, mEnd, 0.14f, 3.5f, 5.0f, yellowMarkMat, "Centerline_Dashed");
                    }
                }
            }

            // Highway & Arterial Street Lighting (every 45m along right roadside)
            if ((edge.classification == RoadClassification.NationalHighway || edge.classification == RoadClassification.CityArterial) && length >= 35.0f)
            {
                PlaceStreetLamps(segmentGo.transform, start, end, side, halfWidth);
            }
        }

        private static void PlaceStreetLamps(Transform parent, Vector3 start, Vector3 end, Vector3 side, float halfWidth)
        {
#if UNITY_EDITOR
            var prefab = UnityEditor.AssetDatabase.LoadAssetAtPath<GameObject>("Assets/_Project/Models/street_lamp_01_1k.fbx");
            if (prefab == null) return;

            Vector3 delta = end - start;
            float len = delta.magnitude;
            int count = Mathf.Max(1, Mathf.FloorToInt(len / 45.0f));

            for (int i = 0; i < count; i++)
            {
                float t = (i + 0.5f) / count;
                Vector3 pos = Vector3.Lerp(start, end, t) + side * (halfWidth + 1.2f) + Vector3.up * 0.05f;
                var lamp = (GameObject)UnityEditor.PrefabUtility.InstantiatePrefab(prefab, parent);
                if (lamp != null)
                {
                    lamp.name = $"Street_Lamp_{i}";
                    lamp.transform.position = pos;
                    lamp.transform.rotation = Quaternion.LookRotation(-side, Vector3.up);
                    lamp.transform.localScale = Vector3.one * 1.3f;

                    // Remove colliders so lamps never physically obstruct vehicles
                    var cols = lamp.GetComponentsInChildren<Collider>();
                    for (int c = 0; c < cols.Length; c++) UnityEngine.Object.DestroyImmediate(cols[c]);
                }
            }
#endif
        }

        private static void CreateKerbStrip(Transform parent, Vector3 start, Vector3 end, float width, float height, Material mat, string name)
        {
            Vector3 delta = end - start;
            float length = delta.magnitude;
            if (length < 0.2f) return;

            var kerb = GameObject.CreatePrimitive(PrimitiveType.Cube);
            kerb.name = name;
            kerb.transform.SetParent(parent, false);
            kerb.transform.position = (start + end) * 0.5f + Vector3.up * (height * 0.5f + 0.12f);
            kerb.transform.rotation = Quaternion.LookRotation(delta.normalized, Vector3.up);
            kerb.transform.localScale = new Vector3(width, height, length);
            kerb.GetComponent<Renderer>().sharedMaterial = mat;

            // Remove box collider so wheels never get snagged on curbs
            var col = kerb.GetComponent<Collider>();
            if (col != null) UnityEngine.Object.DestroyImmediate(col);
        }

        private static void CreateMedianJerseyBarrier(Transform parent, Vector3 start, Vector3 end, float width, float height, Material mat, string name)
        {
            Vector3 delta = end - start;
            float length = delta.magnitude;
            if (length < 0.2f) return;

            var barrier = GameObject.CreatePrimitive(PrimitiveType.Cube);
            barrier.name = name;
            barrier.transform.SetParent(parent, false);
            barrier.transform.position = (start + end) * 0.5f + Vector3.up * (height * 0.5f + 0.12f);
            barrier.transform.rotation = Quaternion.LookRotation(delta.normalized, Vector3.up);
            barrier.transform.localScale = new Vector3(width, height, length);
            barrier.GetComponent<Renderer>().sharedMaterial = mat;

            // Median barrier retains collision for highway safety
            var bc = barrier.GetComponent<BoxCollider>();
            if (bc != null)
            {
                bc.size = new Vector3(width * 0.8f, height, 1.0f);
            }
        }

        private static void CreateContinuousStripe(Transform parent, Vector3 start, Vector3 end, float width, Material mat, string name)
        {
            Vector3 delta = end - start;
            float length = delta.magnitude;
            if (length < 0.2f) return;

            var stripe = GameObject.CreatePrimitive(PrimitiveType.Cube);
            stripe.name = name;
            stripe.transform.SetParent(parent, false);
            stripe.transform.position = (start + end) * 0.5f + Vector3.up * 0.125f;
            stripe.transform.rotation = Quaternion.LookRotation(delta.normalized, Vector3.up);
            stripe.transform.localScale = new Vector3(width, 0.002f, length);
            stripe.GetComponent<Renderer>().sharedMaterial = mat;

            var col = stripe.GetComponent<Collider>();
            if (col != null) UnityEngine.Object.DestroyImmediate(col);
        }

        private static void CreateDashedLaneLine(Transform parent, Vector3 start, Vector3 end, float width, float dashLen, float gapLen, Material mat, string namePrefix)
        {
            Vector3 delta = end - start;
            float totalLen = delta.magnitude;
            if (totalLen < dashLen) return;

            Vector3 dir = delta.normalized;
            float cycle = dashLen + gapLen;
            int count = Mathf.FloorToInt(totalLen / cycle);

            var dashesRoot = new GameObject(namePrefix);
            dashesRoot.transform.SetParent(parent, false);

            for (int i = 0; i < count; i++)
            {
                float dStart = i * cycle;
                Vector3 segPos = start + dir * (dStart + dashLen * 0.5f) + Vector3.up * 0.125f;

                var dash = GameObject.CreatePrimitive(PrimitiveType.Cube);
                dash.name = $"Dash_{i}";
                dash.transform.SetParent(dashesRoot.transform, false);
                dash.transform.position = segPos;
                dash.transform.rotation = Quaternion.LookRotation(dir, Vector3.up);
                dash.transform.localScale = new Vector3(width, 0.002f, dashLen);
                dash.GetComponent<Renderer>().sharedMaterial = mat;

                var col = dash.GetComponent<Collider>();
                if (col != null) UnityEngine.Object.DestroyImmediate(col);
            }
        }
    }
}
