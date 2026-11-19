#!/usr/bin/env python3
"""Generates Director Zone's built-in sound library (original, synthesized, royalty-free).  python3 tools/make_sounds.py"""
import numpy as np, wave, json, os
from scipy import signal
SR = 32000; OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "sfx"); os.makedirs(OUT, exist_ok=True); rng = np.random.default_rng(7)
def T(d): return np.arange(int(d*SR))/SR
def nz(d): return rng.normal(0, 1, int(d*SR))
def bp(x, lo, hi, o=2): return signal.sosfilt(signal.butter(o, [lo, min(hi, SR/2-100)], "band", fs=SR, output="sos"), x)
def lp(x, f, o=2): return signal.sosfilt(signal.butter(o, min(f, SR/2-100), "low", fs=SR, output="sos"), x)
def hp(x, f, o=2): return signal.sosfilt(signal.butter(o, f, "high", fs=SR, output="sos"), x)
def atk(t, a): return np.minimum(t/a, 1.0)**1.5
def body(f0, f1, tau, d=0.3, a=0.003, tri=0.0):
    t = T(d); f = f1+(f0-f1)*np.exp(-t/0.012); ph = 2*np.pi*np.cumsum(f)/SR; x = np.sin(ph)*(1-tri)+tri*signal.sawtooth(ph, 0.5); return x*np.exp(-t/tau)*atk(t, a)
def snoise(d, lo, hi, tau, a=0.003): t = T(d); return bp(nz(d), lo, hi)*np.exp(-t/tau)*atk(t, a)
def pad_to(x, n): return np.pad(x, (0, max(0, n-len(x))))[:n]
def msum(it): return mix(*list(it))
def mix(*xs):
    n = max(len(x) for x in xs); return sum(pad_to(x, n) for x in xs)
def delay(x, dt, g): n = int(dt*SR); return np.concatenate([np.zeros(n), x*g])
def tock(f, g=1.0, tau=0.03, d=0.3): return g*lp(body(f*1.5, f, tau, d, 0.003, 0.25)+0.5*body(f*2.01, f*1.98, tau*0.6, d, 0.003)+0.2*snoise(d, 350, 2600, tau*0.5)/3, 5200)
def sweep(d, f0, f1, g=1.0, top=3000):
    t = T(d); u = t/d; cf = f0*(f1/f0)**u; y = np.zeros(len(t)); blk = 512; n = nz(d)
    for i in range(0, len(n), blk): c = float(cf[min(i, len(cf)-1)]); y[i:i+blk] = signal.sosfilt(signal.butter(2, [c*0.55, min(c*1.4, top)], "band", fs=SR, output="sos"), n[i:i+blk])
    return y*np.sin(np.pi*u**0.8)**1.6*g
def bell(f, d, idx=1.4, g=1.0):
    t = T(d); env = np.exp(-t/(d/4.8)); mod = np.sin(2*np.pi*f*2*t)*idx*np.exp(-t/0.5); return (np.sin(2*np.pi*f*t+mod)+0.3*np.sin(2*np.pi*f*2*t)*np.exp(-t/0.8)+0.1*np.sin(2*np.pi*f*3.01*t)*np.exp(-t/0.4))*env*g*atk(t, 0.004)
def ir(d, tau, lo=4500): t = T(d); x = lp(nz(d), lo)*np.exp(-t/tau); x[:int(0.01*SR)] = 0; return x
def verb(x, wet=0.25, d=1.6, tau=0.4): return x[:len(x)]+wet*signal.fftconvolve(x, ir(d, tau))[:len(x)]*0.05
def fade(x, a=0.004, r=0.02): n = len(x); e = np.ones(n); na = int(a*SR); nr = int(r*SR); e[:na] = np.linspace(0, 1, max(1, na)); e[-nr:] = np.linspace(1, 0, nr); return x*e
def pad(notes, d, cut=1800, det=(-7, 0, 6)):
    t = T(d); out = np.zeros(len(t))
    for k, m in enumerate(notes):
        f = 440*2**((m-69)/12)
        for dd in det: out += np.sin(2*np.pi*f*2**(dd/1200)*t+k*1.3+0.5*np.sin(2*np.pi*0.2*t+k))/(1+0.25*k)
    return lp(out, cut)
def loopable(x, xf=0.4):
    n = int(xf*SR); y = x.copy(); y[:n] = x[:n]*np.linspace(0, 1, n)+x[-n:]*np.linspace(1, 0, n); return y[:-n]
