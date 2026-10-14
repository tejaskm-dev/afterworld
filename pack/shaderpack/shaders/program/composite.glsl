// Atmosphere: the depth of water seen from above it (darker and bluer the more of it lies between
// the eye and the bed, where the water pass marked colortex2), distance fog that fades into the
// sky's own colour, thicker in the rain and in the Nether, water and lava when you are in them, and
// soft rays from the sun or moon.

varying vec2 texcoord;

#ifdef VSH
void main() {
	gl_Position = ftransform();
	texcoord = gl_MultiTexCoord0.xy;
}
#endif

#ifdef FSH
// Where water is (written by the water pass), cleared every frame. (The format is read by Iris from
// this comment; the GLSL compiler has no R8.)
/*
const int colortex2Format = R8;
*/
const bool colortex2Clear = true;
const vec4 colortex2ClearColor = vec4(0.0, 0.0, 0.0, 0.0);
uniform sampler2D colortex0;
uniform sampler2D colortex2;
uniform sampler2D depthtex0;
uniform sampler2D depthtex1;
uniform mat4 gbufferProjectionInverse;
uniform mat4 gbufferProjection;
uniform float far;
uniform float viewWidth;
uniform float viewHeight;
#ifdef DISTANT_HORIZONS
uniform sampler2D dhDepthTex0;
uniform mat4 dhProjectionInverse;
uniform float dhFarPlane;
#endif

vec3 viewFrom(vec2 uv, float depth, mat4 projInv) {
	vec4 p = projInv * vec4(vec3(uv, depth) * 2.0 - 1.0, 1.0);
	return p.xyz / p.w;
}

/* DRAWBUFFERS:0 */
void main() {
	vec3 col = texture2D(colortex0, texcoord).rgb;
	float depth = texture2D(depthtex0, texcoord).r;
	bool sky = depth >= 1.0;
	vec3 vp = viewFrom(texcoord, depth, gbufferProjectionInverse);
	float border = far;
#ifdef DISTANT_HORIZONS
	float dhd = texture2D(dhDepthTex0, texcoord).r;
	if (sky && dhd < 1.0) {
		vp = viewFrom(texcoord, dhd, dhProjectionInverse);
		sky = false;
		border = dhFarPlane;
	}
#endif
	vec3 dir = normalize(mat3(gbufferModelViewInverse) * vp);
	if (!sky && isEyeInWater == 0 && texture2D(colortex2, texcoord).r > 0.5) {
		// Water seen from above: how far the ray runs through it, from the surface to the bed.
		float bedDepth = texture2D(depthtex1, texcoord).r;
		float thick = bedDepth >= 1.0 ? 48.0 : max(length(viewFrom(texcoord, bedDepth, gbufferProjectionInverse)) - length(vp), 0.0);
		float absorb = 1.0 - exp(-thick * 0.30);
		vec3 deep = vec3(0.006, 0.034, 0.058) * (sunlightColor() * 0.35 + ambientColor() * 1.4);
		col = mix(col, deep, absorb * 0.95);
	}
	if (!sky) {
		float d = length(vp);
		float dens = 0.0016 * FOG_DENSITY * (1.0 + rainStrength * 3.5);
#if defined NETHER
		dens = 0.012 * FOG_DENSITY;
#elif defined END
		dens = 0.004 * FOG_DENSITY;
#endif
		float fog = 1.0 - exp(-d * dens);
		fog = max(fog, smoothstep(border * 0.72, border, d));
		col = mix(col, skyGradient(normalize(vec3(dir.x, max(dir.y, 0.0) * 0.5 + 0.02, dir.z))), fog);
	}
	if (isEyeInWater == 1) {
		// Under water: everything filtered teal, and a fog that is never quite clear (even the
		// surface overhead is seen through water), darker the further from the daylight.
		float d = length(vp);
		float daylight = 0.25 + 0.75 * float(eyeBrightnessSmooth.y) / 240.0;
		col *= vec3(0.42, 0.76, 0.88);
		col = mix(col, vec3(0.010, 0.060, 0.085) * daylight * (sunlightColor() * 0.4 + ambientColor()), max(1.0 - exp(-d * 0.09), 0.32));
	} else if (isEyeInWater == 2) {
		col = mix(col, vec3(1.0, 0.3, 0.05), 1.0 - exp(-length(vp) * 0.6));
	} else if (isEyeInWater == 3) {
		col = mix(col, vec3(0.75, 0.85, 1.0), 1.0 - exp(-length(vp) * 0.5));
	}
#ifdef GODRAYS
	bool sunUp = sunHeight() > -0.05;
	vec3 lightPos = sunUp ? sunPosition : moonPosition;
	vec4 sp = gbufferProjection * vec4(lightPos, 1.0);
	vec2 sun = sp.xy / max(sp.w, 1e-4) * 0.5 + 0.5;
	float fall = exp(-length((texcoord - sun) * vec2(viewWidth / viewHeight, 1.0)) * 2.2);
	float strength = GODRAY_STRENGTH * mix(0.25, 0.9, 1.0 - dayWeight()) * (1.0 - rainStrength);
	// Only where the glow can still be seen: most of the screen, most of the time, skips the march.
	if (sp.w > 0.0 && isEyeInWater == 0 && fall * strength > 0.004) {
		// Ten steps toward the light, each pixel starting a little further along (dithered), in place of sixteen.
		float dither = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
		vec2 delta = (sun - texcoord) / 10.0;
		vec2 uv = texcoord + delta * dither;
		float acc = 0.0;
		for (int i = 0; i < 10; i++) {
			if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
				break;
			}
			float open = float(texture2D(depthtex0, uv).r >= 1.0);
#ifdef DISTANT_HORIZONS
			open *= float(texture2D(dhDepthTex0, uv).r >= 1.0);
#endif
			acc += open;
			uv += delta;
		}
		acc /= 10.0;
		vec3 rays = sunUp ? sunlightColor() : vec3(0.30, 0.40, 0.70) * 0.4;
		col += rays * acc * fall * strength * 0.5;
	}
#endif
	gl_FragData[0] = vec4(col, 1.0);
}
#endif
