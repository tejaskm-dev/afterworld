// The sun and moon (and anything else drawn on the sky), bright enough to bloom.

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
	gl_FragData[0] = vec4(toLinear(c.rgb) * 4.0 * (1.0 - rainStrength), c.a);
}
#endif
