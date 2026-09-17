package com.droid.dronepilot.renderer

import android.opengl.GLES20
import com.droid.dronepilot.physics.Vector3
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
     * Builds a 3D FPV Racing Gate Arch (Left column, right column, top lintel) with high-visibility racing stripes.
     */
    fun createGateMesh(width: Float = 6.5f, height: Float = 5.0f, thick: Float = 0.35f): RenderMesh {
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

        // Heavy Base Pads on ground
        val basePadSize = thick * 2.2f
        addBox(-width * 0.5f, 0.08f, 0f, basePadSize, 0.16f, basePadSize, 0.25f, 0.28f, 0.32f)
        addBox(width * 0.5f, 0.08f, 0f, basePadSize, 0.16f, basePadSize, 0.25f, 0.28f, 0.32f)

        // Striped Pillars (5 alternating segments of high-vis racing orange & white)
        val segments = 5
        val segHeight = height / segments
        for (s in 0 until segments) {
            val segCenterY = (s + 0.5f) * segHeight
            val isOrange = (s % 2 == 0)
            val r = if (isOrange) 1.0f else 0.95f
            val g = if (isOrange) 0.45f else 0.95f
            val b = if (isOrange) 0.0f else 0.98f

            // Left pillar segment
            addBox(-width * 0.5f, segCenterY, 0f, thick, segHeight, thick, r, g, b)
            // Right pillar segment
            addBox(width * 0.5f, segCenterY, 0f, thick, segHeight, thick, r, g, b)
        }

        // Top Arch Lintel (electric cyan)
        addBox(0f, height, 0f, width + thick, thick, thick, 0.0f, 0.92f, 1.0f)

        // High-vis Neon Yellow Corner Markers for easy aperture target acquisition
        val cornerSize = thick * 1.25f
        addBox(-width * 0.5f, height, 0f, cornerSize, cornerSize, cornerSize, 1.0f, 0.92f, 0.0f)
        addBox(width * 0.5f, height, 0f, cornerSize, cornerSize, cornerSize, 1.0f, 0.92f, 0.0f)

        return RenderMesh(
            createFloatBuffer(vList.toFloatArray()),
            createFloatBuffer(nList.toFloatArray()),
            createFloatBuffer(cList.toFloatArray()),
            vList.size / 3
        )
    }

    private data class CloudTri(val a: Vector3, val b: Vector3, val c: Vector3)

    /**
     * Builds a rich 3D stylized low-poly volumetric sky filled with multi-tiered clouds.
     * Uses flat-shaded subdivided octaspheres with mathematically proven outward CCW winding,
     * completely eliminating pole singularities, inverted triangles, and Z-fighting artifacts.
     */
    fun createCloudClusterMesh(): RenderMesh {
        val vList = mutableListOf<Float>()
        val nList = mutableListOf<Float>()
        val cList = mutableListOf<Float>()

        // 8 base triangles of an octahedron (strictly outward CCW winding)
        val top = Vector3(0f, 1f, 0f)
        val bot = Vector3(0f, -1f, 0f)
        val px = Vector3(1f, 0f, 0f)
        val nx = Vector3(-1f, 0f, 0f)
        val pz = Vector3(0f, 0f, 1f)
        val nz = Vector3(0f, 0f, -1f)

        val baseTris = listOf(
            // Top 4 triangles (apex to base)
            CloudTri(top, pz, px),
            CloudTri(top, nx, pz),
            CloudTri(top, nz, nx),
            CloudTri(top, px, nz),
            // Bottom 4 triangles (base to bottom)
            CloudTri(bot, px, pz),
            CloudTri(bot, pz, nx),
            CloudTri(bot, nx, nz),
            CloudTri(bot, nz, px)
        )

        // Subdivide 1 time: 8 -> 32 outward-facing equilateral facets
        val subTris = mutableListOf<CloudTri>()
        for (tri in baseTris) {
            val ab = (tri.a + tri.b).normalized()
            val bc = (tri.b + tri.c).normalized()
            val ca = (tri.c + tri.a).normalized()
            subTris.add(CloudTri(tri.a, ab, ca))
            subTris.add(CloudTri(ab, tri.b, bc))
            subTris.add(CloudTri(ca, bc, tri.c))
            subTris.add(CloudTri(ab, bc, ca))
        }

        fun addPuff(
            cx: Float, cy: Float, cz: Float,
            rx: Float, ry: Float, rz: Float,
            flattenBottom: Boolean = true
        ) {
            for (tri in subTris) {
                fun transform(u: Vector3): Vector3 {
                    val yMult = if (flattenBottom && u.y < 0f) 0.45f else 1.0f
                    return Vector3(
                        cx + rx * u.x,
                        cy + ry * (u.y * yMult),
                        cz + rz * u.z
                    )
                }

                val p1 = transform(tri.a)
                val p2 = transform(tri.b)
                val p3 = transform(tri.c)

                // Compute exact outward flat face normal
                val e1 = p2 - p1
                val e2 = p3 - p1
                var fn = e1.cross(e2)
                val len = fn.length()
                fn = if (len > 1e-6f) fn / len else Vector3.UP

                // Shading palette based on facet angle
                val r: Float
                val g: Float
                val b: Float
                if (fn.y > 0.30f) {
                    // Sunlit tops: brilliant crisp cloud white
                    r = 0.99f; g = 0.99f; b = 1.0f
                } else if (fn.y >= -0.15f) {
                    // Soft ambient sides
                    r = 0.93f; g = 0.95f; b = 0.98f
                } else {
                    // Shaded cloud underside with soft sky ambient tone
                    r = 0.74f; g = 0.79f; b = 0.86f
                }

                // Add 3 vertices with flat face normal
                val pts = arrayOf(p1, p2, p3)
                for (pt in pts) {
                    vList.add(pt.x); vList.add(pt.y); vList.add(pt.z)
                    nList.add(fn.x); nList.add(fn.y); nList.add(fn.z)
                    cList.add(r); cList.add(g); cList.add(b); cList.add(1f)
                }
            }
        }

        // Puffy Cumulus Formation with flat bottom base and towering domes
        fun addCumulus(ox: Float, oy: Float, oz: Float, scale: Float) {
            val s = scale
            // Wide flat base
            addPuff(ox, oy, oz, 11.0f * s, 3.8f * s, 9.0f * s, flattenBottom = true)
            // Left flank
            addPuff(ox - 6.5f * s, oy + 0.5f * s, oz + 1.5f * s, 7.5f * s, 4.2f * s, 6.5f * s, flattenBottom = true)
            // Right flank
            addPuff(ox + 7.0f * s, oy + 0.3f * s, oz - 1.5f * s, 8.0f * s, 4.4f * s, 7.0f * s, flattenBottom = true)
            // Tall central turret dome (spherical)
            addPuff(ox + 0.5f * s, oy + 2.8f * s, oz - 0.5f * s, 6.5f * s, 5.0f * s, 6.0f * s, flattenBottom = false)
            // Forward lobe
            addPuff(ox - 1.8f * s, oy + 1.0f * s, oz + 5.0f * s, 5.8f * s, 3.8f * s, 5.2f * s, flattenBottom = true)
            // Rear lobe
            addPuff(ox + 2.5f * s, oy + 0.8f * s, oz - 4.8f * s, 6.0f * s, 4.0f * s, 5.5f * s, flattenBottom = true)
        }

        // Horizontal Streak Cloud for atmospheric variation
        fun addStreak(ox: Float, oy: Float, oz: Float, length: Float, scale: Float, angleDeg: Float) {
            val rad = Math.toRadians(angleDeg.toDouble()).toFloat()
            val dx = kotlin.math.cos(rad)
            val dz = kotlin.math.sin(rad)
            val s = scale
            val steps = 4
            for (step in 0 until steps) {
                val t = (step - (steps - 1) * 0.5f) * (length / steps)
                val ptX = ox + dx * t
                val ptZ = oz + dz * t
                val py = oy + kotlin.math.sin(step.toFloat() * 1.5f) * 1.2f * s
                val puffRad = (5.5f + (step % 2) * 1.5f) * s
                addPuff(ptX, py, ptZ, puffRad, 3.0f * s, puffRad * 0.85f, flattenBottom = true)
            }
        }

        // 1. Zenith Overhead Clouds (Immediately visible when looking straight up at 90°)
        addCumulus(0f, 50f, 0f, 1.35f)          // Grand Central Zenith Formation
        addCumulus(24f, 54f, 22f, 1.15f)
        addCumulus(-24f, 52f, -20f, 1.1f)
        addStreak(0f, 55f, -15f, 28f, 1.0f, 40f)

        // 2. Inner Track Ceiling (Y = 32..38m, perfectly framing the race course)
        addCumulus(0f, 32f, 32f, 1.1f)          // North gate approach
        addCumulus(35f, 36f, 48f, 1.2f)         // Gate 2/3 high turn
        addCumulus(55f, 34f, 18f, 1.15f)        // East straight
        addCumulus(38f, 37f, -20f, 1.1f)        // South-East turn
        addCumulus(-15f, 32f, -28f, 1.0f)       // South back-straight
        addCumulus(-32f, 35f, 14f, 1.05f)       // West final turn

        // 3. Mid-Field Horizon Ring (Y = 36..42m, radius 60..85m)
        addCumulus(0f, 38f, 85f, 1.45f)         // North
        addStreak(65f, 41f, 65f, 35f, 1.3f, -30f) // North-East
        addCumulus(88f, 37f, 0f, 1.4f)          // East
        addStreak(60f, 40f, -65f, 32f, 1.25f, 20f)// South-East
        addCumulus(0f, 36f, -85f, 1.45f)        // South
        addStreak(-65f, 38f, -60f, 30f, 1.25f, -45f)// South-West
        addCumulus(-85f, 37f, 0f, 1.4f)         // West
        addStreak(-60f, 40f, 60f, 32f, 1.3f, 35f) // North-West

        // 4. Far Perimeter Atmosphere (Y = 40..46m, radius 120..150m)
        addCumulus(0f, 42f, 135f, 1.8f)
        addCumulus(130f, 44f, 0f, 1.8f)
        addCumulus(0f, 40f, -135f, 1.8f)
        addCumulus(-130f, 43f, 0f, 1.8f)

        return RenderMesh(
            createFloatBuffer(vList.toFloatArray()),
            createFloatBuffer(nList.toFloatArray()),
            createFloatBuffer(cList.toFloatArray()),
            vList.size / 3
        )
    }
}
