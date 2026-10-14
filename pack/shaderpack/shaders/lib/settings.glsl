// Afterworld Shaders: settings. Every option here appears in the shader settings screen.

#define SHADOWS
#define SHADOW_RES 1536 // [1024 1536 2048 3072]
#define SHADOW_DIST 64.0 // [48.0 64.0 96.0 128.0 160.0]
#define SHADOW_SOFTNESS 1.0 // [0.5 1.0 1.5 2.0]
#define WAVING_PLANTS
#define WATER_WAVES
#define GODRAYS
#define GODRAY_STRENGTH 0.6 // [0.0 0.3 0.6 0.9 1.2]
#define BLOOM
#define BLOOM_STRENGTH 0.5 // [0.0 0.25 0.5 0.75 1.0]
#define FOG_DENSITY 1.0 // [0.5 0.75 1.0 1.25 1.5 2.0]
#define EXPOSURE 1.0 // [0.7 0.85 1.0 1.15 1.3]
#define SATURATION 1.10 // [0.80 0.90 1.00 1.10 1.20 1.30]
#define VIGNETTE 0.30 // [0.00 0.15 0.30 0.45]
#define TORCH_WARMTH 1.0 // [0.5 1.0 1.5]
#define NORMAL_MAPS
#define SPECULAR_MAPS
#define NORMAL_STRENGTH 1.0 // [0.5 0.75 1.0 1.25]

#ifdef NETHER
	#undef SHADOWS
	#undef GODRAYS
#endif
#ifdef END
	#undef SHADOWS
	#undef GODRAYS
#endif

const int shadowMapResolution = SHADOW_RES;
const float shadowDistance = SHADOW_DIST;
const float sunPathRotation = -25.0;
const bool shadowHardwareFiltering = true;
const float shadowDistanceRenderMul = 1.0;
const float SHADOW_DISTORT = 0.85;
