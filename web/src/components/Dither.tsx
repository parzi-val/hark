import { useRef, useEffect } from 'react';
import './Dither.css';

// Same dithered-waves effect as the react-three-fiber original, in raw WebGL2 — the effect was
// always just a fragment shader; three.js/fiber/postprocessing (~1MB) were only plumbing. The two
// original passes (fbm wave → Bayer dither) are merged into one full-screen shader here.

const VERT = `#version 300 es
in vec2 pos;
void main() { gl_Position = vec4(pos, 0.0, 1.0); }`;

const FRAG = `#version 300 es
precision highp float;
uniform vec2 resolution;
uniform float time;
uniform float waveSpeed;
uniform float waveFrequency;
uniform float waveAmplitude;
uniform vec3 waveColor;
uniform vec3 backgroundColor;
uniform vec2 mousePos;
uniform int enableMouseInteraction;
uniform float mouseRadius;
uniform float colorNum;
uniform float pixelSize;
out vec4 fragColor;

vec4 mod289(vec4 x){ return x - floor(x*(1.0/289.0))*289.0; }
vec4 permute(vec4 x){ return mod289(((x*34.0)+1.0)*x); }
vec4 taylorInvSqrt(vec4 r){ return 1.79284291400159 - 0.85373472095314*r; }
vec2 fade(vec2 t){ return t*t*t*(t*(t*6.0-15.0)+10.0); }

float cnoise(vec2 P){
  vec4 Pi = floor(P.xyxy) + vec4(0.0,0.0,1.0,1.0);
  vec4 Pf = fract(P.xyxy) - vec4(0.0,0.0,1.0,1.0);
  Pi = mod289(Pi);
  vec4 ix = Pi.xzxz; vec4 iy = Pi.yyww;
  vec4 fx = Pf.xzxz; vec4 fy = Pf.yyww;
  vec4 i = permute(permute(ix) + iy);
  vec4 gx = fract(i * (1.0/41.0)) * 2.0 - 1.0;
  vec4 gy = abs(gx) - 0.5;
  vec4 tx = floor(gx + 0.5);
  gx = gx - tx;
  vec2 g00 = vec2(gx.x, gy.x); vec2 g10 = vec2(gx.y, gy.y);
  vec2 g01 = vec2(gx.z, gy.z); vec2 g11 = vec2(gx.w, gy.w);
  vec4 norm = taylorInvSqrt(vec4(dot(g00,g00), dot(g01,g01), dot(g10,g10), dot(g11,g11)));
  g00 *= norm.x; g01 *= norm.y; g10 *= norm.z; g11 *= norm.w;
  float n00 = dot(g00, vec2(fx.x, fy.x));
  float n10 = dot(g10, vec2(fx.y, fy.y));
  float n01 = dot(g01, vec2(fx.z, fy.z));
  float n11 = dot(g11, vec2(fx.w, fy.w));
  vec2 fade_xy = fade(Pf.xy);
  vec2 n_x = mix(vec2(n00, n01), vec2(n10, n11), fade_xy.x);
  return 2.3 * mix(n_x.x, n_x.y, fade_xy.y);
}

float fbm(vec2 p){
  float value = 0.0; float amp = 1.0; float freq = waveFrequency;
  for (int i = 0; i < 4; i++) { value += amp * abs(cnoise(p)); p *= freq; amp *= waveAmplitude; }
  return value;
}
float pattern(vec2 p){ vec2 p2 = p - time * waveSpeed; return fbm(p + fbm(p2)); }

const float bayer[64] = float[64](
  0.0/64.0,48.0/64.0,12.0/64.0,60.0/64.0,3.0/64.0,51.0/64.0,15.0/64.0,63.0/64.0,
  32.0/64.0,16.0/64.0,44.0/64.0,28.0/64.0,35.0/64.0,19.0/64.0,47.0/64.0,31.0/64.0,
  8.0/64.0,56.0/64.0,4.0/64.0,52.0/64.0,11.0/64.0,59.0/64.0,7.0/64.0,55.0/64.0,
  40.0/64.0,24.0/64.0,36.0/64.0,20.0/64.0,43.0/64.0,27.0/64.0,39.0/64.0,23.0/64.0,
  2.0/64.0,50.0/64.0,14.0/64.0,62.0/64.0,1.0/64.0,49.0/64.0,13.0/64.0,61.0/64.0,
  34.0/64.0,18.0/64.0,46.0/64.0,30.0/64.0,33.0/64.0,17.0/64.0,45.0/64.0,29.0/64.0,
  10.0/64.0,58.0/64.0,6.0/64.0,54.0/64.0,9.0/64.0,57.0/64.0,5.0/64.0,53.0/64.0,
  42.0/64.0,26.0/64.0,38.0/64.0,22.0/64.0,41.0/64.0,25.0/64.0,37.0/64.0,21.0/64.0
);

vec3 waveAt(vec2 fragCoord){
  vec2 uv = fragCoord / resolution.xy;
  uv -= 0.5;
  uv.x *= resolution.x / resolution.y;
  float f = pattern(uv);
  if (enableMouseInteraction == 1) {
    vec2 mouseNDC = (mousePos / resolution - 0.5) * vec2(1.0, -1.0);
    mouseNDC.x *= resolution.x / resolution.y;
    float dist = length(uv - mouseNDC);
    float effect = 1.0 - smoothstep(0.0, mouseRadius, dist);
    f -= 0.5 * effect;
  }
  return mix(backgroundColor, waveColor, clamp(f, 0.0, 1.0));
}

void main(){
  vec2 pix = floor(gl_FragCoord.xy / pixelSize) * pixelSize; // pixelate
  vec3 color = waveAt(pix);
  vec2 sc = floor(gl_FragCoord.xy / pixelSize);
  int x = int(mod(sc.x, 8.0));
  int y = int(mod(sc.y, 8.0));
  float threshold = bayer[y * 8 + x] - 0.25;
  float st = 1.0 / (colorNum - 1.0);
  color += threshold * st;
  float lum = dot(color, vec3(0.2126, 0.7152, 0.0722));
  float bias = mix(0.2, 0.0, smoothstep(0.45, 0.8, lum));
  color = clamp(color - bias, 0.0, 1.0);
  color = floor(color * (colorNum - 1.0) + 0.5) / (colorNum - 1.0); // quantize
  fragColor = vec4(color, 1.0);
}`;

