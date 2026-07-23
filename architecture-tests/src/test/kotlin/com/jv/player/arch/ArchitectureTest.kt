package com.jv.player.arch

import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * Enforces the clean-room + dependency-direction invariants from APP-583 plan
 * §0 and §2.3. These are structural guarantees, not runtime behavior, so they are
 * checked by scanning module build files and SDK sources:
 *
 *  1. :player-core NEVER depends on :player-ui.
 *  2. :player-api has NO Media3 and NO Compose dependency (stable, minimal contract).
 *  3. :player-api depends on NO other project module (it is the leaf contract).
 *  4. No SDK module source contains encryption/vault/cipher / crypto-key types.
 *
 * The test is intentionally implemented against the file tree (not reflection) so it
 * holds even while the modules are empty Wave 0 skeletons and catches a bad
 * dependency the moment it is declared in a build file.
 */
class ArchitectureTest {
    private val repoRoot: File = findRepoRoot()

    /** SDK modules that must stay encryption-free (the :demo app is scanned too). */
    private val scannedModules = listOf("player-api", "player-core", "player-ui", "demo")

    // --- §2.3 dependency direction -------------------------------------------------

    @Test
    fun `player-core does not depend on player-ui`() {
        val core = buildFileText("player-core")
        assertTrue(
            ":player-core must NOT depend on :player-ui (plan §2.3)",
            !core.contains("\":player-ui\""),
        )
    }

    @Test
    fun `player-api has no Media3 or Compose dependency`() {
        val api = buildFileText("player-api").lowercase()
        for (forbidden in listOf("media3", "compose")) {
            assertTrue(
                ":player-api must NOT depend on '$forbidden' (Media3/Compose-free contract, plan §2.2/§2.3)",
                !api.contains(forbidden),
            )
        }
    }

    @Test
    fun `player-api depends on no other project module`() {
        val api = buildFileText("player-api")
        val projectDeps = Regex("""project\(\s*":([a-z0-9-]+)"\s*\)""")
            .findAll(api)
            .map { it.groupValues[1] }
            .toList()
        assertTrue(
            ":player-api must be a leaf contract with no inter-module deps, found: $projectDeps",
            projectDeps.isEmpty(),
        )
    }

    // --- §0 no encryption ever enters the SDK -------------------------------------

    @Test
    fun `no SDK source references encryption, vault, cipher, or crypto-key types`() {
        val forbiddenWords = listOf("encrypt", "vault", "cipher")
        val forbiddenImports = listOf("javax.crypto", "java.security.key", "keystore", "secretkey")
        val offenders = mutableListOf<String>()

        for (module in scannedModules) {
            val src = File(repoRoot, "$module/src")
            if (!src.exists()) continue
            src.walkTopDown()
                .filter { it.isFile && it.extension in setOf("kt", "java") }
                .forEach { file ->
                    val lower = file.readText().lowercase()
                    for (word in forbiddenWords) {
                        if (Regex("\\b${Regex.escape(word)}").containsMatchIn(lower)) {
                            offenders += "${file.relativeToRepo()} contains forbidden word '$word'"
                        }
                    }
                    for (imp in forbiddenImports) {
                        if (lower.contains(imp)) {
                            offenders += "${file.relativeToRepo()} references crypto type '$imp'"
                        }
                    }
                }
        }

        if (offenders.isNotEmpty()) {
            fail(
                "Encryption must NEVER enter the SDK (plan §0). Offending files:\n  " +
                    offenders.joinToString("\n  "),
            )
        }
    }

    // --- helpers -------------------------------------------------------------------

    private fun buildFileText(module: String): String {
        val f = File(repoRoot, "$module/build.gradle.kts")
        assertTrue("Expected build file missing: ${f.relativeToRepo()}", f.exists())
        return f.readText()
    }

    private fun File.relativeToRepo(): String = relativeTo(repoRoot).path

    private fun findRepoRoot(): File {
        val start = System.getProperty("jvplayer.rootDir")?.let(::File)
            ?: File(System.getProperty("user.dir"))
        var dir: File? = start
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").exists()) return dir
            dir = dir.parentFile
        }
        error("Could not locate repo root (no settings.gradle.kts above $start)")
    }
}
