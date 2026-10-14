// Lit surfaces: terrain, block entities, entities, the hand and particles. Forward lighting with
// soft sun shadows, a warm torch light (and the light in your hand), and glowing blocks. With a
// texture pack that carries LabPBR maps (Patrix does), surfaces take their relief from the normal
// map, catch the sun by how smooth they are, and glow where the pack says they do.

varying vec2 texcoord;
varying vec2 lmcoord;
varying vec4 glcolor;
varying vec3 shadowPos;
varying float NdotL;
varying float material;
varying float upness;
varying float dist;
varying vec3 vNormal;
varying vec4 vTangent;
varying vec3 vView;

#ifdef VSH
attribute vec4 mc_Entity;
attribute vec2 mc_midTexCoord;
attribute vec4 at_tangent;
uniform mat4 gbufferModelView;
uniform mat4 shadowModelView;
uniform mat4 shadowProjection;
uniform vec3 cameraPosition;

void main() {
	texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
	lmcoord = lmRemap((gl_TextureMatrix[1] * gl_MultiTexCoord1).xy);
	glcolor = gl_Color;
	material = 0.0;
	vec4 viewPos = gl_ModelViewMatrix * gl_Vertex;
	vec4 playerPos = gbufferModelViewInverse * viewPos;
#ifdef TERRAIN
	float id = mc_Entity.x;
	if (id == 10001.0 || id == 10002.0 || id == 10004.0) material = 1.0;
	if (id == 10010.0) material = 2.0;
	float top = float(gl_MultiTexCoord0.t < mc_midTexCoord.t);
	playerPos.xyz = wave(playerPos.xyz, cameraPosition, id, top, lmcoord.y);
	viewPos = gbufferModelView * playerPos;
#endif
	gl_Position = gl_ProjectionMatrix * viewPos;
	dist = length(viewPos.xyz);
	vec3 normal = normalize(gl_NormalMatrix * safeNormal(gl_Normal));
	vNormal = normal;
	vTangent = vec4(gl_NormalMatrix * at_tangent.xyz, at_tangent.w);
	vView = viewPos.xyz;
	NdotL = dot(normal, normalize(shadowLightPosition));
	vec3 nWorld = mat3(gbufferModelViewInverse) * normal;
	upness = nWorld.y * 0.5 + 0.5;
	if (material == 1.0) {
		NdotL = 0.6;
		upness = 0.8;
	}
#ifdef SHADOWS
	float bias = 0.06 + length(playerPos.xyz) * 0.004;
	vec4 sp = shadowProjection * (shadowModelView * vec4(playerPos.xyz + nWorld * bias, 1.0));
	sp.xy /= distortFactor(sp.xy);
	sp.z *= 0.2;
	shadowPos = sp.xyz * 0.5 + 0.5;
#else
	shadowPos = vec3(0.0);
#endif
}
#endif

#ifdef FSH
uniform sampler2D gtexture;
#ifdef NORMAL_MAPS
uniform sampler2D normals;
#endif
#ifdef SPECULAR_MAPS
uniform sampler2D specular;
#endif
uniform int heldBlockLightValue;
uniform int heldBlockLightValue2;
#ifdef SHADOWS
uniform sampler2DShadow shadowtex0;
#endif
#ifdef ENTITIES
uniform vec4 entityColor;
#endif

float shadowAt(vec3 p) {
#ifdef SHADOWS
	if (p.x <= 0.0 || p.x >= 1.0 || p.y <= 0.0 || p.y >= 1.0 || p.z >= 1.0) {
		return 1.0;
	}
	float r = SHADOW_SOFTNESS / float(shadowMapResolution);
	float s = shadow2D(shadowtex0, p + vec3(-r, -r, 0.0)).r;
	s += shadow2D(shadowtex0, p + vec3(r, -r, 0.0)).r;
	s += shadow2D(shadowtex0, p + vec3(-r, r, 0.0)).r;
	s += shadow2D(shadowtex0, p + vec3(r, r, 0.0)).r;
	return s * 0.25;
#else
	return 1.0;
#endif
}

