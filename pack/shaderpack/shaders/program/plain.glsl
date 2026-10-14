// Plain colour without a texture: lightning, and Afterworld's shapes of light for attacks and
// powers. The game draws each shape twice, and tells the layers apart by alpha: up to a half is a
// body in its true colour (alpha 0..1 scaled into 0..0.5); from 0.52 up is its core, which shines
// (the gap keeps a core's faded edge from reading as a solid body).
// Like everything else here, the colour goes into linear light.

varying vec4 glcolor;

#ifdef VSH
void main() {
	gl_Position = ftransform();
	glcolor = gl_Color;
}
#endif

#ifdef FSH
/* DRAWBUFFERS:0 */
void main() {
	vec4 c = glcolor;
	if (c.a < 0.51) {
		float a = c.a * 2.0;
		if (a < 0.004) {
			discard;
		}
		gl_FragData[0] = vec4(toLinear(c.rgb), a);
	} else {
		float a = (c.a - 0.52) / 0.48;
		if (a < 0.004) {
			discard;
		}
		gl_FragData[0] = vec4(toLinear(c.rgb) * 2.4, a);
	}
}
#endif
