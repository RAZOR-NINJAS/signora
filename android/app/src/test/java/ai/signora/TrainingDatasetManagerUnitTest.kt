package ai.signora

import ai.signora.training.TrainingDatasetManager
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TrainingDatasetManagerUnitTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Test
    fun testSaveAndGetRecordedCounts() {
        val rootDir = tempFolder.newFolder("isl_test_samples")
        val manager = TrainingDatasetManager(baseDir = rootDir)

        assertTrue(manager.getRecordedCounts().isEmpty())
        assertEquals(0, manager.getTotalSampleCount())

        val sample1 = FloatArray(220) { it * 0.01f }
        val sample2 = FloatArray(220) { it * 0.02f }

        val count = manager.saveSamples("HELLO", listOf(sample1, sample2))
        assertEquals(2, count)

        val counts = manager.getRecordedCounts()
        assertEquals(1, counts.size)
        assertEquals(2, counts["HELLO"] ?: 0)
        assertEquals(2, manager.getTotalSampleCount())
    }

    @Test
    fun testAppendSamples() {
        val rootDir = tempFolder.newFolder("isl_test_append")
        val manager = TrainingDatasetManager(baseDir = rootDir)

        val batch1 = List(5) { FloatArray(220) { 0.1f } }
        val batch2 = List(3) { FloatArray(220) { 0.2f } }

        manager.saveSamples("THANK YOU", batch1)
        assertEquals(5, manager.getRecordedCounts()["THANK YOU"] ?: 0)

        val total = manager.saveSamples("THANK YOU", batch2)
        assertEquals(8, total)
        assertEquals(8, manager.getRecordedCounts()["THANK YOU"] ?: 0)
    }

    @Test
    fun testClearSignSamples() {
        val rootDir = tempFolder.newFolder("isl_test_clear")
        val manager = TrainingDatasetManager(baseDir = rootDir)

        val batch = List(10) { FloatArray(220) { 0.5f } }
        manager.saveSamples("GOODBYE", batch)
        manager.saveSamples("WELCOME", batch)

        assertEquals(2, manager.getRecordedCounts().size)

        val cleared = manager.clearSignSamples("GOODBYE")
        assertTrue(cleared)

        val updated = manager.getRecordedCounts()
        assertEquals(1, updated.size)
        assertFalse(updated.containsKey("GOODBYE"))
        assertEquals(10, updated["WELCOME"] ?: 0)
    }

    @Test
    fun testManifestRebuildFromFiles() {
        val rootDir = tempFolder.newFolder("isl_test_rebuild")
        val manager = TrainingDatasetManager(baseDir = rootDir)

        val batchA = List(4) { FloatArray(220) { 1.0f } }
        val batchB = List(6) { FloatArray(220) { 2.0f } }

        manager.saveSamples("A", batchA)
        manager.saveSamples("B", batchB)

        // Delete the manifest file to simulate missing manifest
        val manifestFile = File(rootDir, "manifest_counts.json")
        assertTrue(manifestFile.exists())
        manifestFile.delete()

        // Create a new manager instance pointing to the same folder: it should rebuild the manifest
        val newManager = TrainingDatasetManager(baseDir = rootDir)
        val counts = newManager.getRecordedCounts()

        assertEquals(4, counts["A"] ?: 0)
        assertEquals(6, counts["B"] ?: 0)
        assertEquals(10, newManager.getTotalSampleCount())
        assertTrue(File(rootDir, "manifest_counts.json").exists())
    }

    @Test
    fun testLoadAllSamples() {
        val rootDir = tempFolder.newFolder("isl_test_load_all")
        val manager = TrainingDatasetManager(baseDir = rootDir)

        val sample = FloatArray(220) { i -> i.toFloat() }
        manager.saveSamples("NAMASTE", listOf(sample))

        val loaded = manager.loadAllSamples()
        assertEquals(1, loaded.size)
        assertTrue(loaded.containsKey("NAMASTE"))
        val signSamples = loaded["NAMASTE"]!!
        assertEquals(1, signSamples.size)
        assertEquals(220, signSamples[0].size)
        assertEquals(10.0f, signSamples[0][10], 0.001f)
    }

    @Test
    fun testExportCompleteDataset() {
        val rootDir = tempFolder.newFolder("isl_test_export")
        val manager = TrainingDatasetManager(baseDir = rootDir)

        val s1 = FloatArray(220) { 0.1f }
        val s2 = FloatArray(220) { 0.2f }
        manager.saveSamples("YES", listOf(s1))
        manager.saveSamples("NO", listOf(s2))

        val exported = manager.exportCompleteDataset()
        assertNotNull(exported)
        assertTrue(exported!!.exists())
        val json = exported.readText()
        assertTrue(json.contains("\"total_classes\":2"))
        assertTrue(json.contains("\"total_samples\":2"))
        assertTrue(json.contains("\"YES\""))
        assertTrue(json.contains("\"NO\""))
    }
}
