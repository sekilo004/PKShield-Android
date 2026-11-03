#!/usr/bin/env python3
"""
Converts pso_manifest.tsv (recorded render pipelines) into the binary vk_pipelines_seed.bin
used by nvn_vk.c for fast, multi-threaded pipeline prewarming.
"""
import os
import struct
import sys

PSO_MAGIC = b"NXPL"
RECORD_SIZE = 720

MTL_TO_VK_COLOR = {
    10: 9,    # R8Unorm -> R8_UNORM
    60: 77,   # RG16Unorm -> RG16_UNORM
    70: 37,   # RGBA8Unorm -> RGBA8_UNORM
    71: 43,   # RGBA8Unorm_sRGB -> RGBA8_SRGB
    92: 122,  # RG11B10Float -> B10G11R11_UFLOAT_PACK32
    113: 95,  # RGBA16Uint -> RGBA16_UINT
    115: 97,  # RGBA16Float -> RGBA16_SFLOAT
    125: 109  # RGBA32Float -> RGBA32_SFLOAT
}

MTL_TO_VK_DEPTH = {
    250: 124, # Depth16Unorm -> D16_UNORM
    252: 126, # Depth32Float -> D32_SFLOAT
    253: 127, # Stencil8 -> S8_UINT
    260: 130  # Depth32Float_Stencil8 -> D32_SFLOAT_S8_UINT
}

# NVN vertex format to VkFormat (from nvn_fmt_vk.c nvn_vfmt_to_vk)
NVN_VTX_TABLE = {
    0x01: 9,   0x02: 10,  0x03: 13,  0x04: 14,  0x05: 76,  0x06: 70,  0x07: 71,  0x08: 74,  0x09: 75,
    0x0A: 100, 0x0B: 98,  0x0C: 99,
    0x0D: 16,  0x0E: 17,  0x0F: 20,  0x10: 21,  0x11: 83,  0x12: 77,  0x13: 78,  0x14: 81,  0x15: 82,
    0x16: 103, 0x17: 101, 0x18: 102,
    0x19: 23,  0x1A: 24,  0x1B: 27,  0x1C: 28,  0x1D: 90,  0x1E: 84,  0x1F: 85,  0x20: 88,  0x21: 89,
    0x22: 106, 0x23: 104, 0x24: 105,
    0x25: 37,  0x26: 38,  0x27: 41,  0x28: 42,  0x29: 97,  0x2A: 91,  0x2B: 92,  0x2C: 95,  0x2D: 96,
    0x2E: 109, 0x2F: 107, 0x30: 108,
    0x3D: 64,  0x69: 65,  0x3F: 122, 0x9A: 44,
    0x51: 11,  0x52: 12,  0x53: 72,  0x54: 73,  0x55: 98,  0x56: 99,
    0x57: 18,  0x58: 19,  0x59: 79,  0x5A: 80,  0x5B: 101, 0x5C: 102,
    0x5D: 25,  0x5E: 26,  0x5F: 86,  0x60: 87,  0x61: 104, 0x62: 105,
    0x63: 39,  0x64: 40,  0x65: 93,  0x66: 94,  0x67: 107, 0x68: 108,
}

def hexkey(s):
    return bytes.fromhex(s[:16])

def convert_manifest(tsv_path, out_path):
    if not os.path.isfile(tsv_path):
        print(f"Manifest not found: {tsv_path}", file=sys.stderr)
        return 0
    with open(tsv_path, 'r', encoding='utf-8') as f:
        lines = f.readlines()
    records = []
    for line in lines:
        if not line.startswith('R\t'):
            continue
        hex_str = line.strip().split('\t')[1]
        if len(hex_str) != 864:
            continue
        raw = bytes.fromhex(hex_str)
        vs_str = raw[0:24].split(b'\0')[0].decode('ascii', 'ignore')
        fs_str = raw[24:48].split(b'\0')[0].decode('ascii', 'ignore')
        if len(vs_str) < 16:
            continue
        vs_key = hexkey(vs_str)
        fs_key = hexkey(fs_str) if len(fs_str) >= 16 else b'\0'*8
        
        key_bytes = vs_key + fs_key + b'\0'*24
        k_raw = raw[88:]
        mtl_cfmt = struct.unpack('<8I', k_raw[16:48])
        mtl_dfmt, mtl_sfmt = struct.unpack('<2I', k_raw[48:56])
        mtl_samples, mtl_blend_en, mtl_chmask, mtl_a2c = struct.unpack('<4I', k_raw[56:72])
        blend_bytes = k_raw[72:136]
        
        vk_cfmt = [MTL_TO_VK_COLOR.get(c, 0) for c in mtl_cfmt]
        vk_dfmt = MTL_TO_VK_DEPTH.get(mtl_dfmt, 0)
        vk_sfmt = MTL_TO_VK_DEPTH.get(mtl_sfmt, 0)
        
        attr_bytes = bytearray(512)
        for i in range(16):
            va_off = 136 + i * 4
            va_val = struct.unpack('<I', k_raw[va_off:va_off+4])[0]
            fmt = va_val & 0xFF
            offset = (va_val >> 8) & 0x7FFFF
            stream = (va_val >> 27) & 0x1F
            if fmt != 0:
                vs_off = 200 + stream * 8
                stride, divisor = struct.unpack('<2I', k_raw[vs_off:vs_off+8])
                vk_fmt = NVN_VTX_TABLE.get(fmt, 0)
                inst = 1 if divisor != 0 else 0
                attr_struct = struct.pack('<IIBBBB I', vk_fmt, offset, stream, 1, inst, 0, divisor)
                attr_bytes[i*16:(i+1)*16] = attr_struct
        
        sh_bytes = b'\0' * 40
        cfmt_bytes = struct.pack('<8I', *vk_cfmt)
        ds_bytes = struct.pack('<II', vk_dfmt, vk_sfmt)
        chmask_bytes = struct.pack('<II', mtl_chmask, 0)
        flags_bytes = struct.pack('<BBBBBBBBBB 6s', 
            mtl_samples if mtl_samples else 1,
            mtl_a2c,
            0,
            0,
            0,
            0,
            0,
            mtl_blend_en,
            0,
            0,
            b'\0'*6
        )
        
        pkey_bytes = sh_bytes + cfmt_bytes + ds_bytes + chmask_bytes + blend_bytes + attr_bytes + flags_bytes
        rec_bytes = key_bytes + pkey_bytes
        assert len(rec_bytes) == RECORD_SIZE
        records.append(rec_bytes)
        
    os.makedirs(os.path.dirname(os.path.abspath(out_path)), exist_ok=True)
    with open(out_path, 'wb') as fout:
        fout.write(PSO_MAGIC)
        fout.write(struct.pack('<II', 1, RECORD_SIZE))
        for r in records:
            fout.write(r)
    print(f"Converted {len(records)} pipelines from {tsv_path} -> {out_path}")
    return len(records)

if __name__ == '__main__':
    if len(sys.argv) > 2:
        convert_manifest(sys.argv[1], sys.argv[2])
    else:
        dm = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
        for g in ('01008DB008C2C000', '0100ABF008968000'):
            tsv = os.path.join(dm, 'games', g, 'pso_manifest.tsv')
            out = os.path.join(dm, 'games', g, 'vk_pipelines_seed.bin')
            convert_manifest(tsv, out)

