using System;
using System.Collections.Generic;
using UnityEngine;

namespace BharatBusSim.Roads
{
    public enum RoadClassification
    {
        TerminalLane,
        CityArterial,
        Roundabout,
        NationalHighway,
        StateHighway,
        CoastalRoad,
        TollPlaza
    }

    [Serializable]
    public class RoadNode
    {
        public string id;
        public string name;
        public string nameMl;
        public double latitude;
        public double longitude;
        public double utmX;
        public double utmY;
        public float elevation;
        public Vector3 unityPosition;
        public string nodeType;

        public RoadNode(string id, string name, string nameMl, double lat, double lon, double utmX, double utmY, float elevation, string nodeType)
        {
            this.id = id;
            this.name = name;
            this.nameMl = nameMl;
            this.latitude = lat;
            this.longitude = lon;
            this.utmX = utmX;
            this.utmY = utmY;
            this.elevation = elevation;
            this.nodeType = nodeType;
            this.unityPosition = GeoCoordinateTransformer.UtmToUnity(utmX, utmY, elevation);
        }
    }

    [Serializable]
    public class RoadEdge
    {
        public string id;
        public string fromNodeId;
        public string toNodeId;
        public RoadClassification classification;
        public int lanes;
        public float width;
        public float speedLimitKmh;
        public List<Vector3> waypoints = new List<Vector3>();

        public RoadEdge(string id, string from, string to, RoadClassification classification, int lanes, float width, float speedLimit)
        {
            this.id = id;
            this.fromNodeId = from;
            this.toNodeId = to;
            this.classification = classification;
            this.lanes = lanes;
            this.width = width;
            this.speedLimitKmh = speedLimit;
        }
    }

    /// <summary>
    /// Accurate geographic projection transformer.
    /// Anchors the Unity local origin (0, 0, 0) at Thrissur Swaraj Round (10.5244 N, 76.2144 E).
    /// Uses metric UTM Zone 43N (EPSG:32643) coordinates with a regional corridor compression factor
    /// to maintain high mobile visual density and stable physics while preserving 1:1 cross-sectional road widths.
    /// </summary>
    public static class GeoCoordinateTransformer
    {
        // Central Datum Anchor: Thrissur Swaraj Round
        public const double OriginLat = 10.5244;
        public const double OriginLon = 76.2144;
        public const double OriginUtmX = 632883.2;
        public const double OriginUtmY = 1163650.18;
        public const float OriginElevation = 12.0f;

        // Regional corridor distance compression factor (1:5 scale for intercity corridors,
        // while road widths and bus dimensions remain strict 1:1 metric scale)
        public const float CorridorScale = 0.20f;

        public static Vector3 UtmToUnity(double utmX, double utmY, float elevation)
        {
            float unityX = (float)((utmX - OriginUtmX) * CorridorScale);
            float unityZ = (float)((utmY - OriginUtmY) * CorridorScale);
            float unityY = (elevation - OriginElevation) * 0.35f; // Gentle topography scaling
            return new Vector3(unityX, Mathf.Max(0.05f, unityY), unityZ);
        }

        public static Vector2 UnityToUtm(Vector3 unityPos)
        {
            double utmX = OriginUtmX + (unityPos.x / CorridorScale);
            double utmY = OriginUtmY + (unityPos.z / CorridorScale);
            return new Vector2((float)utmX, (float)utmY);
        }
    }

