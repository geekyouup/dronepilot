package com.droid.dronepilot.renderer

import android.opengl.GLES20
import android.util.Log

object ShaderUtils {
    private const val TAG = "ShaderUtils"

    const val VERTEX_SHADER = """
        uniform mat4 uMVPMatrix;
        uniform mat4 uModelMatrix;
        uniform vec3 uLightDir;
        
        attribute vec4 aPosition;
        attribute vec3 aNormal;
        attribute vec4 aColor;
        
        varying vec4 vColor;
        varying float vDiffuse;
        varying float vDistance;
        
        void main() {
            gl_Position = uMVPMatrix * aPosition;
            vec3 worldNormal = normalize(mat3(uModelMatrix[0].xyz, uModelMatrix[1].xyz, uModelMatrix[2].xyz) * aNormal);
            vDiffuse = max(dot(worldNormal, normalize(uLightDir)), 0.0) * 0.7 + 0.3;
            vColor = aColor;
            vDistance = length(gl_Position.xyz);
        }
    """

    const val FRAGMENT_SHADER = """
        precision mediump float;
        
        varying vec4 vColor;
        varying float vDiffuse;
        varying float vDistance;
        
        void main() {
            // Distance fog: blend with atmospheric sky color at long distances
            float fogFactor = clamp((vDistance - 40.0) / 120.0, 0.0, 1.0);
            vec3 skyColor = vec3(0.53, 0.75, 0.95);
            vec3 litColor = vColor.rgb * vDiffuse;
            vec3 finalColor = mix(litColor, skyColor, fogFactor);
            gl_FragColor = vec4(finalColor, vColor.a);
        }
    """

    fun createProgram(vertexSource: String = VERTEX_SHADER, fragmentSource: String = FRAGMENT_SHADER): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        if (vertexShader == 0) return 0

        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        if (fragmentShader == 0) return 0

        var program = GLES20.glCreateProgram()
        if (program != 0) {
            GLES20.glAttachShader(program, vertexShader)
            GLES20.glAttachShader(program, fragmentShader)
            GLES20.glLinkProgram(program)

            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] != GLES20.GL_TRUE) {
                Log.e(TAG, "Could not link program: " + GLES20.glGetProgramInfoLog(program))
                GLES20.glDeleteProgram(program)
                program = 0
            }
        }
        return program
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader != 0) {
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)

            val compiled = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
            if (compiled[0] == 0) {
                Log.e(TAG, "Could not compile shader $type: " + GLES20.glGetShaderInfoLog(shader))
                GLES20.glDeleteShader(shader)
                return 0
            }
        }
        return shader
    }
}
