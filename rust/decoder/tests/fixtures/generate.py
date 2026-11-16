import struct
import subprocess
import sys
from pathlib import Path

SAMPLE_RATE = 44100
CHANNELS = 2
FRAMES = 22050
LCG_MULTIPLIER = 6364136223846793005
LCG_INCREMENT = 1442695040888963407
LCG_SEED = 0x2545F4914F6CDD1D
MASK64 = (1 << 64) - 1


def samples(width, frames=FRAMES):
    maximum = (1 << (width - 1)) - 1
    minimum = -(1 << (width - 1))
    boundary = [
        (0, 0),
        (maximum, minimum),
        (minimum, maximum),
        (1, -1),
        (-1, 1),
        (maximum, maximum),
        (minimum, minimum),
    ]
    values = []
    for left, right in boundary:
        values.extend([left, right])
    state = LCG_SEED
    modulus = 1 << width
    sign_bit = 1 << (width - 1)
    remaining = frames * CHANNELS - len(values)
    for _ in range(remaining):
        state = (state * LCG_MULTIPLIER + LCG_INCREMENT) & MASK64
        value = (state >> 11) % modulus
        if value >= sign_bit:
            value -= modulus
        values.append(value)
    return values


LOSSY_FRAMES = 110_250


def write_raw(path, values, width):
    if width == 16:
        payload = struct.pack(f"<{len(values)}h", *values)
    elif width == 24:
        payload = b"".join(
            (value & 0xFFFFFF).to_bytes(3, "little") for value in values
        )
    elif width == 32:
        payload = struct.pack(f"<{len(values)}i", *values)
    else:
        raise ValueError(width)
    path.write_bytes(payload)


def encode(arguments):
    subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", *arguments], check=True)


def main():
    root = Path(__file__).resolve().parent
    refs = {}
    for width, name in ((16, "ref16.raw"), (24, "ref24.raw"), (32, "ref32.raw")):
        values = samples(width)
        write_raw(root / name, values, width)
        refs[width] = root / name

    def raw_input(width, codec):
        return [
            "-f", f"s{width}le", "-ar", str(SAMPLE_RATE), "-ac", str(CHANNELS),
            "-i", str(refs[width]), "-c:a", codec,
        ]

    encode([*raw_input(16, "pcm_s16le"), str(root / "tone16.wav")])
    encode([*raw_input(24, "pcm_s24le"), str(root / "tone24.wav")])
    encode([*raw_input(32, "pcm_s32le"), str(root / "tone32.wav")])
    encode([*raw_input(16, "flac"), "-sample_fmt", "s16", str(root / "tone16.flac")])
    encode([*raw_input(24, "flac"), "-sample_fmt", "s32", str(root / "tone24.flac")])
    encode([*raw_input(16, "pcm_s16be"), str(root / "tone16.aif")])
    encode([*raw_input(16, "alac"), str(root / "tone_lossless.m4a")])

    long_raw = root / "reflong16.raw"
    write_raw(long_raw, samples(16, LOSSY_FRAMES), 16)

    def long_input(codec):
        return [
            "-f", "s16le", "-ar", str(SAMPLE_RATE), "-ac", str(CHANNELS),
            "-i", str(long_raw), "-c:a", codec,
        ]

    encode([*long_input("libmp3lame"), "-b:a", "192k", str(root / "tone_lossy.mp3")])
    encode([*long_input("aac"), "-b:a", "192k", str(root / "tone_lossy.m4a")])
    encode([*long_input("libvorbis"), "-q:a", "5", str(root / "tone_lossy.ogg")])
    long_raw.unlink()
    print("fixtures written to", root)


if __name__ == "__main__":
    sys.exit(main())
