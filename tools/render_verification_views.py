import math
import struct
import os
from PIL import Image, ImageDraw

W = 1920
H = 1080

def load_mesh(bin_path):
    with open(bin_path, 'rb') as f:
        data = f.read()
    magic = data[:4].decode('latin-1')
    count = struct.unpack('<I', data[4:8])[0]
    is_v3 = (magic == 'HRM3')
    offset = 8
    polys = []
    for _ in range(count):
        part_id = data[offset]
        vcnt = data[offset + 1]
        cull_mode = data[offset + 2] if is_v3 else 0
        r, g, b, a = data[offset + 3:offset + 7]
        bias = struct.unpack('<f', data[offset + 7:offset + 11])[0]
        offset += 11
        if is_v3:
            nx, ny, nz = struct.unpack('<3f', data[offset:offset + 12])
            offset += 12
        else:
            nx, ny, nz = (0.0, 1.0, 0.0)
        verts = []
        for _ in range(vcnt):
            vx, vy, vz = struct.unpack('<3f', data[offset:offset + 12])
            verts.append((vx, vy, vz))
            offset += 12
        cx = sum(v[0] for v in verts) / len(verts)
        cy = sum(v[1] for v in verts) / len(verts)
        cz = sum(v[2] for v in verts) / len(verts)
        polys.append({
            'part_id': part_id,
            'cull_mode': cull_mode,
            'color': (r, g, b, a),
            'bias': bias,
            'normal': (nx, ny, nz),
            'centroid': (cx, cy, cz),
            'verts': verts
        })
    return polys

class AndroidCamera:
    def __init__(self, eye, focus, fov_deg, screen_w=W, screen_h=H):
        self.eye = eye
        self.focus = focus
        self.w = screen_w
        self.h = screen_h
        
        dx = focus[0] - eye[0]
        dy = focus[1] - eye[1]
        dz = focus[2] - eye[2]
        len_f = math.sqrt(dx*dx + dy*dy + dz*dz) or 1.0
        self.fwd = (dx/len_f, dy/len_f, dz/len_f)
        
        rx = self.fwd[2]
        rz = -self.fwd[0]
        r_len = math.hypot(rx, rz) or 0.001
        self.right = (rx / r_len, 0.0, rz / r_len)
        
        # up = fwd x right
        ux = self.fwd[1] * self.right[2] - self.fwd[2] * self.right[1]
        uy = self.fwd[2] * self.right[0] - self.fwd[0] * self.right[2]
        uz = self.fwd[0] * self.right[1] - self.fwd[1] * self.right[0]
        u_len = math.sqrt(ux * ux + uy * uy + uz * uz) or 0.001
        self.up = (ux / u_len, uy / u_len, uz / u_len)
        
        self.fov_scale = (screen_h * 0.5) / math.tan(math.radians(fov_deg * 0.5))
        self.cx = screen_w * 0.5
        self.cy = screen_h * 0.5

    def project(self, pt):
        dx = pt[0] - self.eye[0]
        dy = pt[1] - self.eye[1]
        dz = pt[2] - self.eye[2]
        
        cx = dx * self.right[0] + dy * self.right[1] + dz * self.right[2]
        cy = dx * self.up[0] + dy * self.up[1] + dz * self.up[2]
        cz = dx * self.fwd[0] + dy * self.fwd[1] + dz * self.fwd[2]
        
        if cz < 0.25:
            return None
        inv_z = self.fov_scale / cz
        sx = self.cx + cx * inv_z
        sy = self.cy - cy * inv_z
        return (sx, sy, cz)

def render(polys, eye, focus, fov, out_path, bg_color=(235, 240, 248, 255)):
    cam = AndroidCamera(eye, focus, fov)
    im = Image.new('RGBA', (W, H), bg_color)
    draw = ImageDraw.Draw(im)

    render_list = []
    for poly in polys:
        cm = poly['cull_mode']
        nx, ny, nz = poly['normal']
        cx, cy, cz = poly['centroid']
        if cm == 1:
            dot = (cx - cam.eye[0])*nx + (cy - cam.eye[1])*ny + (cz - cam.eye[2])*nz
            if dot >= 0.08:
                continue
        # Project verts
        s_pts = []
        valid = True
        for v in poly['verts']:
            p = cam.project(v)
            if p:
                s_pts.append((p[0], p[1]))
            else:
                valid = False
                break
        if valid and len(s_pts) >= 3:
            d = math.dist(cam.eye, (cx, cy, cz)) + poly['bias']
            render_list.append((d, s_pts, poly['color']))

    render_list.sort(key=lambda x: x[0], reverse=True)
    for d, pts, col in render_list:
        if len(col) == 4 and col[3] < 255:
            over = Image.new('RGBA', (W, H), (0, 0, 0, 0))
            od = ImageDraw.Draw(over)
            od.polygon(pts, fill=col)
            im.alpha_composite(over)
        else:
            draw.polygon(pts, fill=col)

    im.save(out_path, 'PNG')
    print(f"Saved {out_path}")

if __name__ == '__main__':
    bin_path = 'app/src/main/assets/hino_rm2_real_mesh.bin'
    polys = load_mesh(bin_path)
    out_dir = r'C:\Users\praya\.gemini\antigravity\brain\bab35e35-8716-4809-bfd5-4364c9a11a0f'

    # 1. True Side Profile (focusing on both wheel arches and tires)
    render(polys, eye=(-14.0, 1.8, 0.2), focus=(0.0, 1.8, 0.2), fov=44.0,
           out_path=os.path.join(out_dir, 'test_final_side.png'))

    # 2. Front 3/4 Orbit View
    render(polys, eye=(-10.5, 2.3, 7.8), focus=(0.0, 1.6, 1.5), fov=48.0,
           out_path=os.path.join(out_dir, 'test_final_front_orbit.png'))

    # 3. Direct Front View (Windshield & Fascia)
    render(polys, eye=(0.0, 1.95, 11.0), focus=(0.0, 1.95, 0.0), fov=40.0,
           out_path=os.path.join(out_dir, 'test_final_front.png'))

    # 4. Close-up on Front-Left Wheel Arch & Wheel
    render(polys, eye=(-4.2, 0.85, 3.0), focus=(-1.0, 0.65, 3.0), fov=42.0,
           out_path=os.path.join(out_dir, 'test_final_fl_wheel.png'))
