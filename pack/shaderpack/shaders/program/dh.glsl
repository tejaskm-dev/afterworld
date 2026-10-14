// Distant Horizons' far terrain: lit like the near world (without shadows), water tinted.

varying vec4 glcolor;
varying vec2 lmcoord;
varying float NdotL;
varying float upness;

#ifdef VSH
void main() {
	gl_Position = ftransform();
	glcolor = gl_Color;
	lmcoord = lmRemap((gl_TextureMatrix[1] * gl_MultiTexCoord1).xy);
	vec3 n = normalize(gl_NormalMatrix * safeNormal(gl_Normal));
	NdotL = dot(n, normalize(shadowLightPosition));
	upness = (mat3(gbufferModelViewInverse) * n).y * 0.5 + 0.5;
}
#endif

#ifdef FSH
/* DRAWBUFFERS:0 */
void main() {
	vec3 col = toLinear(glcolor.rgb) * lighting(lmcoord, max(NdotL, 0.0), 1.0, upness);
#ifdef DH_WATER
	gl_FragData[0] = vec4(col * vec3(0.6, 0.85, 0.95), 0.85);
#else
	gl_FragData[0] = vec4(col, 1.0);
#endif
}
#endif
