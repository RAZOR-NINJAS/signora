package ai.signora
 
import ai.signora.recovery.AutoAssetManager
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
 
class AutoAssetManagerUnitTest {
 
    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()
 
    private fun createDummyModelJson(
        numClasses: Int,
        inDim: Int,
        labels: List<String>,
        includeWeights: Boolean = true
    ): String {
        return JSONObject().apply {
            put("format", "signora_mlp_v1")
            put("mode", "isl")
            put("num_classes", numClasses)
            put("in_dim", inDim)
            val lArr = JSONArray()
            labels.forEach { lArr.put(it) }
            put("labels", lArr)
 
            val mArr = JSONArray()
            for (i in 0 until inDim) mArr.put(0.0)
            put("mean", mArr)
 
            val sArr = JSONArray()
            for (i in 0 until inDim) sArr.put(1.0)
            put("std", sArr)
 
            if (includeWeights) {
                val wArr = JSONArray()
                val row = JSONArray()
                for (i in 0 until inDim) row.put(0.01)
                wArr.put(row)
                put("net_0_weight", wArr)
                put("net_0_bias", JSONArray().put(0.0))
            }
        }.toString()
    }
 
    @Test
    fun testShadowedModelWithFewerClassesIsOverwrittenByBundled() {
        val rootDir = tempFolder.newFolder("models_test")
        val bundled33Classes = (1..33).map { "SIGN_$it" }
        val bundledJson = createDummyModelJson(33, 220, bundled33Classes)
 
        val cached2Classes = listOf("HELLO", "NAMASTE")
        val cachedJson = createDummyModelJson(2, 220, cached2Classes)
 
        val cachedFile = File(rootDir, "isl_model.json")
        cachedFile.writeText(cachedJson, Charsets.UTF_8)
        assertEquals(2, JSONObject(cachedFile.readText(Charsets.UTF_8)).getInt("num_classes"))
 
        val manager = AutoAssetManager(
            baseDir = rootDir,
            assetLoader = { filename ->
                if (filename == "isl_model.json") bundledJson else ""
            }
        )
 
        val loaded = manager.loadModelJsonString("isl_model.json")
        val loadedObj = JSONObject(loaded)
        assertEquals(33, loadedObj.getInt("num_classes"))
        assertEquals(220, loadedObj.getInt("in_dim"))
 
        // Cached file on disk must now be updated to bundled 33-class model
        val updatedFileObj = JSONObject(cachedFile.readText(Charsets.UTF_8))
        assertEquals(33, updatedFileObj.getInt("num_classes"))
    }
 
    @Test
    fun testCorruptedCachedModelIsOverwrittenByBundled() {
        val rootDir = tempFolder.newFolder("models_corrupt_test")
        val bundled33Classes = (1..33).map { "SIGN_$it" }
        val bundledJson = createDummyModelJson(33, 220, bundled33Classes)
 
        val cachedFile = File(rootDir, "isl_model.json")
        cachedFile.writeText("{ corrupted_incomplete_json...", Charsets.UTF_8)
 
        val manager = AutoAssetManager(
            baseDir = rootDir,
            assetLoader = { filename ->
                if (filename == "isl_model.json") bundledJson else ""
            }
        )
 
        val loaded = manager.loadModelJsonString("isl_model.json")
        val loadedObj = JSONObject(loaded)
        assertEquals(33, loadedObj.getInt("num_classes"))
    }
 
    @Test
    fun testMissingWeightsCachedModelIsOverwrittenByBundled() {
        val rootDir = tempFolder.newFolder("models_noweights_test")
        val bundled33Classes = (1..33).map { "SIGN_$it" }
        val bundledJson = createDummyModelJson(33, 220, bundled33Classes)
 
        // Cached model has 33 classes but missing weights
        val cachedInvalidJson = createDummyModelJson(33, 220, bundled33Classes, includeWeights = false)
        val cachedFile = File(rootDir, "isl_model.json")
        cachedFile.writeText(cachedInvalidJson, Charsets.UTF_8)
 
        val manager = AutoAssetManager(
            baseDir = rootDir,
            assetLoader = { filename ->
                if (filename == "isl_model.json") bundledJson else ""
            }
        )
 
        val loaded = manager.loadModelJsonString("isl_model.json")
        val loadedObj = JSONObject(loaded)
        assertTrue(loadedObj.has("net_0_weight"))
    }
 
    @Test
    fun testForceBundledLoadsAndOverwritesCached() {
        val rootDir = tempFolder.newFolder("models_force_test")
        val bundled33Classes = (1..33).map { "SIGN_$it" }
        val bundledJson = createDummyModelJson(33, 220, bundled33Classes)
 
        val cached40Classes = (1..40).map { "CUSTOM_$it" }
        val cachedJson = createDummyModelJson(40, 220, cached40Classes)
        val cachedFile = File(rootDir, "isl_model.json")
        cachedFile.writeText(cachedJson, Charsets.UTF_8)
 
        val manager = AutoAssetManager(
            baseDir = rootDir,
            assetLoader = { filename ->
                if (filename == "isl_model.json") bundledJson else ""
            }
        )
 
        // Normal load preserves richer 40 classes
        val normalLoaded = manager.loadModelJsonString("isl_model.json", forceBundled = false)
        assertEquals(40, JSONObject(normalLoaded).getInt("num_classes"))
 
        // Force bundled overwrites with bundled 33 classes
        val forceLoaded = manager.loadModelJsonString("isl_model.json", forceBundled = true)
        assertEquals(33, JSONObject(forceLoaded).getInt("num_classes"))
        assertEquals(33, JSONObject(cachedFile.readText(Charsets.UTF_8)).getInt("num_classes"))
    }
 
    @Test
    fun testEnsureModelAssetsOverwritesShadowedModel() = runBlocking {
        val rootDir = tempFolder.newFolder("models_ensure_test")
        val bundledJson = createDummyModelJson(33, 220, (1..33).map { "SIGN_$it" })
 
        val cachedFile = File(rootDir, "isl_model.json")
        cachedFile.writeText(createDummyModelJson(2, 220, listOf("HELLO", "NAMASTE")), Charsets.UTF_8)
 
        val manager = AutoAssetManager(
            baseDir = rootDir,
            assetLoader = { filename ->
                when (filename) {
                    "isl_model.json" -> bundledJson
                    "asl_model.json" -> createDummyModelJson(26, 74, (1..26).map { "L_$it" })
                    else -> "mock_task_bytes"
                }
            }
        )
 
        val ok = manager.ensureModelAssets {}
        assertTrue(ok)
        val obj = JSONObject(cachedFile.readText(Charsets.UTF_8))
        assertEquals(33, obj.getInt("num_classes"))
    }
}
