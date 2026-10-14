// The shadow map, seen from the sun (or the moon). Water casts no shadow; plants sway as they do above.

varying vec2 texcoord;
varying vec4 glcolor;

#ifdef VSH
attribute vec4 mc_Entity;
attribute vec2 mc_midTexCoord;
uniform mat4 shadowModelView;
uniform mat4 shadowModelViewInverse;
uniform vec3 cameraPosition;

void main() {
	texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
	glcolor = gl_Color;
	if (mc_Entity.x == 10003.0) {
		gl_Position = vec4(10.0, 10.0, 10.0, 1.0);
		return;
	}
	vec4 viewPos = gl_ModelViewMatrix * gl_Vertex;
#ifdef WAVING_PLANTS
	vec4 playerPos = shadowModelViewInverse * viewPos;
	vec2 lm = lmRemap((gl_TextureMatrix[1] * gl_MultiTexCoord1).xy);
	float top = float(gl_MultiTexCoord0.t < mc_midTexCoord.t);
	playerPos.xyz = wave(playerPos.xyz, cameraPosition, mc_Entity.x, top, lm.y);
	viewPos = shadowModelView * playerPos;
#endif
	gl_Position = gl_ProjectionMatrix * viewPos;
	gl_Position.xy /= distortFactor(gl_Position.xy);
	gl_Position.z *= 0.2;
}
#endif

#ifdef FSH
uniform sampler2D gtexture;

void main() {
	vec4 c = texture2D(gtexture, texcoord) * glcolor;
	if (c.a < 0.1) {
		discard;
	}
	gl_FragData[0] = c;
}
#endif
