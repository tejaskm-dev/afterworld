// Clouds, lit by the hour: gold at noon, rose at dusk, ink at night.

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
	vec3 col = toLinear(c.rgb) * (sunlightColor() * 0.5 + ambientColor() * 1.3);
	gl_FragData[0] = vec4(col, c.a * 0.85);
}
#endif
