//	VIDIS is a simulation and visualisation framework for distributed systems.
//	Copyright (C) 2009 Dominik Psenner, Christoph Caks
//	This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation; either version 3 of the License, or (at your option) any later version.
//	This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
//	You should have received a copy of the GNU General Public License along with this program; if not, see <http://www.gnu.org/licenses/>.
varying vec3 normal;
varying vec3 incom;
uniform bool black;

void myRefract(in vec3 incom, in vec3 normal, in float index_external, in float index_internal,
				out vec3 reflection, out vec3 refraction,
				out float reflectance, out float transmittance) 
{
				
	float eta = index_external/index_internal;
	float cos_theta1 = dot(incom, normal);
	float cos_theta2 = sqrt(1.0 - ((eta * eta) * ( 1.0 - (cos_theta1 * cos_theta1))));
	reflection = incom - 2.0 * cos_theta1 * normal;
	refraction = (eta * incom) + (cos_theta2 - eta * cos_theta1) * normal;

	float fresnel_rs = (index_external * cos_theta1 - index_internal * cos_theta2 ) /
		(index_external * cos_theta1 + index_internal * cos_theta2);


	float fresnel_rp = (index_internal * cos_theta1 - index_external * cos_theta2 ) /
			(index_internal * cos_theta1 + index_external * cos_theta2);

	reflectance =  (fresnel_rs * fresnel_rs + fresnel_rp * fresnel_rp) / 2.0;
	transmittance =  ((1.0-fresnel_rs) * (1.0-fresnel_rs) + (1.0-fresnel_rp) * (1.0-fresnel_rp)) / 2.0;
}

void main() {

	vec3 refraction_ray, reflection_ray;
	float fresnel_R, fresnel_T;
	
	myRefract( incom, normal, 1.0, 1.7, reflection_ray, refraction_ray, fresnel_R, fresnel_T);


	refraction_ray = (gl_ModelViewMatrix * vec4(refraction_ray,0.0)).xyz;
	reflection_ray = (gl_ModelViewMatrix * vec4(reflection_ray,0.0)).xyz;


	vec4 reflect_color = vec4( (gl_Color * 0.5).xyz, 0.01);
	vec4 refract_color = vec4( gl_Color.xyz, 0.3 );
	
	fresnel_T = fresnel_T * 1.5;	
	fresnel_R = fresnel_R * 0.5;

	gl_FragColor =
		  reflect_color * fresnel_R +
		  refract_color * fresnel_T;
		  
	if ( black == true ) {
		gl_FragColor = vec4( 0, 0, 0, 1 );
	}

}
