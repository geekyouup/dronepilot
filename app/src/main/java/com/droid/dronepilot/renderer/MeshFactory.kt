package com.droid.dronepilot.renderer

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.cos
import kotlin.math.sin

/**
 * Encapsulates an OpenGL ES mesh with vertex positions, normals, and colors.
 */
class RenderMesh(
    val vertexBuffer: FloatBuffer,
    val normalBuffer: FloatBuffer,
    val colorBuffer: FloatBuffer,
    val vertexCount: Int,
    val drawMode: Int = GLES20.GL_TRIANGLES
) {
    fun draw(posHandle: Int, normalHandle: Int, colorHandle: Int) {
        vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glEnableVertexAttribArray(posHandle)

        normalBuffer.position(0)
        GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 0, normalBuffer)
        GLES20.glEnableVertexAttribArray(normalHandle)

        colorBuffer.position(0)
        GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 0, colorBuffer)
        GLES20.glEnableVertexAttribArray(colorHandle)

        GLES20.glDrawArrays(drawMode, 0, vertexCount)
    }
}

object MeshFactory {

    private fun createFloatBuffer(array: FloatArray): FloatBuffer {
        return ByteBuffer.allocateDirect(array.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(array)
                position(0)
            }
    }

    /**
     * Builds a large checkered ground terrain with runway markings and launch pad.
     */
    fun createGroundMesh(gridSize: Int = 40, cellSize: Float = 5.0f): RenderMesh {
        val half = gridSize / 2
        val totalQuads = gridSize * gridSize
        val vertices = FloatArray(totalQuads * 6 * 3)
        val normals = FloatArray(totalQuads * 6 * 3)
        val colors = FloatArray(totalQuads * 6 * 4)

        var vIdx = 0
        var nIdx = 0
        var cIdx = 0

        for (x in -half until half) {
            for (z in -half until half) {
                val x0 = x * cellSize
                val x1 = (x + 1) * cellSize
                val z0 = z * cellSize
                val z1 = (z + 1) * cellSize

                val isEven = (x + z) % 2 == 0
                // Color palette: tarmac runway down the middle, grass checkered elsewhere
                val isRunway = x in -1..0 && z in -20..20
                val isLaunchPad = x in -1..0 && z in -1..0

                val r: Float
                val g: Float
                val b: Float
                if (isLaunchPad) {
                    r = 0.95f; g = 0.55f; b = 0.1f // orange launch pad
                } else if (isRunway) {
                    if (isEven) {
                        r = 0.22f; g = 0.24f; b = 0.26f // dark asphalt
                    } else {
                        r = 0.28f; g = 0.30f; b = 0.33f // lighter asphalt
                    }
                } else {
                    if (isEven) {
                        r = 0.18f; g = 0.35f; b = 0.18f // dark grass
                    } else {
                        r = 0.22f; g = 0.42f; b = 0.22f // light grass
                    }
                }

                // Two triangles forming quad: (x0,z0), (x0,z1), (x1,z0), (x1,z0), (x0,z1), (x1,z1)
                val quadVerts = floatArrayOf(
                    x0, 0f, z0,
                    x0, 0f, z1,
                    x1, 0f, z0,
                    x1, 0f, z0,
                    x0, 0f, z1,
                    x1, 0f, z1
                )

                for (i in 0 until 6) {
                    vertices[vIdx++] = quadVerts[i * 3]
                    vertices[vIdx++] = quadVerts[i * 3 + 1]
                    vertices[vIdx++] = quadVerts[i * 3 + 2]

                    normals[nIdx++] = 0f
                    normals[nIdx++] = 1f
                    normals[nIdx++] = 0f

                    colors[cIdx++] = r
                    colors[cIdx++] = g
                    colors[cIdx++] = b
                    colors[cIdx++] = 1f
                }
            }
        }

        return RenderMesh(
            createFloatBuffer(vertices),
            createFloatBuffer(normals),
            createFloatBuffer(colors),
            totalQuads * 6
        )
    }

