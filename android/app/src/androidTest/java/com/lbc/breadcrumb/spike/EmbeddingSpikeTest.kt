package com.lbc.breadcrumb.spike

import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.EmbeddingEngine
import com.google.ai.edge.litertlm.EmbeddingEngineConfig
import com.google.ai.edge.litertlm.EmbeddingOptions
import com.google.ai.edge.litertlm.InputData
import com.lbc.breadcrumb.BreadcrumbApp
import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.MemoryType
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Spike, not for main: can the phone run EmbeddingGemma 2, how fast, and does it
 * find the right memory from a vague description? Reads the real memories and
 * their pictures, writes nothing. The model is pushed by hand to the app's
 * external files directory. Results go to logcat under "EmbeddingSpike".
 */
@RunWith(AndroidJUnit4::class)
class EmbeddingSpikeTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val app = context.applicationContext as BreadcrumbApp
    private val model = File(context.getExternalFilesDir(null), "embeddinggemma-2-text-vision-440m.litertlm")

    private fun log(line: String) = Log.i("EmbeddingSpike", line)

    private fun rssMb(): Long = File("/proc/self/status").readLines()
        .first { it.startsWith("VmRSS") }.filter { it.isDigit() }.toLong() / 1024

    private class Doc(val id: String, val label: String, val text: String, val picture: File? = null)

    private val distractors = listOf(
        "Paid ₹450 to Swiggy via UPI. Transaction ID 4123 9988. Order from Arsalan Biryani",
        "Your train ticket: Howrah to New Delhi, Rajdhani Express 12301, coach B4 seat 32, 14 Nov",
        "Recipe: paneer butter masala. Butter, tomato puree, cream, kasuri methi, garam masala",
        "Google STEP internship 2027 applications open for first and second year students",
        "Microsoft Engage mentorship program, apply by March 15",
        "Operating systems lecture 7: process scheduling, round robin, shortest job first",
        "DBMS assignment 3: normalisation, 3NF and BCNF, due Friday",
        "Google I/O Extended Kolkata, Saturday at Techno India, register on the meetup page",
        "Mainland China in Salt Lake has great dim sum, Rahul says try the prawn har gow",
        "Lyrics: Here comes the sun, doo-doo-doo-doo, and I say it's all right",
        "WhatsApp forward: drinking warm water every morning cures everything",
        "Electricity bill for October: ₹2,340, due on 25 Oct, CESC consumer number 1234",
        "Flight confirmation: IndiGo 6E 512, Kolkata to Bengaluru, 3 Dec 06:15",
        "Notes from the standup: backend auth is blocked on the token refresh bug",
        "Book to read: Thinking, Fast and Slow by Daniel Kahneman",
        "Tweet: the best way to learn Rust is to rewrite something you already know",
        "Gym plan: push day bench press 4x8, overhead press 3x10, dips",
        "Amazon order: boAt Airdopes 141 delivered, return window closes 20 Nov",
        "Instagram post: sunset at Marine Drive, Mumbai",
        "Article: how transformers work, attention is all you need explained",
        "Hostel mess menu for this week: Monday rajma chawal, Tuesday chole bhature",
        "Professor's email: mid-semester exam moved to Thursday, syllabus unchanged",
        "Netflix: watch Dark season 2 next",
        "Spotify playlist: lo-fi beats for studying",
        "Cricket: India vs Australia 3rd ODI tickets on BookMyShow",
        "Apartment listing: 2BHK in Koramangala, rent 35,000, available December",
        "Meeting with the placement cell on Monday at 3 pm in room 204",
        "Bitcoin price alert: crossed 100k",
        "Python tutorial: list comprehensions and generators",
        "Doctor's appointment at Apollo, Tuesday 11 am, bring old reports",
    ).mapIndexed { i, t -> Doc("x$i", "distractor: ${t.take(40)}", t) }

    private val queries = listOf(
        "that finance lecture about managing cash" to setOf("7ea28fe0"),
        "cash forecasting notes from classroom" to setOf("7ea28fe0"),
        "side events around devcon" to setOf("41140234"),
        "web3 events in goa" to setOf("41140234"),
        "the k-pop song lyrics i screenshotted" to setOf("219c1c53"),
        "constitution of india syllabus" to setOf("032472ee"),
        "email saying i got accepted to the ethereum hackathon" to setOf("bd017560", "de6f7dea"),
        "qualcomm internship" to setOf("55694d07"),
        "the job i saved from linkedin" to setOf("55694d07"),
        "where priya said to eat" to setOf("dcde7b10"),
        "good burger place in bangalore" to setOf("dcde7b10"),
        "idea about remaking a game" to setOf("27bc0d32"),
    )

    private val pictureQueries = listOf(
        "screenshot of song lyrics" to setOf("219c1c53"),
        "a university course syllabus" to setOf("032472ee"),
        "hackathon acceptance email" to setOf("bd017560"),
        "an email in my inbox" to setOf("bd017560"),
    )

    private fun textOf(m: Memory) =
        listOfNotNull(m.title, m.rawText, m.extractedText, m.note).filter { it.isNotBlank() }.joinToString("\n").take(2_000)

    private fun pictureOf(m: Memory): File? =
        m.localUri?.takeIf { m.type == MemoryType.IMAGE }?.let { File(Uri.parse(it).path!!) }?.takeIf { it.exists() }

    private fun dot(a: FloatArray, b: FloatArray): Float {
        var s = 0f
        for (i in a.indices) s += a[i] * b[i]
        return s
    }

    @Test
    fun embeddingGemma2OnTheCpu() = runBlocking {
        check(model.exists()) { "push the model to ${model.path} first" }
        val memories = app.database.memoryDao().getAll()
        val real = memories.map { Doc(it.id.take(8), "${it.type} ${(it.title ?: it.rawText ?: it.extractedText ?: "").take(40)}", textOf(it), pictureOf(it)) }
        log("memories ${real.size}, distractors ${distractors.size}")

        val rssBefore = rssMb()
        val t0 = SystemClock.elapsedRealtime()
        val config = EmbeddingEngineConfig(
            modelPath = model.path,
            backend = Backend.CPU(),
            visionBackend = Backend.CPU(),
            cacheDir = context.cacheDir.path,
        )
        EmbeddingEngine(config).use { engine ->
            engine.initialize()
            log("init ${SystemClock.elapsedRealtime() - t0} ms, rss $rssBefore -> ${rssMb()} MB")
            val options = EmbeddingOptions(normalize = true)

            fun embed(inputs: List<InputData>): Pair<FloatArray, Long> {
                val s = SystemClock.elapsedRealtime()
                val v = engine.computeEmbedding(inputs, options).embedding
                return v to SystemClock.elapsedRealtime() - s
            }

            // Two prompt styles: the LiteRT-LM page's, and EmbeddingGemma 1's.
            val styles = listOf(
                Triple("v2 page", { d: Doc -> "task: search result | text: ${d.text.trim()}" }, { q: String -> "task: search query | text: ${q.trim()}" }),
                Triple("v1 style", { d: Doc -> "title: none | text: ${d.text.trim()}" }, { q: String -> "task: search result | query: ${q.trim()}" }),
            )
            val docs = real + distractors
            for ((name, docPrompt, queryPrompt) in styles) {
                val times = mutableListOf<Long>()
                val vectors = docs.associate { d ->
                    val (v, ms) = embed(listOf(InputData.Text(docPrompt(d))))
                    times += ms
                    d.id to v
                }
                log("[$name] dims ${vectors.values.first().size}; text embed avg ${times.average().toInt()} ms, max ${times.max()} ms over ${times.size}")
                var top1 = 0
                var top3 = 0
                for ((q, want) in queries) {
                    val (qv, qms) = embed(listOf(InputData.Text(queryPrompt(q))))
                    val ranked = docs.sortedByDescending { dot(qv, vectors.getValue(it.id)) }
                    val rank = ranked.indexOfFirst { it.id in want } + 1
                    if (rank == 1) top1++
                    if (rank in 1..3) top3++
                    log("[$name] \"$q\" -> rank $rank (${qms} ms); top: " +
                        ranked.take(3).joinToString(" | ") { "%.3f %s".format(dot(qv, vectors.getValue(it.id)), it.label) })
                }
                log("[$name] top-1 $top1/${queries.size}, top-3 $top3/${queries.size}")
            }

            // Pictures alone, no OCR text: does a description find the screenshot itself?
            val pictures = real.filter { it.picture != null }
            val picTimes = mutableListOf<Long>()
            val picVectors = pictures.associate { d ->
                val (v, ms) = embed(listOf(InputData.Image(d.picture!!.readBytes())))
                picTimes += ms
                d.id to v
            }
            log("picture embed avg ${picTimes.average().toInt()} ms, max ${picTimes.max()} ms over ${picTimes.size}; rss ${rssMb()} MB")
            val textOnly = (real.filter { it.picture == null } + distractors).associate { d ->
                d.id to embed(listOf(InputData.Text(styles[0].second(d)))).first
            }
            val mixed = picVectors + textOnly
            val labels = (real + distractors).associate { it.id to it.label }
            for ((q, want) in pictureQueries) {
                val qv = embed(listOf(InputData.Text(styles[0].third(q)))).first
                val ranked = mixed.entries.sortedByDescending { dot(qv, it.value) }
                val rank = ranked.indexOfFirst { it.key in want } + 1
                log("[pictures only] \"$q\" -> rank $rank; top: " +
                    ranked.take(3).joinToString(" | ") { "%.3f %s".format(dot(qv, it.value), labels[it.key]) })
            }
        }
    }
}