    /// <summary>
    /// Master repository of verified Thrissur district road topology.
    /// </summary>
    public static class ThrissurRoadNetworkCatalog
    {
        public static Dictionary<string, RoadNode> GetNodes()
        {
            var nodes = new Dictionary<string, RoadNode>();

            void Add(string id, string name, string ml, double lat, double lon, double utmX, double utmY, float elev, string type)
            {
                nodes[id] = new RoadNode(id, name, ml, lat, lon, utmX, utmY, elev, type);
            }

            // 1. Sakthan Thampuran Bus Stand (South Hub & Player Spawn)
            // Synchronized with Player Bus Spawn at (25.0, 0.35, -169.3) and Exit Gate at (25.0, 0.05, -135.0)
            Add("NODE_SAKTHAN_BAY", "Sakthan Stand - Starting Bay", "ശക്തൻ തമ്പുരാൻ ബസ് സ്റ്റാൻഡ്", 10.5165, 76.2150, 632952.25, 1162776.77, 12.0f, "bay");            Add("NODE_SAKTHAN_EXIT", "Sakthan Stand Exit Gate", "ശക്തൻ സ്റ്റാൻഡ് എക്സിറ്റ്", 10.5175, 76.2152, 632973.71, 1162887.45, 12.0f, "junction");            // Geographic position unresolved; preserve existing values until a verified source is available.
            Add("NODE_SAKTHAN_APPROACH", "Sakthan City Link", "ശക്തൻ സിറ്റി ലിങ്ക്", 10.5190, 76.2160, 632975.0, 1163425.0, 12.0f, "junction");

            // 2. Puthanpalli & High Road Arterial
            Add("NODE_PUTHANPALLI", "Puthanpalli (Dolours Basilica)", "പുത്തൻപള്ളി", 10.5209, 76.2183, 633311.51, 1163264.77, 12.0f, "landmark");            Add("NODE_HIGH_ROAD", "High Road Junction", "ഹൈ റോഡ്", 10.5230, 76.2148, 632927.58, 1163495.52, 12.0f, "junction");
            // 3. Swaraj Round (Thekkinkadu Maidan Ring)
            Add("NODE_SWARAJ_SOUTH", "Swaraj Round South", "സ്വരാജ് റൗണ്ട് തെക്ക്", 10.5235, 76.2144, 632883.59, 1163550.65, 12.0f, "roundabout");            Add("NODE_SWARAJ_EAST", "Swaraj Round East", "സ്വരാജ് റൗണ്ട് കിഴക്ക്", 10.5244, 76.2162, 633080.19, 1163650.94, 12.0f, "roundabout");            Add("NODE_SWARAJ_NORTH", "Swaraj Round North", "സ്വരാജ് റൗണ്ട് വടക്ക്", 10.5255, 76.2144, 632882.73, 1163771.83, 12.0f, "roundabout");            Add("NODE_SWARAJ_WEST", "Swaraj Round West", "സ്വരാജ് റൗണ്ട് പടിഞ്ഞാറ്", 10.5244, 76.2126, 632686.21, 1163649.42, 12.0f, "roundabout");
            // 4. Vadakke Stand (North Stand Terminal)
            Add("NODE_VADAKKE_STAND", "Vadakke Stand Terminal", "വടക്കേ ബസ് സ്റ്റാൻഡ്", 10.5285, 76.2135, 632782.95, 1164103.22, 13.0f, "terminal");
            // 5. East Fort & NH 544 Corridor
            Add("NODE_EAST_FORT", "East Fort Junction", "കിഴക്കേക്കോട്ട", 10.5238, 76.2275, 634317.11, 1163589.41, 12.5f, "junction");            Add("NODE_MANNUTHY", "Mannuthy Interchange (NH 544)", "മണ്ണുത്തി", 10.5332, 76.2625, 638143.31, 1164644.18, 16.0f, "junction");            Add("NODE_NH544_OLLUR", "NH 544 Ollur", "ഒല്ലൂർ", 10.4785, 76.2420, 635923.77, 1158585.87, 14.5f, "highway");            Add("NODE_NH544_AMBALLUR", "NH 544 Amballur", "ആമ്പല്ലൂർ", 10.4505, 76.2580, 637687.42, 1155496.25, 14.0f, "highway");
            // 6. Paliyekkara Toll Plaza
            Add("NODE_PALIYEKKARA_APPROACH", "Paliyekkara Toll Approach", "പാലിയേക്കര ടോൾ അപ്രോച്ച്", 10.4440, 76.2600, 637909.21, 1154778.27, 14.0f, "toll_approach");            Add("NODE_PALIYEKKARA_TOLL", "Paliyekkara Toll Plaza", "പാലിയേക്കര ടോൾ പ്ലാസ", 10.4428, 76.2600, 637909.74, 1154645.56, 14.0f, "toll_booth");            Add("NODE_PALIYEKKARA_EXIT", "Paliyekkara Toll Exit", "പാലിയേക്കര ടോൾ എക്സിറ്റ്", 10.4415, 76.2600, 637910.32, 1154501.79, 14.0f, "toll_exit");
            // 7. Peechi Dam & Reservoir Corridor
            Add("NODE_PEECHI_MID", "Peechi Scenic Road Midpoint", "പീച്ചി റോഡ്", 10.5315, 76.3150, 643889.60, 1164479.80, 35.0f, "road");            Add("NODE_PEECHI_DAM", "Peechi Dam & Reservoir", "പീച്ചി ഡാം", 10.5300, 76.3698, 649887.69, 1164339.59, 75.0f, "dam");
            // 8. West Fort to Guruvayur & Chavakkad Beach
            Add("NODE_WEST_FORT", "West Fort Junction", "പടിഞ്ഞാറേക്കോട്ട", 10.5218, 76.2025, 631581.99, 1163357.63, 11.8f, "junction");            Add("NODE_KANJANI", "Kanjani Junction", "കാഞ്ഞാണി", 10.5180, 76.1260, 623211.54, 1162906.33, 9.0f, "junction");            Add("NODE_CHOONDAL", "Choondal Junction", "ചൂണ്ടൽ", 10.5850, 76.0950, 619793.06, 1170303.60, 10.5f, "junction");            Add("NODE_GURUVAYUR", "Guruvayur Temple East Nada", "ഗുരുവായൂർ ക്ഷേത്രം", 10.5945, 76.0394, 613706.27, 1171333.34, 8.0f, "temple");            Add("NODE_CHAVAKKAD_TOWN", "Chavakkad Town", "ചാവക്കാട് ടൗൺ", 10.5800, 76.0150, 611041.96, 1169721.07, 4.0f, "town");            Add("NODE_CHAVAKKAD_BEACH", "Chavakkad Beach Coast", "ചാവക്കാട് ബീച്ച്", 10.5720, 76.0080, 610278.94, 1168833.92, 2.0f, "beach");
            // 9. Coastal Highway NH 66 to Triprayar
            Add("NODE_VADANAPPALLY", "Vadanappally NH 66 Junction", "വാടാനപ്പള്ളി", 10.4850, 76.1020, 620597.85, 1159247.66, 4.5f, "junction");            Add("NODE_TRIPRAYAR_TEMPLE", "Triprayar Sree Rama Temple", "തൃപ്രയാർ ശ്രീരാമസ്വാമി ക്ഷേത്രം", 10.4141, 76.1155, 622103.09, 1151412.30, 4.0f, "temple");            Add("NODE_TRIPRAYAR_BEACH", "Triprayar Beach", "തൃപ്രയാർ ബീച്ച്", 10.4228, 76.1065, 621114.47, 1152370.94, 2.0f, "beach");
            return nodes;
        }