export interface DitherProps {
  waveSpeed?: number;
  waveFrequency?: number;
  waveAmplitude?: number;
  waveColor?: [number, number, number];
  backgroundColor?: [number, number, number];
  colorNum?: number;
  pixelSize?: number;
  disableAnimation?: boolean;
  enableMouseInteraction?: boolean;
  mouseRadius?: number;
  className?: string;
  style?: React.CSSProperties;
}

export function Dither({
  waveSpeed = 0.05,
  waveFrequency = 3,
  waveAmplitude = 0.3,
  waveColor = [0.54, 0.29, 0.2], // Rust #8A4B34
  backgroundColor = [0.957, 0.949, 0.929], // Paper #F4F2ED
  colorNum = 4,
  pixelSize = 2,
  disableAnimation = false,
  enableMouseInteraction = true,
  mouseRadius = 1,
  className = '',
  style,
}: DitherProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const propsRef = useRef({
    waveSpeed, waveFrequency, waveAmplitude, waveColor, backgroundColor,
    colorNum, pixelSize, disableAnimation, enableMouseInteraction, mouseRadius,
  });
  propsRef.current = {
    waveSpeed, waveFrequency, waveAmplitude, waveColor, backgroundColor,
    colorNum, pixelSize, disableAnimation, enableMouseInteraction, mouseRadius,
  };

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const gl = canvas.getContext('webgl2', { antialias: false, alpha: false });
    if (!gl) return; // no WebGL2 → the container's background shows through

    const compile = (type: number, src: string) => {
      const s = gl.createShader(type)!;
      gl.shaderSource(s, src);
      gl.compileShader(s);
      if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) console.error(gl.getShaderInfoLog(s));
      return s;
    };
    const program = gl.createProgram()!;
    gl.attachShader(program, compile(gl.VERTEX_SHADER, VERT));
    gl.attachShader(program, compile(gl.FRAGMENT_SHADER, FRAG));
    gl.linkProgram(program);
    if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
      console.error(gl.getProgramInfoLog(program));
      return;
    }
    gl.useProgram(program);

    const buf = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, buf);
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 3, -1, -1, 3]), gl.STATIC_DRAW);
    const posLoc = gl.getAttribLocation(program, 'pos');
    gl.enableVertexAttribArray(posLoc);
    gl.vertexAttribPointer(posLoc, 2, gl.FLOAT, false, 0, 0);

    const u = (name: string) => gl.getUniformLocation(program, name);
    const loc = {
      resolution: u('resolution'), time: u('time'), waveSpeed: u('waveSpeed'),
      waveFrequency: u('waveFrequency'), waveAmplitude: u('waveAmplitude'),
      waveColor: u('waveColor'), backgroundColor: u('backgroundColor'),
      mousePos: u('mousePos'), enableMouseInteraction: u('enableMouseInteraction'),
      mouseRadius: u('mouseRadius'), colorNum: u('colorNum'), pixelSize: u('pixelSize'),
    };

    let w = 0;
    let h = 0;
    const resize = () => {
      const cw = Math.max(1, Math.floor(canvas.clientWidth));
      const ch = Math.max(1, Math.floor(canvas.clientHeight));
      if (cw === w && ch === h) return;
      w = cw; h = ch;
      canvas.width = w; canvas.height = h;
      gl.viewport(0, 0, w, h);
    };
    resize();
    const ro = new ResizeObserver(resize);
    ro.observe(canvas);

    const mouse = { x: 0, y: 0 };
    const onMove = (e: PointerEvent) => {
      const r = canvas.getBoundingClientRect();
      mouse.x = e.clientX - r.left;
      mouse.y = e.clientY - r.top;
    };
    canvas.addEventListener('pointermove', onMove);

    const start = performance.now();
    let raf = 0;
    const render = () => {
      const p = propsRef.current;
      gl.uniform2f(loc.resolution, w, h);
      gl.uniform1f(loc.time, p.disableAnimation ? 0 : (performance.now() - start) / 1000);
      gl.uniform1f(loc.waveSpeed, p.waveSpeed);
      gl.uniform1f(loc.waveFrequency, p.waveFrequency);
      gl.uniform1f(loc.waveAmplitude, p.waveAmplitude);
      gl.uniform3f(loc.waveColor, p.waveColor[0], p.waveColor[1], p.waveColor[2]);
      gl.uniform3f(loc.backgroundColor, p.backgroundColor[0], p.backgroundColor[1], p.backgroundColor[2]);
      gl.uniform2f(loc.mousePos, mouse.x, mouse.y);
      gl.uniform1i(loc.enableMouseInteraction, p.enableMouseInteraction ? 1 : 0);
      gl.uniform1f(loc.mouseRadius, p.mouseRadius);
      gl.uniform1f(loc.colorNum, p.colorNum);
      gl.uniform1f(loc.pixelSize, p.pixelSize);
      gl.drawArrays(gl.TRIANGLES, 0, 3);
      raf = requestAnimationFrame(render);
    };
    render();

    return () => {
      cancelAnimationFrame(raf);
      ro.disconnect();
      canvas.removeEventListener('pointermove', onMove);
      gl.deleteProgram(program);
      gl.deleteBuffer(buf);
    };
  }, []);

  return (
    <div className={`dither-container ${className}`} style={style}>
      <canvas ref={canvasRef} style={{ display: 'block', width: '100%', height: '100%' }} />
    </div>
  );
}

export default Dither;