def kick(d=0.3): return body(150, 48, 0.12, d, 0.002)*1.0
def snare(d=0.25): return lp(snoise(d, 900, 7000, 0.07)*0.9+body(220, 180, 0.05, d)*0.5, 8000)
def hat(d=0.08): return hp(nz(d), 6000)*np.exp(-T(d)/0.018)*0.35
def clap(d=0.2): return msum(delay(snoise(0.1, 1200, 5000, 0.03), k*0.011, 0.8) for k in range(3))[:int(d*SR)]
S = {}   # name -> (category, signal)
def add(cat, name, x): S[name] = (cat, x)
# ---- UI
add("UI", "Pop", fade(body(900, 380, 0.05, 0.25, 0.002)*0.9+0.3*snoise(0.25, 800, 4000, 0.02)))
add("UI", "Tap", tock(520, 0.9, 0.03)); add("UI", "Soft Click", tock(760, 0.6, 0.02, 0.2)); add("UI", "Toggle On", mix(tock(330, 0.8, 0.034), delay(tock(520, 0.8, 0.04), 0.009, 1), delay(bell(1180, 0.3, 0.5, 0.15), 0.012, 1)))
add("UI", "Toggle Off", mix(tock(420, 0.8, 0.034), delay(tock(260, 0.8, 0.04), 0.009, 1)))
add("UI", "Tick", fade(body(1700, 1250, 0.011, 0.12, 0.0015)*0.8+0.3*snoise(0.12, 1500, 6000, 0.005))); add("UI", "Switch", mix(tock(300, 1, 0.05), delay(tock(450, 0.8, 0.05), 0.02, 1)))
add("UI", "Select", mix(tock(640, 0.8, 0.03), delay(bell(1568, 0.35, 0.4, 0.2), 0.005, 1))); add("UI", "Back", mix(tock(440, 0.8, 0.03), delay(tock(330, 0.6, 0.03), 0.07, 1)))
add("UI", "Slider Tick", fade(body(2100, 1800, 0.008, 0.08, 0.001)*0.6)); add("UI", "Double Tap", mix(tock(560, 0.8), delay(tock(560, 0.8), 0.11, 1)))
# ---- whooshes
add("Whoosh", "Soft Whoosh", fade(sweep(0.9, 300, 1600, 0.9), 0.02, 0.1)); add("Whoosh", "Fast Whoosh", fade(sweep(0.45, 500, 3500, 1.0, 5000), 0.01, 0.06))
add("Whoosh", "Air Whoosh", fade(sweep(1.2, 200, 1100, 0.9, 2500), 0.05, 0.2)); add("Whoosh", "Swipe Left", fade(sweep(0.4, 1800, 500, 0.9), 0.01, 0.05)); add("Whoosh", "Swipe Right", fade(sweep(0.4, 500, 1800, 0.9), 0.01, 0.05))
add("Whoosh", "Flip", fade(mix(sweep(0.7, 250, 1700, 0.9), delay(tock(150, 0.8, 0.06, 0.4), 0.33, 1)), 0.01, 0.08)); add("Whoosh", "Spin", fade(sweep(1.0, 400, 3000, 1.0, 4500)*(0.7+0.3*np.sin(2*np.pi*9*T(1.0))), 0.02, 0.1))
add("Whoosh", "Rewind", fade(sweep(0.8, 2500, 300, 0.9, 4000)*(0.7+0.3*np.sin(2*np.pi*22*T(0.8))), 0.01, 0.08)); add("Whoosh", "Cinematic Whoosh", fade(verb(sweep(1.6, 160, 1200, 1.0, 2200), 0.6), 0.05, 0.3))
# ---- impacts
add("Impact", "Thud", fade(body(90, 48, 0.12, 0.6, 0.004)*1.1+0.15*lp(snoise(0.6, 100, 700, 0.05), 900), 0.002, 0.05)); add("Impact", "Hit", fade(mix(body(120, 50, 0.1, 0.6)*1.0, 0.7*snoise(0.6, 300, 3000, 0.04)), 0.002, 0.05))
add("Impact", "Boom", fade(verb(body(70, 32, 0.45, 1.8, 0.004)*1.2+0.2*lp(snoise(1.8, 60, 400, 0.4), 500), 0.5, 2.0, 0.7), 0.002, 0.3)); add("Impact", "Bass Drop", fade(body(180, 36, 0.8, 2.4, 0.004)*1.1+0.2*body(360, 72, 0.4, 2.4), 0.002, 0.4))
add("Impact", "Glass Hit", fade(msum(bell(f, 1.3, 0.6, g) for f, g in ((1480, 0.5), (2210, 0.4), (3340, 0.3), (4510, 0.2)))+0.5*snoise(1.3, 3000, 9000, 0.03), 0.002, 0.1))
add("Impact", "Slam", fade(mix(body(110, 42, 0.15, 0.9), 0.9*snoise(0.9, 200, 3500, 0.08), delay(body(70, 40, 0.3, 0.8), 0.04, 0.8)), 0.002, 0.1)); add("Impact", "Punch", fade(mix(body(140, 60, 0.07, 0.4), 0.6*snoise(0.4, 400, 2500, 0.03)), 0.002, 0.04))
# ---- risers
add("Riser", "Riser Short", fade(sweep(1.4, 200, 3500, 1.0, 5000)*np.linspace(0.2, 1, int(1.4*SR)), 0.05, 0.03)); add("Riser", "Riser Long", fade(sweep(3.0, 150, 3800, 1.0, 5500)*np.linspace(0.1, 1, int(3.0*SR))**1.5, 0.1, 0.03))
add("Riser", "Dreamy Swell", fade(pad([60, 67, 72, 76], 3.0, 2000)*np.sin(np.pi*np.linspace(0, 1, int(3.0*SR))**0.8)**2*0.4, 0.01, 0.01)); add("Riser", "Reverse Cymbal", fade(hp(nz(2.0), 3000)*np.linspace(0, 1, int(2.0*SR))**2.5*0.5, 0.01, 0.03))
# ---- chimes
add("Chime", "Ding", fade(bell(1318, 1.4, 1.0, 0.7), 0.002, 0.1)); add("Chime", "Bell", fade(verb(bell(880, 2.2, 1.3, 0.7), 0.4), 0.002, 0.3)); add("Chime", "Success", fade(mix(bell(784, 1.0, 0.8, 0.5), delay(bell(1047, 1.0, 0.8, 0.5), 0.12, 1), delay(bell(1568, 1.2, 0.8, 0.5), 0.24, 1)), 0.002, 0.2))
add("Chime", "Error", fade(mix(bell(330, 0.5, 0.5, 0.6), delay(bell(262, 0.7, 0.5, 0.6), 0.14, 1)), 0.002, 0.1)); add("Chime", "Notify", fade(mix(bell(1568, 0.8, 0.9, 0.5), delay(bell(2093, 1.0, 0.9, 0.5), 0.1, 1)), 0.002, 0.15))
add("Chime", "Sparkle", fade(msum(delay(bell(2000+k*437, 0.5, 0.5, 0.25), k*0.045, 1) for k in range(7)), 0.002, 0.1)); add("Chime", "Coin", fade(mix(bell(1976, 0.2, 0.3, 0.5), delay(bell(2637, 0.6, 0.5, 0.5), 0.07, 1)), 0.001, 0.1))
add("Chime", "Level Up", fade(msum(delay(bell(f, 0.6, 0.7, 0.4), k*0.09, 1) for k, f in enumerate((523, 659, 784, 1047, 1319))), 0.002, 0.3))
# ---- camera & typing
add("Camera", "Shutter", fade(mix(tock(1500, 0.9, 0.012, 0.2), delay(tock(900, 0.8, 0.02, 0.2), 0.06, 1)), 0.001, 0.05)); add("Camera", "Key Press", tock(900, 0.6, 0.012, 0.15)); add("Camera", "Mouse Click", mix(tock(1200, 0.7, 0.008, 0.12), delay(tock(800, 0.5, 0.01, 0.12), 0.05, 1)))
add("Camera", "Typewriter", fade(msum(delay(tock(700+rng.integers(-120, 120), 0.5, 0.01, 0.12), k*0.09, 1) for k in range(10)), 0.001, 0.05)); add("Camera", "Swish In", fade(sweep(0.35, 800, 3200, 0.8, 4500), 0.01, 0.05))
# ---- glitch / tech
add("Glitch", "Glitch", fade(mix(msum(delay(snoise(0.05, 400, 7000, 0.02)*rng.uniform(0.5, 1), k*0.035, 1) for k in range(8)), 0.3*body(1200, 700, 0.1, 0.4, 0.001, 0.8)), 0.001, 0.05)); add("Glitch", "Zap", fade(sweep(0.35, 4500, 400, 1.0, 7000)*(1+np.sin(2*np.pi*60*T(0.35)))*0.5, 0.001, 0.05))
add("Glitch", "Static Burst", fade(hp(nz(0.5), 1500)*np.exp(-T(0.5)/0.14)*0.7, 0.001, 0.05)); add("Glitch", "Digital Blip", fade(msum(delay(body(900+k*300, 900+k*300, 0.02, 0.06, 0.001, 0.3), k*0.05, 1) for k in range(4)), 0.001, 0.03))
add("Glitch", "Power Up", fade(sweep(0.8, 150, 2500, 1.0, 4000)*0.8, 0.01, 0.05)); add("Glitch", "Power Down", fade(sweep(0.8, 2500, 120, 1.0, 4000)*0.8, 0.01, 0.1))
# ---- beds (loops)
def bed(name, chords, cut, seg, bpm=None):
    d = len(chords)*seg+0.4; out = np.zeros(int(d*SR))
    for i, ch in enumerate(chords):
        x = pad(ch, seg+1.2, cut)*np.sin(np.pi*np.clip(T(seg+1.2)/(seg+1.2), 0, 1))**2; s0 = int(i*seg*SR); out[s0:s0+len(x)] += x[: len(out)-s0]*0.3
    add("Ambience", name, loopable(verb(out, 0.3)*0.9))
