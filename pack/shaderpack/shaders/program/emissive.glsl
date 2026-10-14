// Things that shine by themselves: creatures' eyes and glow layers, beacon beams.

varying vec2 texcoord;
varying vec4 glcolor;

#ifdef VSH
void main() {
	gl_Position = ftransform();
	texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
	glcolor = gl_Color;
}
#endif

#ifdef FSH
uniform sampler2D gtexture;

/* DRAWBUFFERS:0 */
void main() {
	vec4 c = texture2D(gtexture, texcoord) * glcolor;
	if (c.a < 0.02) {
		discard;
	}
	gl_FragData[0] = vec4(toLinear(c.rgb) * 2.6, c.a);
}
#endif
