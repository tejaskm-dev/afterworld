// Rain and snow: softer, lit by the sky.

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
	gl_FragData[0] = vec4(toLinear(c.rgb) * (ambientColor() * 1.6 + 0.08), c.a * 0.55);
}
#endif