    /**
     * Builds 3D Quadcopter Frame (Central carbon pod, 4 tubular arms, 4 motor mounts).
     */
    fun createDroneFrameMesh(): RenderMesh {
        val vList = mutableListOf<Float>()
        val nList = mutableListOf<Float>()
        val cList = mutableListOf<Float>()

        fun addBox(
            cx: Float, cy: Float, cz: Float,
            sx: Float, sy: Float, sz: Float,
            r: Float, g: Float, b: Float
        ) {
            val x0 = cx - sx * 0.5f; val x1 = cx + sx * 0.5f
            val y0 = cy - sy * 0.5f; val y1 = cy + sy * 0.5f
            val z0 = cz - sz * 0.5f; val z1 = cz + sz * 0.5f

            val faces = arrayOf(
                // Front (+Z)
                floatArrayOf(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y0, z1, x1, y1, z1, x0, y1, z1),
                // Back (-Z)
                floatArrayOf(x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y0, z0, x0, y1, z0, x1, y1, z0),
                // Top (+Y)
                floatArrayOf(x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z1, x1, y1, z0, x0, y1, z0),
                // Bottom (-Y)
                floatArrayOf(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z0, x1, y0, z1, x0, y0, z1),
                // Right (+X)
                floatArrayOf(x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y0, z1, x1, y1, z0, x1, y1, z1),
                // Left (-X)
                floatArrayOf(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y0, z0, x0, y1, z1, x0, y1, z0)
            )

            val faceNormals = arrayOf(
                floatArrayOf(0f, 0f, 1f),
                floatArrayOf(0f, 0f, -1f),
                floatArrayOf(0f, 1f, 0f),
                floatArrayOf(0f, -1f, 0f),
                floatArrayOf(1f, 0f, 0f),
                floatArrayOf(-1f, 0f, 0f)
            )

            for (f in faces.indices) {
                val fVerts = faces[f]
                val fn = faceNormals[f]
                for (v in 0 until 6) {
                    vList.add(fVerts[v * 3])
                    vList.add(fVerts[v * 3 + 1])
                    vList.add(fVerts[v * 3 + 2])

                    nList.add(fn[0])
                    nList.add(fn[1])
                    nList.add(fn[2])

                    cList.add(r); cList.add(g); cList.add(b); cList.add(1f)
                }
            }
        }

        // 1. Central Canopy / Body (Dark carbon/graphite)
        addBox(0f, 0.03f, 0f, 0.08f, 0.04f, 0.16f, 0.15f, 0.16f, 0.18f)

        // 2. FPV Camera at the nose (Orange/Red lens housing)
        addBox(0f, 0.045f, 0.08f, 0.04f, 0.035f, 0.04f, 0.95f, 0.2f, 0.1f)

        // 3. Four Arms extending to motors
        val armOffset = 0.106f
        // Diagonal arms
        addBox(armOffset * 0.5f, 0.02f, armOffset * 0.5f, 0.02f, 0.012f, 0.15f, 0.1f, 0.1f, 0.1f)
        addBox(-armOffset * 0.5f, 0.02f, armOffset * 0.5f, 0.02f, 0.012f, 0.15f, 0.1f, 0.1f, 0.1f)
        addBox(armOffset * 0.5f, 0.02f, -armOffset * 0.5f, 0.02f, 0.012f, 0.15f, 0.1f, 0.1f, 0.1f)
        addBox(-armOffset * 0.5f, 0.02f, -armOffset * 0.5f, 0.02f, 0.012f, 0.15f, 0.1f, 0.1f, 0.1f)

        // 4. Four Motor Pods
        val d = armOffset
        // Front-Left
        addBox(d, 0.025f, d, 0.032f, 0.03f, 0.032f, 0.0f, 0.85f, 0.95f) // Cyan bell
        // Front-Right
        addBox(-d, 0.025f, d, 0.032f, 0.03f, 0.032f, 0.0f, 0.85f, 0.95f)
        // Rear-Left
        addBox(d, 0.025f, -d, 0.032f, 0.03f, 0.032f, 0.25f, 0.25f, 0.25f)
        // Rear-Right
        addBox(-d, 0.025f, -d, 0.032f, 0.03f, 0.032f, 0.25f, 0.25f, 0.25f)

        // 5. LED Orientation Lights: Green in front, Red in rear!
        addBox(d, 0.01f, d + 0.016f, 0.015f, 0.01f, 0.005f, 0.0f, 1.0f, 0.2f) // FL Green
        addBox(-d, 0.01f, d + 0.016f, 0.015f, 0.01f, 0.005f, 0.0f, 1.0f, 0.2f) // FR Green
        addBox(d, 0.01f, -d - 0.016f, 0.015f, 0.01f, 0.005f, 1.0f, 0.1f, 0.1f) // RL Red
        addBox(-d, 0.01f, -d - 0.016f, 0.015f, 0.01f, 0.005f, 1.0f, 0.1f, 0.1f) // RR Red

        return RenderMesh(
            createFloatBuffer(vList.toFloatArray()),
            createFloatBuffer(nList.toFloatArray()),
            createFloatBuffer(cList.toFloatArray()),
            vList.size / 3
        )
    }

