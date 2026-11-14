"""List scene objects near a world position, using Unity's YAML scene text.

Parses only transforms/game objects (no mesh assets), rebuilding world positions
from the m_Father chain so nearby colliders can be identified.
"""
import re
import sys

SCENE = "unity/Assets/_Project/Scenes/03_Thrissur_RealisticWorld.unity"


def parse_documents(path):
    with open(path, "r", encoding="utf-8", errors="replace") as handle:
        text = handle.read()
    docs = {}
    for block in re.split(r"^--- !u!", text, flags=re.MULTILINE)[1:]:
        header, _, body = block.partition("\n")
        match = re.match(r"(\d+) &(-?\d+)", header.strip())
        if not match:
            continue
        docs[int(match.group(2))] = (int(match.group(1)), body)
    return docs


def field(body, name):
    match = re.search(rf"^\s*{name}:\s*(.+)$", body, re.MULTILINE)
    return match.group(1).strip() if match else None


def vector(body, name):
    match = re.search(rf"^\s*{name}:\s*\{{x:\s*(-?[\d.eE+-]+),\s*y:\s*(-?[\d.eE+-]+),\s*z:\s*(-?[\d.eE+-]+)\}}",
                      body, re.MULTILINE)
    if not match:
        return None
    return tuple(float(match.group(i)) for i in (1, 2, 3))


def main():
    target = (float(sys.argv[1]), float(sys.argv[2]))
    radius = float(sys.argv[3]) if len(sys.argv) > 3 else 25.0
    docs = parse_documents(SCENE)

    game_objects = {}
    transforms = {}
    for file_id, (class_id, body) in docs.items():
        if class_id == 1:
            game_objects[file_id] = field(body, "m_Name")
        elif class_id in (4, 224):
            game = field(body, "m_GameObject")
            go_id = None
            if game:
                go_id = int(re.search(r"fileID:\s*(-?\d+)", game).group(1))
            father = field(body, "m_Father")
            father_id = int(re.search(r"fileID:\s*(-?\d+)", father).group(1)) if father else 0
            transforms[file_id] = dict(go=go_id, father=father_id,
                                       pos=vector(body, "m_LocalPosition"),
                                       scale=vector(body, "m_LocalScale"))

    by_go = {t["go"]: fid for fid, t in transforms.items() if t["go"]}

    def world_position(transform_id, depth=0):
        transform = transforms.get(transform_id)
        if transform is None or depth > 40:
            return (0.0, 0.0, 0.0)
        position = transform["pos"] or (0.0, 0.0, 0.0)
        parent = transforms.get(transform["father"])
        if parent is None:
            return position
        px, py, pz = world_position(transform["father"], depth + 1)
        sx = parent["scale"] or (1.0, 1.0, 1.0)
        return (px + position[0] * sx[0], py + position[1] * sx[1], pz + position[2] * sx[2])

    hits = []
    for transform_id, transform in transforms.items():
        name = game_objects.get(transform["go"], "<unnamed>")
        if not name:
            continue
        wx, wy, wz = world_position(transform_id)
        distance = ((wx - target[0]) ** 2 + (wz - target[1]) ** 2) ** 0.5
        if distance <= radius:
            hits.append((distance, name, wx, wy, wz, transform["scale"]))
    hits.sort()
    print(f"objects with an origin within {radius:.0f} m of {target} ({len(hits)} found)")
    seen = set()
    for distance, name, wx, wy, wz, scale in hits:
        key = (name, round(wx, 1), round(wy, 1), round(wz, 1))
        if key in seen:
            continue
        seen.add(key)
        print(f"  {distance:6.1f} m  y={wy:6.2f}  ({wx:8.2f}, {wz:9.2f})  scale={scale}  {name}")


if __name__ == "__main__":
    main()
