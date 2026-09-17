package com.droid.dronepilot

import com.droid.dronepilot.physics.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Vector3Test {

    @Test
    fun testVectorArithmetic() {
        val a = Vector3(1f, 2f, 3f)
        val b = Vector3(4f, 5f, 6f)

        val sum = a + b
        assertEquals(5f, sum.x, 1e-4f)
        assertEquals(7f, sum.y, 1e-4f)
        assertEquals(9f, sum.z, 1e-4f)

        val diff = b - a
        assertEquals(3f, diff.x, 1e-4f)
        assertEquals(3f, diff.y, 1e-4f)
        assertEquals(3f, diff.z, 1e-4f)

        val scaled = a * 2f
        assertEquals(2f, scaled.x, 1e-4f)
        assertEquals(4f, scaled.y, 1e-4f)
        assertEquals(6f, scaled.z, 1e-4f)
    }

    @Test
    fun testDotAndCrossProduct() {
        val x = Vector3(1f, 0f, 0f)
        val y = Vector3(0f, 1f, 0f)

        assertEquals(0f, x.dot(y), 1e-4f)

        val z = x.cross(y)
        assertEquals(0f, z.x, 1e-4f)
        assertEquals(0f, z.y, 1e-4f)
        assertEquals(1f, z.z, 1e-4f)
    }

    @Test
    fun testLengthAndNormalize() {
        val v = Vector3(0f, 3f, 4f)
        assertEquals(5f, v.length(), 1e-4f)

        val norm = v.normalized()
        assertEquals(1f, norm.length(), 1e-4f)
        assertEquals(0f, norm.x, 1e-4f)
        assertEquals(0.6f, norm.y, 1e-4f)
        assertEquals(0.8f, norm.z, 1e-4f)
    }
}
