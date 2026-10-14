// The sky dome: our own gradient (deep zenith, bright horizon, a glow round the sun), and the stars.

varying vec3 viewPosV;
varying vec4 glcolor;

#ifdef VSH
void main() {
	gl_Position = ftransform();
	viewPosV = (gl_ModelViewMatrix * gl_Vertex).xyz;
	glcolor = gl_Color;
}
#endif

#ifdef FSH
uniform int renderStage;

/* DRAWBUFFERS:0 */
void main() {
#ifdef MC_RENDER_STAGE_STARS
	if (renderStage == MC_RENDER_STAGE_STARS) {
		gl_FragData[0] = vec4(toLinear(glcolor.rgb) * 3.0, glcolor.a);
		return;
	}
#endif
#ifdef MC_RENDER_STAGE_SUNSET
	if (renderStage == MC_RENDER_STAGE_SUNSET) {
		discard;
	}
#endif
	vec3 dir = normalize(mat3(gbufferModelViewInverse) * viewPosV);
	gl_FragData[0] = vec4(skyGradient(dir), 1.0);
}
#endif