    /**
     * Builds a single 3-blade propeller disc mesh of radius r.
     */
    fun createPropellerMesh(radius: Float = 0.065f): RenderMesh {
        val vList = mutableListOf<Float>()
        val nList = mutableListOf<Float>()
        val cList = mutableListOf<Float>()

        val blades = 3
        for (b in 0 until blades) {
            val angle = (b * 2.0 * Math.PI / blades).toFloat()
            val cosA = cos(angle)
            val sinA = sin(angle)
            val cosB = cos(angle + 0.15f)
            val sinB = sin(angle + 0.15f)

            // Triangle blade
            vList.add(0f); vList.add(0.005f); vList.add(0f)
            vList.add(cosA * radius); vList.add(0.005f); vList.add(sinA * radius)
            vList.add(cosB * (radius * 0.9f)); vList.add(0.005f); vList.add(sinB * (radius * 0.9f))

            for (i in 0 until 3) {
                nList.add(0f); nList.add(1f); nList.add(0f)
                cList.add(0.85f); cList.add(0.85f); cList.add(0.9f); cList.add(0.85f)
            }
        }

        return RenderMesh(
            createFloatBuffer(vList.toFloatArray()),
            createFloatBuffer(nList.toFloatArray()),
            createFloatBuffer(cList.toFloatArray()),
            vList.size / 3
        )
    }

    /**
     * Builds a 3D FPV Racing Gate Arch (Left column, right column, top lintel).
     */
    fun createGateMesh(width: Float = 3.5f, height: Float = 3.0f, thick: Float = 0.25f): RenderMesh {
        val vList = mutableListOf<Float>()
        val nList = mutableListOf<Float>()
        val cList = mutableListOf<Float>()

        fun addBox(
            cx: Float, cy: Float, cz: Float,
            sx: Float, sy: Float, sz: Float,
            r: Float, g: Float, b: Float
        ) {
            val x0 = cx - sx * 0.5f; val x1 = cx + sx * 0.5f
            val y0 = cy - sy * 0.5f; val y1 = cy + sy * 0.5f
            val z0 = cz - sz * 0.5f; val z1 = cz + sz * 0.5f

            val faces = arrayOf(
                floatArrayOf(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y0, z1, x1, y1, z1, x0, y1, z1),
                floatArrayOf(x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y0, z0, x0, y1, z0, x1, y1, z0),
                floatArrayOf(x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z1, x1, y1, z0, x0, y1, z0),
                floatArrayOf(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z0, x1, y0, z1, x0, y0, z1),
                floatArrayOf(x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y0, z1, x1, y1, z0, x1, y1, z1),
                floatArrayOf(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y0, z0, x0, y1, z1, x0, y1, z0)
            )

            val faceNormals = arrayOf(
                floatArrayOf(0f, 0f, 1f),
                floatArrayOf(0f, 0f, -1f),
                floatArrayOf(0f, 1f, 0f),
                floatArrayOf(0f, -1f, 0f),
                floatArrayOf(1f, 0f, 0f),
                floatArrayOf(-1f, 0f, 0f)
            )

            for (f in faces.indices) {
                val fVerts = faces[f]
                val fn = faceNormals[f]
                for (v in 0 until 6) {
                    vList.add(fVerts[v * 3])
                    vList.add(fVerts[v * 3 + 1])
                    vList.add(fVerts[v * 3 + 2])

                    nList.add(fn[0]); nList.add(fn[1]); nList.add(fn[2])
                    cList.add(r); cList.add(g); cList.add(b); cList.add(1f)
                }
            }
        }

        // Left Pillar
        addBox(-width * 0.5f, height * 0.5f, 0f, thick, height, thick, 1.0f, 0.55f, 0.0f)
        // Right Pillar
        addBox(width * 0.5f, height * 0.5f, 0f, thick, height, thick, 1.0f, 0.55f, 0.0f)
        // Top Arch
        addBox(0f, height, 0f, width + thick, thick, thick, 0.0f, 0.9f, 1.0f)

        return RenderMesh(
            createFloatBuffer(vList.toFloatArray()),
            createFloatBuffer(nList.toFloatArray()),
            createFloatBuffer(cList.toFloatArray()),
            vList.size / 3
        )
    }
}
