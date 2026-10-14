// The picture: bloom from the bright things, eyes that adjust to caves and noon, a filmic curve,
// a little more colour, cool shadows, and a soft vignette.

/*
const int colortex0Format = RGBA16F;
*/
const bool colortex0MipmapEnabled = true;

varying vec2 texcoord;

#ifdef VSH
void main() {
	gl_Position = ftransform();
	texcoord = gl_MultiTexCoord0.xy;
}
#endif

#ifdef FSH
uniform sampler2D colortex0;

vec3 aces(vec3 x) {
	const float a = 2.51;
	const float b = 0.03;
	const float c = 2.43;
	const float d = 0.59;
	const float e = 0.14;
	return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

void main() {
	vec3 col = texture2D(colortex0, texcoord).rgb;
#ifdef BLOOM
	vec3 b = max(texture2D(colortex0, texcoord, 2.0).rgb - 1.0, 0.0) * 0.30;
	b += max(texture2D(colortex0, texcoord, 3.5).rgb - 0.9, 0.0) * 0.30;
	b += max(texture2D(colortex0, texcoord, 5.0).rgb - 0.8, 0.0) * 0.25;
	b += max(texture2D(colortex0, texcoord, 6.5).rgb - 0.7, 0.0) * 0.20;
	col += b * BLOOM_STRENGTH;
#endif
	float eye = float(eyeBrightnessSmooth.y) / 240.0;
	col *= EXPOSURE * mix(1.9, 1.0, eye);
	col = aces(col);
	float lum = dot(col, vec3(0.2126, 0.7152, 0.0722));
	col = mix(vec3(lum), col, SATURATION);
	col += vec3(-0.004, 0.0, 0.014) * (1.0 - lum) * (1.0 - lum);
	col *= mix(vec3(1.0), vec3(1.03, 1.0, 0.96), lum);
	vec2 d = texcoord - 0.5;
	col *= 1.0 - dot(d, d) * VIGNETTE * 1.4;
	col = pow(max(col, 0.0), vec3(1.0 / 2.2));
	gl_FragColor = vec4(col, 1.0);
}
#endif