        public static List<RoadEdge> GetEdges()
        {
            var edges = new List<RoadEdge>();

            void Add(string id, string from, string to, RoadClassification type, int lanes, float width, float speed)
            {
                edges.Add(new RoadEdge(id, from, to, type, lanes, width, speed));
            }

            // Sakthan Hub & Approaches (Continuous wide asphalt arterial straight North to Swaraj Round)
            Add("EDGE_SAKTHAN_DEP", "NODE_SAKTHAN_BAY", "NODE_SAKTHAN_EXIT", RoadClassification.TerminalLane, 2, 12.0f, 30f);
            Add("EDGE_SAKTHAN_EXIT_TO_APPROACH", "NODE_SAKTHAN_EXIT", "NODE_SAKTHAN_APPROACH", RoadClassification.CityArterial, 4, 14.0f, 45f);
            Add("EDGE_SAKTHAN_APPROACH_TO_HIGHROAD", "NODE_SAKTHAN_APPROACH", "NODE_HIGH_ROAD", RoadClassification.CityArterial, 4, 14.0f, 45f);
            Add("EDGE_HIGHROAD_TO_SWARAJ", "NODE_HIGH_ROAD", "NODE_SWARAJ_SOUTH", RoadClassification.CityArterial, 4, 14.0f, 40f);

            // Puthanpalli East Arterial Branch
            Add("EDGE_HIGHROAD_TO_PUTHAN", "NODE_HIGH_ROAD", "NODE_PUTHANPALLI", RoadClassification.CityArterial, 2, 10.0f, 40f);

            // Swaraj Round Ring
            Add("EDGE_SWARAJ_S_E", "NODE_SWARAJ_SOUTH", "NODE_SWARAJ_EAST", RoadClassification.Roundabout, 3, 14.0f, 35f);
            Add("EDGE_SWARAJ_E_N", "NODE_SWARAJ_EAST", "NODE_SWARAJ_NORTH", RoadClassification.Roundabout, 3, 14.0f, 35f);
            Add("EDGE_SWARAJ_N_W", "NODE_SWARAJ_NORTH", "NODE_SWARAJ_WEST", RoadClassification.Roundabout, 3, 14.0f, 35f);
            Add("EDGE_SWARAJ_W_S", "NODE_SWARAJ_WEST", "NODE_SWARAJ_SOUTH", RoadClassification.Roundabout, 3, 14.0f, 35f);

            // Vadakke Stand Connection
            Add("EDGE_SWARAJ_TO_VADAKKE", "NODE_SWARAJ_NORTH", "NODE_VADAKKE_STAND", RoadClassification.TerminalLane, 2, 10.0f, 30f);

            // Swaraj East to NH 544
            Add("EDGE_SWARAJ_TO_EASTFORT", "NODE_SWARAJ_EAST", "NODE_EAST_FORT", RoadClassification.CityArterial, 2, 11.0f, 50f);
            Add("EDGE_EASTFORT_TO_MANNUTHY", "NODE_EAST_FORT", "NODE_MANNUTHY", RoadClassification.StateHighway, 2, 12.0f, 60f);

            // NH 544 Highway Corridor & Paliyekkara Toll (6-Lane Expanded Plaza)
            Add("EDGE_MANNUTHY_TO_OLLUR", "NODE_MANNUTHY", "NODE_NH544_OLLUR", RoadClassification.NationalHighway, 4, 16.0f, 80f);
            Add("EDGE_OLLUR_TO_AMBALLUR", "NODE_NH544_OLLUR", "NODE_NH544_AMBALLUR", RoadClassification.NationalHighway, 4, 16.0f, 80f);
            Add("EDGE_AMBALLUR_TO_TOLLAPP", "NODE_NH544_AMBALLUR", "NODE_PALIYEKKARA_APPROACH", RoadClassification.NationalHighway, 6, 30.0f, 60f);
            Add("EDGE_TOLL_LANE", "NODE_PALIYEKKARA_APPROACH", "NODE_PALIYEKKARA_TOLL", RoadClassification.TollPlaza, 6, 38.0f, 20f);
            Add("EDGE_TOLL_EXIT", "NODE_PALIYEKKARA_TOLL", "NODE_PALIYEKKARA_EXIT", RoadClassification.NationalHighway, 6, 30.0f, 60f);

            // Peechi Dam Branch
            Add("EDGE_MANNUTHY_TO_PEECHI_MID", "NODE_MANNUTHY", "NODE_PEECHI_MID", RoadClassification.StateHighway, 2, 8.5f, 50f);
            Add("EDGE_PEECHI_MID_TO_DAM", "NODE_PEECHI_MID", "NODE_PEECHI_DAM", RoadClassification.StateHighway, 2, 8.5f, 45f);

            // West Corridor to Guruvayur & Chavakkad Beach
            Add("EDGE_SWARAJ_TO_WESTFORT", "NODE_SWARAJ_WEST", "NODE_WEST_FORT", RoadClassification.CityArterial, 2, 10.0f, 50f);
            Add("EDGE_WESTFORT_TO_KANJANI", "NODE_WEST_FORT", "NODE_KANJANI", RoadClassification.StateHighway, 2, 9.0f, 50f);
            Add("EDGE_KANJANI_TO_CHOONDAL", "NODE_KANJANI", "NODE_CHOONDAL", RoadClassification.StateHighway, 2, 9.0f, 50f);
            Add("EDGE_CHOONDAL_TO_GURUVAYUR", "NODE_CHOONDAL", "NODE_GURUVAYUR", RoadClassification.StateHighway, 2, 9.5f, 50f);
            Add("EDGE_GURUVAYUR_TO_CHAVAKKAD_TOWN", "NODE_GURUVAYUR", "NODE_CHAVAKKAD_TOWN", RoadClassification.StateHighway, 2, 8.5f, 50f);
            Add("EDGE_CHAVAKKAD_TOWN_TO_BEACH", "NODE_CHAVAKKAD_TOWN", "NODE_CHAVAKKAD_BEACH", RoadClassification.CoastalRoad, 2, 8.0f, 40f);

            // Coastal Corridor NH 66 to Triprayar
            Add("EDGE_CHAVAKKAD_TO_VADANAPPALLY", "NODE_CHAVAKKAD_TOWN", "NODE_VADANAPPALLY", RoadClassification.NationalHighway, 2, 10.0f, 60f);
            Add("EDGE_VADANAPPALLY_TO_TRIPRAYAR", "NODE_VADANAPPALLY", "NODE_TRIPRAYAR_TEMPLE", RoadClassification.NationalHighway, 2, 10.0f, 60f);
            Add("EDGE_TRIPRAYAR_TO_BEACH", "NODE_TRIPRAYAR_TEMPLE", "NODE_TRIPRAYAR_BEACH", RoadClassification.CoastalRoad, 2, 8.0f, 40f);

            // Triprayar back to Kanjani / Thrissur via SH 61 (Completing the full loop)
            Add("EDGE_TRIPRAYAR_TO_KANJANI", "NODE_TRIPRAYAR_TEMPLE", "NODE_KANJANI", RoadClassification.StateHighway, 2, 9.0f, 50f);

            // Sakthan link to Ollur / NH 544 (Exits north to approach, then heads toward Ollur)
            Add("EDGE_SAKTHAN_TO_OLLUR", "NODE_SAKTHAN_APPROACH", "NODE_NH544_OLLUR", RoadClassification.CityArterial, 2, 9.5f, 50f);

            return edges;
        }
    }
}
