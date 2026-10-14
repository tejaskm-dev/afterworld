// Translucent blocks. Water gets a living surface: small waves, the sky in it at a glancing angle and
// a glint of sun, its colour the biome's (not the texture pack's own water, which may already be
// coloured). It marks where it is in colortex2, and the composite pass darkens it by how much water
// lies between the eye and the bed, so shallows stay clear and the deep turns blue. The sides of
// falls and streams use their own facing (no sheet of sky on them). Seen from under it, the surface
// is a teal ceiling with the sky bright through it overhead. Glass and ice are simply lit.

varying vec2 texcoord;
varying vec2 lmcoord;
varying vec4 glcolor;
varying vec3 shadowPos;
varying vec3 worldPos;
varying vec3 viewPosV;
varying float NdotL;
varying float isWater;
varying vec3 normalWorld;

#ifdef VSH
attribute vec4 mc_Entity;
uniform mat4 gbufferModelView;
uniform mat4 shadowModelView;
uniform mat4 shadowProjection;
uniform vec3 cameraPosition;

void main() {
	texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
	lmcoord = lmRemap((gl_TextureMatrix[1] * gl_MultiTexCoord1).xy);
	glcolor = gl_Color;
	isWater = float(mc_Entity.x == 10003.0);
	vec4 viewPos = gl_ModelViewMatrix * gl_Vertex;
	vec4 playerPos = gbufferModelViewInverse * viewPos;
	worldPos = playerPos.xyz + cameraPosition;
#ifdef WATER_WAVES
	if (isWater > 0.5) {
		playerPos.y += (sin(frameTimeCounter * 1.4 + worldPos.x * 0.9 + worldPos.z * 0.6) - 1.0) * 0.025;
	}
#endif
	viewPos = gbufferModelView * playerPos;
	viewPosV = viewPos.xyz;
	gl_Position = gl_ProjectionMatrix * viewPos;
	vec3 normal = normalize(gl_NormalMatrix * safeNormal(gl_Normal));
	normalWorld = mat3(gbufferModelViewInverse) * normal;
	NdotL = dot(normal, normalize(shadowLightPosition));
#ifdef SHADOWS
	vec3 nWorld = mat3(gbufferModelViewInverse) * normal;
	vec4 sp = shadowProjection * (shadowModelView * vec4(playerPos.xyz + nWorld * 0.08, 1.0));
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
#ifdef SHADOWS
uniform sampler2DShadow shadowtex0;
#endif

float waterShadow(vec3 p) {
#ifdef SHADOWS
	if (p.x <= 0.0 || p.x >= 1.0 || p.y <= 0.0 || p.y >= 1.0 || p.z >= 1.0) {
		return 1.0;
	}
	return shadow2D(shadowtex0, p).r;
#else
	return 1.0;
#endif
}

/* DRAWBUFFERS:02 */
void main() {
	vec4 tex = texture2D(gtexture, texcoord) * glcolor;
	float shadow = NdotL > 0.0 ? waterShadow(shadowPos) : 0.0;
	vec3 light = lighting(lmcoord, max(NdotL, 0.0), shadow, 1.0);
	if (isWater > 0.5) {
		vec3 viewDir = normalize(mat3(gbufferModelViewInverse) * viewPosV);
		vec3 n = vec3(0.0, 1.0, 0.0);
#ifdef WATER_WAVES
		float t = frameTimeCounter;
		vec2 g = vec2(cos(worldPos.x * 1.3 + t * 1.6) + 0.6 * cos(worldPos.x * 0.7 + worldPos.z * 1.1 + t * 1.1),
			cos(worldPos.z * 1.5 + t * 1.8) + 0.6 * cos(worldPos.z * 0.8 - worldPos.x * 0.9 + t * 1.3));
		n = normalize(vec3(-g.x * 0.045, 1.0, -g.y * 0.045));
#endif
		bool side = abs(normalWorld.y) < 0.5;
		if (side) {
			n = normalize(normalWorld);
		}
		bool below = viewDir.y > 0.0 && !side;
		if (below) {
			n = -n;
		}
		float fres = 0.02 + 0.98 * pow(1.0 - clamp(dot(-viewDir, n), 0.0, 1.0), 5.0);
		if (side) {
			fres *= 0.3;
		}
		// The biome's water colour, with only the texture's light and shade (a coloured pack's hue would fight it).
		vec3 tint = toLinear(glcolor.rgb);
		float pattern = dot(tex.rgb / max(glcolor.rgb, vec3(0.05)), vec3(0.299, 0.587, 0.114));
		vec3 body = tint * (0.7 + 0.3 * clamp(pattern, 0.0, 1.5)) * vec3(0.26, 0.46, 0.56) * light;
		// The sky in the water, softened and only out in the open.
		vec3 refl = min(skyGradient(reflect(viewDir, n)), vec3(1.1)) * 0.32 * smoothstep(0.2, 0.8, lmcoord.y);
		vec3 col;
		float alpha;
		if (below) {
			float window = smoothstep(0.78, 0.97, viewDir.y);
			col = mix(tint * vec3(0.10, 0.22, 0.26) * light, skyGradient(viewDir) * 0.6, window * (1.0 - fres));
			alpha = mix(0.92, 0.6, window);
		} else {
			col = mix(body, refl, fres * 0.75);
			alpha = mix(0.5, 0.9, fres);
			vec3 h = normalize(sunDirWorld() - viewDir);
			col += sunlightColor() * pow(max(dot(n, h), 0.0), 320.0) * 1.6 * shadow * smoothstep(0.3, 0.9, lmcoord.y) * (1.0 - nightWeight() * 0.6) * (side ? 0.2 : 1.0);
		}
		gl_FragData[0] = vec4(col, clamp(alpha, 0.0, 1.0));
		// Water is here (for the composite pass's depth): written outright, whatever was under it.
		gl_FragData[1] = vec4(1.0, 0.0, 0.0, 1.0);
	} else {
		if (tex.a < 0.02) {
			discard;
		}
		gl_FragData[0] = vec4(toLinear(tex.rgb) * light, tex.a);
		// Glass and ice leave the water mask as it was (alpha 0 in its blend).
		gl_FragData[1] = vec4(0.0);
	}
}
#endif