bed("Calm Pad", [[50, 57, 60, 65], [46, 53, 57, 62], [53, 57, 60, 64], [48, 55, 60, 64]], 1700, 2.5); bed("Dreamy Pad", [[57, 64, 69, 72], [53, 60, 65, 69], [55, 62, 67, 71], [52, 59, 64, 67]], 2400, 2.5)
bed("Tech Pad", [[45, 52, 57, 60], [43, 50, 55, 59], [41, 48, 53, 57], [43, 50, 55, 62]], 1300, 2.5); bed("Cinematic Pad", [[38, 45, 50, 57], [41, 48, 53, 60], [36, 43, 48, 55], [43, 50, 55, 62]], 1100, 3.0)
def beat(name, bpm, pattern, chords, swing=0.0):
    sb = 60/bpm; bars = 4; d = bars*4*sb; out = np.zeros(int(d*SR)+SR)
    def put(x, t, g=1.0): s0 = int(t*SR); out[s0:s0+len(x)] += x[: len(out)-s0]*g
    for bar in range(bars):
        for st in range(16):
            t = (bar*16+st)*sb/4+(swing*sb/4 if st % 2 else 0)
            if pattern["k"][st]: put(kick(), t, 0.9)
            if pattern["s"][st]: put(snare(), t, 0.55)
            if pattern["h"][st]: put(hat(), t, 0.8 if st % 4 else 1.0)
        if chords:
            ch = chords[bar % len(chords)]; x = pad(ch, 4*sb+0.8, 1500)*0.20; put(x*np.sin(np.pi*np.clip(T(4*sb+0.8)/(4*sb+0.8), 0, 1))**0.6, bar*4*sb)
    out = out[: int(d*SR)]; add("Beats", name, fade(np.tanh(out*1.4)*0.8, 0.002, 0.01))