/* DRAWBUFFERS:0 */
void main() {
	vec4 tex = texture2D(gtexture, texcoord);
#ifdef TERRAIN
	vec4 albedo = tex * vec4(glcolor.rgb, 1.0);
	float ao = glcolor.a;
#else
	vec4 albedo = tex * glcolor;
	float ao = 1.0;
#endif
#ifdef ENTITIES
	albedo.rgb = mix(albedo.rgb, entityColor.rgb, entityColor.a);
#endif
	if (albedo.a < 0.1) {
		discard;
	}
	vec3 col = toLinear(albedo.rgb);
	vec3 base = col;
	float shadow = 1.0;
#ifndef HAND
	shadow = NdotL > 0.0 ? shadowAt(shadowPos) : 0.0;
#endif
	float ndl = NdotL;
	vec3 n = normalize(vNormal);
#ifdef NORMAL_MAPS
	// The relief in the normal map shades the surface; the geometric normal still decides which
	// faces see the sun at all, so a mapped bump never lights the dark side of a block.
	if (material != 1.0 && dot(vTangent.xyz, vTangent.xyz) > 0.01) {
		vec4 nm = texture2D(normals, texcoord);
		vec2 xy = (nm.xy * 2.0 - 1.0) * NORMAL_STRENGTH;
		vec3 tN = normalize(vec3(xy, sqrt(max(0.0, 1.0 - dot(xy, xy)))));
		vec3 T = normalize(vTangent.xyz);
		vec3 B = normalize(cross(n, T)) * (vTangent.w < 0.0 ? -1.0 : 1.0);
		n = normalize(mat3(T, B, n) * tN);
		float mapped = dot(n, normalize(shadowLightPosition));
		ndl = NdotL > 0.0 ? mix(NdotL, mapped, 0.85) : min(mapped, NdotL);
		ao *= mix(1.0, nm.b, 0.75);
	}
#endif
	vec3 light = lighting(lmcoord, max(ndl, 0.0), shadow, upness);
	// The light in your hand.
	float held = float(max(heldBlockLightValue, heldBlockLightValue2)) / 15.0;
	if (held > 0.0) {
		float reach = held * 14.0;
		light += torchColor() * pow(clamp(1.0 - dist / reach, 0.0, 1.0), 2.0) * held * 0.9;
	}
	col *= light * mix(1.0, ao * ao, 0.8);
	if (material == 2.0) {
		float l = max(albedo.r, max(albedo.g, albedo.b));
		col += toLinear(albedo.rgb) * smoothstep(0.45, 0.95, l) * 3.0;
	}
#ifdef SPECULAR_MAPS
	vec4 spec = texture2D(specular, texcoord);
	// Smooth surfaces catch the sun: polished stone, metal, ice, wet clay.
	float smoothness = spec.r;
	if (smoothness > 0.08 && ndl > 0.0 && shadow > 0.0) {
		vec3 V = normalize(-vView);
		vec3 L = normalize(shadowLightPosition);
		vec3 H = normalize(L + V);
		float rough = (1.0 - smoothness) * (1.0 - smoothness);
		float a2 = max(rough * rough, 0.004);
		float ndh = max(dot(n, H), 0.0);
		float k = ndh * ndh * (a2 - 1.0) + 1.0;
		float d = min(a2 / (3.14159 * k * k), 24.0);
		bool metal = spec.g >= 0.9;
		float f0 = metal ? 0.65 : max(spec.g, 0.04);
		float fres = f0 + (1.0 - f0) * pow(1.0 - max(dot(H, V), 0.0), 5.0);
		vec3 tint = metal ? base : vec3(1.0);
		col += sunlightColor() * tint * d * fres * max(ndl, 0.0) * shadow * smoothstep(0.02, 0.5, lmcoord.y) * 0.18;
	}
	// LabPBR emission lives in the alpha channel (255 means none).
	if (spec.a < 0.999) {
		col += base * spec.a * (255.0 / 254.0) * 2.4;
	}
#endif
	gl_FragData[0] = vec4(col, albedo.a);
}
#endif
