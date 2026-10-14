// Afterworld Shaders: shared uniforms, the time of day, light colours, the sky.

uniform vec3 sunPosition;
uniform vec3 moonPosition;
uniform vec3 upPosition;
uniform vec3 shadowLightPosition;
uniform float rainStrength;
uniform float nightVision;
uniform float blindness;
uniform float darknessFactor;
uniform ivec2 eyeBrightnessSmooth;
uniform int isEyeInWater;
uniform vec3 fogColor;
uniform mat4 gbufferModelViewInverse;
uniform float frameTimeCounter;

vec3 toLinear(vec3 c) {
	return pow(c, vec3(2.2));
}

vec2 lmRemap(vec2 lm) {
	return clamp(lm * (33.05 / 32.0) - 1.05 / 32.0, 0.0, 1.0);
}

float distortFactor(vec2 p) {
	return length(p) * SHADOW_DISTORT + (1.0 - SHADOW_DISTORT);
}

// How high the sun stands: 1 at noon, 0 at the horizon, -1 at midnight.
float sunHeight() {
	return dot(normalize(sunPosition), normalize(upPosition));
}

float dayWeight() {
	return smoothstep(-0.02, 0.32, sunHeight());
}

float nightWeight() {
	return smoothstep(0.02, -0.14, sunHeight());
}

vec3 sunDirWorld() {
	return normalize(mat3(gbufferModelViewInverse) * sunPosition);
}

// Sunlight by time of day: warm gold at noon, rose and amber at the ends of the day, cool moonlight at night.
vec3 sunlightColor() {
	vec3 day = vec3(1.0, 0.92, 0.80) * 2.1;
	vec3 dusk = vec3(1.0, 0.50, 0.28) * 1.5;
	vec3 night = vec3(0.30, 0.40, 0.72) * 0.30;
	vec3 c = mix(dusk, day, dayWeight());
	c = mix(c, night, nightWeight());
	return c * (1.0 - rainStrength * 0.75);
}

// The sky's own light, falling on everything open to it.
vec3 ambientColor() {
#if defined NETHER
	return vec3(0.55, 0.30, 0.22) * 0.45;
#elif defined END
	return vec3(0.36, 0.30, 0.48) * 0.60;
#else
	vec3 day = vec3(0.52, 0.64, 0.90) * 0.78;
	vec3 dusk = vec3(0.62, 0.46, 0.60) * 0.55;
	vec3 night = vec3(0.09, 0.12, 0.25) * 0.55;
	vec3 c = mix(dusk, day, dayWeight());
	c = mix(c, night, nightWeight());
	return mix(c, vec3(dot(c, vec3(0.33))) * 0.8, rainStrength * 0.6);
#endif
}

vec3 torchColor() {
	return mix(vec3(1.0, 0.82, 0.62), vec3(1.0, 0.58, 0.28), clamp(TORCH_WARMTH - 0.5, 0.0, 1.0)) * 1.9;
}

// Sky, sun and moon light, block light and the darkness of caves, gathered for one surface.
vec3 lighting(vec2 lm, float ndl, float shadow, float upness) {
	float sky = lm.y;
	vec3 light = vec3(0.0);
#if !defined NETHER && !defined END
	light += sunlightColor() * ndl * shadow * smoothstep(0.02, 0.5, sky);
#endif
#if defined NETHER || defined END
	light += ambientColor() * (0.8 + 0.2 * upness);
#else
	light += ambientColor() * sky * sky * (0.72 + 0.28 * upness);
#endif
	light += torchColor() * (pow(lm.x, 3.0) * 0.85 + pow(lm.x, 9.0) * 0.6);
	light += vec3(0.045, 0.05, 0.068);
	light += vec3(0.5) * nightVision;
	light *= 1.0 - 0.85 * max(blindness, darknessFactor);
	return light;
}

// The sky's colour in a world direction: a deep zenith, a soft bright horizon, the glow round the sun.
vec3 skyGradient(vec3 dir) {
#if defined NETHER
	return toLinear(fogColor) * 0.6;
#elif defined END
	return vec3(0.03, 0.02, 0.05) + vec3(0.06, 0.03, 0.09) * pow(1.0 - abs(dir.y), 3.0);
#else
	float up = clamp(dir.y, -1.0, 1.0);
	float d = dayWeight();
	float n = nightWeight();
	vec3 zenith = mix(mix(vec3(0.20, 0.20, 0.50), vec3(0.16, 0.36, 0.82), d), vec3(0.008, 0.012, 0.035), n);
	vec3 horizon = mix(mix(vec3(1.00, 0.52, 0.32), vec3(0.66, 0.78, 0.96), d), vec3(0.035, 0.045, 0.09), n);
	float t = pow(1.0 - max(up, 0.0), 3.5);
	vec3 c = mix(zenith, horizon, t);
	if (up < 0.0) {
		c = mix(horizon, horizon * 0.35, clamp(-up * 2.5, 0.0, 1.0));
	}
	vec3 sd = sunDirWorld();
	float s = max(dot(dir, sd), 0.0);
	c += sunlightColor() * (pow(s, 5.0) * 0.18 + pow(s, 48.0) * 0.55) * (1.0 - n);
	float m = max(dot(dir, -sd), 0.0);
	c += vec3(0.20, 0.26, 0.42) * pow(m, 24.0) * 0.25 * n;
	c = mix(c, vec3(dot(c, vec3(0.33))) * 0.55, rainStrength * 0.8);
	return c * 1.15;
#endif
}

// Plants sway from their tops, leaves breathe, more in the rain and only out in the open.
vec3 wave(vec3 playerPos, vec3 cameraPos, float id, float top, float sky) {
#ifdef WAVING_PLANTS
	vec3 w = playerPos + cameraPos;
	float t = frameTimeCounter;
	float wind = (0.6 + 0.4 * sin(t * 0.37 + w.x * 0.05)) * (1.0 + rainStrength * 1.5) * smoothstep(0.3, 0.9, sky);
	if (id == 10001.0 || id == 10004.0) {
		playerPos.x += sin(t * 1.7 + w.x * 0.8 + w.z * 0.4) * 0.07 * wind * top;
		playerPos.z += cos(t * 1.3 + w.z * 0.7 + w.x * 0.3) * 0.06 * wind * top;
	} else if (id == 10002.0) {
		playerPos.x += sin(t * 1.1 + w.x * 0.6 + w.y * 0.4) * 0.035 * wind;
		playerPos.y += sin(t * 1.5 + w.z * 0.5) * 0.02 * wind;
		playerPos.z += cos(t * 1.2 + w.z * 0.6 + w.y * 0.3) * 0.035 * wind;
	}
#endif
	return playerPos;
}

vec3 safeNormal(vec3 n) {
	return dot(n, n) < 0.01 ? vec3(0.0, 1.0, 0.0) : n;
}