k4 = [1, 0, 0, 0]*4; beat("Lofi Beat", 82, {"k": [1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0, 0, 0], "s": [0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0], "h": [1, 0, 1, 0]*4}, [[57, 60, 64, 67], [53, 57, 60, 64]], 0.3)
beat("House Beat", 122, {"k": k4, "s": [0, 0, 0, 0, 1, 0, 0, 0]*2, "h": [0, 0, 1, 0]*4}, [[57, 60, 64], [55, 59, 62]]); beat("Trap Beat", 70, {"k": [1, 0, 0, 0, 0, 0, 0, 1, 0, 0, 1, 0, 0, 0, 0, 0], "s": [0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0], "h": [1, 1, 1, 1]*4}, [[50, 53, 57]])
beat("Chill Groove", 96, {"k": [1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 1, 0, 0, 0, 0, 0], "s": [0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 1], "h": [0, 0, 1, 0]*4}, [[60, 64, 67, 71], [57, 60, 64, 67]], 0.15)
idx = []
for name, (cat, x) in S.items():
    x = np.asarray(x, np.float64); x = x/(np.abs(x).max()+1e-9)*0.8; fn = name.lower().replace(" ", "_")+".wav"
    with wave.open(os.path.join(OUT, fn), "wb") as w: w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes((x*32767).astype("<i2").tobytes())
    idx.append({"name": name, "cat": cat, "file": fn, "ms": int(len(x)/SR*1000), "loop": cat in ("Ambience", "Beats")})
with wave.open(os.path.join(OUT, "_silence.wav"), "wb") as w: w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(b"\x00\x00"*SR)
json.dump(idx, open(os.path.join(OUT, "index.json"), "w"), indent=1); print(len(idx), "sounds", sum(os.path.getsize(os.path.join(OUT, f)) for f in os.listdir(OUT))//1024, "KB")
