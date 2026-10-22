package ai.signora

import ai.signora.ml.SignoraMLP
import org.junit.Assert.*
import org.junit.Test

class SignoraMLPUnitTest {

    @Test
    fun testSignoraMLPForwardAndPredict() {
        val inDim = 74
        val hidden1 = 16
        val hidden2 = 8
        val numClasses = 3
        val labels = listOf("A", "B", "C")

        val w0 = Array(hidden1) { FloatArray(inDim) { 0.01f } }
        val b0 = FloatArray(hidden1) { 0.0f }
        val lnW0 = FloatArray(hidden1) { 1.0f }
        val lnB0 = FloatArray(hidden1) { 0.0f }

        val w4 = Array(hidden2) { FloatArray(hidden1) { 0.01f } }
        val b4 = FloatArray(hidden2) { 0.0f }
        val lnW4 = FloatArray(hidden2) { 1.0f }
        val lnB4 = FloatArray(hidden2) { 0.0f }

        // Make class 1 ("B") have higher weights to test prediction
        val w8 = Array(numClasses) { c -> FloatArray(hidden2) { if (c == 1) 0.5f else 0.05f } }
        val b8 = FloatArray(numClasses) { if (it == 1) 1.0f else 0.0f }

        val mean = FloatArray(inDim) { 0.0f }
        val std = FloatArray(inDim) { 1.0f }

        val mlp = SignoraMLP(
            mode = "ASL",
            inDim = inDim,
            numClasses = numClasses,
            labels = labels,
            mean = mean,
            std = std,
            temperature = 1.0f,
            w0 = w0,
            b0 = b0,
            lnW0 = lnW0,
            lnB0 = lnB0,
            w4 = w4,
            b4 = b4,
            lnW4 = lnW4,
            lnB4 = lnB4,
            w8 = w8,
            b8 = b8
        )

        val input = FloatArray(inDim) { 1.0f }
        val probs = mlp.forward(input)
        assertEquals(numClasses, probs.size)

        // Sum of probabilities must equal 1.0
        val sumProb = probs.sum()
        assertEquals(1.0f, sumProb, 1e-4f)

        // Predict
        val result = mlp.predict(input)
        assertEquals("B", result.label)
        assertTrue(result.confidence > 0.5f)
        assertTrue(result.margin > 0.0f)
        assertEquals(3, result.top3.size)
        assertEquals("B", result.top3[0].first)
    }

    @Test
    fun testSignoraMLPPartialAndEmptyInputSafety() {
        val inDim = 74
        val hidden1 = 4
        val hidden2 = 4
        val numClasses = 2
        val labels = listOf("YES", "NO")

        val mlp = SignoraMLP(
            mode = "ISL",
            inDim = inDim,
            numClasses = numClasses,
            labels = labels,
            mean = FloatArray(inDim),
            std = FloatArray(inDim) { 1.0f },
            temperature = 1.0f,
            w0 = Array(hidden1) { FloatArray(inDim) },
            b0 = FloatArray(hidden1),
            lnW0 = FloatArray(hidden1) { 1.0f },
            lnB0 = FloatArray(hidden1),
            w4 = Array(hidden2) { FloatArray(hidden1) },
            b4 = FloatArray(hidden2),
            lnW4 = FloatArray(hidden2) { 1.0f },
            lnB4 = FloatArray(hidden2),
            w8 = Array(numClasses) { FloatArray(hidden2) },
            b8 = FloatArray(numClasses)
        )

        // Empty input passed: should not crash, effectiveInDim handles it gracefully
        val res = mlp.predict(FloatArray(0))
        assertNotNull(res)
        assertEquals(2, res.top3.size)
    }

    @Test
    fun testSignoraMLPZeroClassesSafety() {
        val inDim = 16
        val hidden1 = 4
        val hidden2 = 4
        val numClasses = 0
        val labels = emptyList<String>()

        val mlp = SignoraMLP(
            mode = "ISL",
            inDim = inDim,
            numClasses = numClasses,
            labels = labels,
            mean = FloatArray(inDim),
            std = FloatArray(inDim) { 1.0f },
            temperature = 1.0f,
            w0 = Array(hidden1) { FloatArray(inDim) },
            b0 = FloatArray(hidden1),
            lnW0 = FloatArray(hidden1) { 1.0f },
            lnB0 = FloatArray(hidden1),
            w4 = Array(hidden2) { FloatArray(hidden1) },
            b4 = FloatArray(hidden2),
            lnW4 = FloatArray(hidden2) { 1.0f },
            lnB4 = FloatArray(hidden2),
            w8 = Array(numClasses) { FloatArray(hidden2) },
            b8 = FloatArray(numClasses)
        )

        val probs = mlp.forward(FloatArray(inDim))
        assertEquals(0, probs.size)

        val res = mlp.predict(FloatArray(inDim))
        assertNotNull(res)
        assertEquals("UNKNOWN", res.label)
        assertEquals(0f, res.confidence, 1e-5f)
        assertEquals(0, res.top3.size)
    }
}
