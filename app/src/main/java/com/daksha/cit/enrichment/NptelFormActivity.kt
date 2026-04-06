package com.daksha.cit.enrichment

import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.app.DatePickerDialog
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.text.TextUtils
import android.text.method.KeyListener
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.ExecutorService
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class NptelFormActivity : AppCompatActivity() {
    private val registerNumberPattern = Regex("^[A-Za-z0-9/-]{4,20}$")
    private val uiHandler = Handler(Looper.getMainLooper())
    private val refreshSuggestionsRunnable = Runnable { refreshCourseSuggestions() }

    private var certificateUri: Uri? = null
    private var paymentUri: Uri? = null
    private var selectedSuggestedCourse: NptelCourse? = null
    private var isApplyingSuggestion = false

    private lateinit var progressBar: ProgressBar
    private lateinit var txtCertificateName: TextView
    private lateinit var txtCourseSuggestionState: TextView
    private lateinit var txtPaymentName: TextView
    private lateinit var txtFormTitle: TextView
    private lateinit var txtCourseLink: TextView
    private lateinit var txtCourseLinkHint: TextView
    private lateinit var txtUploadStatus: TextView
    private lateinit var formScrollView: ScrollView
    private lateinit var layoutCourseSuggestions: LinearLayout
    private lateinit var layoutRegisterNumber: TextInputLayout
    private lateinit var layoutDepartment: TextInputLayout
    private lateinit var layoutYear: TextInputLayout
    private lateinit var layoutCourseName: TextInputLayout
    private lateinit var layoutCourseId: TextInputLayout
    private lateinit var layoutExamDate: TextInputLayout
    private lateinit var layoutScore: TextInputLayout
    private lateinit var amountPaidLayout: View
    private lateinit var amountPaidInputLayout: TextInputLayout
    private lateinit var btnSubmitNptel: Button

    private lateinit var inputStudentName: TextInputEditText
    private lateinit var inputRegisterNumber: TextInputEditText
    private lateinit var inputDepartment: TextInputEditText
    private lateinit var inputYear: TextInputEditText
    private lateinit var inputCourseName: TextInputEditText
    private lateinit var inputCourseId: TextInputEditText
    private lateinit var inputExamDate: TextInputEditText
    private lateinit var inputScore: TextInputEditText
    private lateinit var inputAmountPaid: TextInputEditText
    private lateinit var inputRemarks: TextInputEditText
    private var inputCourseIdKeyListener: KeyListener? = null

    private lateinit var claimType: String
    private lateinit var formTitle: String

    private val pickCertificate = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        handleSelectedDocument(
            uri = uri,
            assignUri = { certificateUri = it },
            fileNameView = txtCertificateName,
            documentLabel = getString(R.string.label_certificate)
        )
    }

    private val pickPayment = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        handleSelectedDocument(
            uri = uri,
            assignUri = { paymentUri = it },
            fileNameView = txtPaymentName,
            documentLabel = getString(R.string.label_payment_proof)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nptel_form)

        progressBar = findViewById(R.id.progressNptel)
        txtCertificateName = findViewById(R.id.txtCertificateName)
        txtCourseSuggestionState = findViewById(R.id.txtCourseSuggestionState)
        txtPaymentName = findViewById(R.id.txtPaymentName)
        txtFormTitle = findViewById(R.id.txtFormTitle)
        txtCourseLink = findViewById(R.id.txtCourseLink)
        txtCourseLinkHint = findViewById(R.id.txtCourseLinkHint)
        txtUploadStatus = findViewById(R.id.txtUploadStatus)
        formScrollView = findViewById(R.id.scrollNptelForm)
        layoutCourseSuggestions = findViewById(R.id.layoutCourseSuggestions)
        layoutRegisterNumber = findViewById(R.id.layoutRegisterNumber)
        layoutDepartment = findViewById(R.id.layoutDepartment)
        layoutYear = findViewById(R.id.layoutYear)
        layoutCourseName = findViewById(R.id.layoutCourseName)
        layoutCourseId = findViewById(R.id.layoutCourseId)
        layoutExamDate = findViewById(R.id.layoutExamDate)
        layoutScore = findViewById(R.id.layoutScore)
        amountPaidLayout = findViewById(R.id.layoutAmountPaid)
        amountPaidInputLayout = findViewById(R.id.layoutAmountPaid)
        btnSubmitNptel = findViewById(R.id.btnSubmitNptel)

        claimType = intent.getStringExtra(EXTRA_CLAIM_TYPE).orEmpty().ifBlank { getString(R.string.apply_nptel) }
        formTitle = intent.getStringExtra(EXTRA_FORM_TITLE).orEmpty().ifBlank { getString(R.string.nptel_title) }
        txtFormTitle.text = formTitle

        inputStudentName = findViewById(R.id.inputStudentName)
        inputRegisterNumber = findViewById(R.id.inputRegisterNumber)
        inputDepartment = findViewById(R.id.inputDepartment)
        inputYear = findViewById(R.id.inputYear)
        inputCourseName = findViewById(R.id.inputCourseName)
        inputCourseId = findViewById(R.id.inputCourseId)
        inputCourseIdKeyListener = inputCourseId.keyListener
        inputExamDate = findViewById(R.id.inputExamDate)
        inputScore = findViewById(R.id.inputScore)
        inputAmountPaid = findViewById(R.id.inputAmountPaid)
        inputRemarks = findViewById(R.id.inputRemarks)

        inputDepartment.setOnClickListener {
            showDepartmentPicker()
        }
        inputExamDate.setOnClickListener {
            showExamDatePicker()
        }

        bindEnterToNext(inputStudentName, inputRegisterNumber)
        bindEnterToAction(inputRegisterNumber) {
            showDepartmentPicker()
        }
        bindEnterToNext(inputYear, inputCourseName)
        bindEnterToNext(inputExamDate, inputScore)

        bindEnterToAction(inputRemarks) {
            submitForm()
        }

        if (claimType != getString(R.string.apply_nptel)) {
            inputCourseName.hint = getString(R.string.label_claim_title)
            inputCourseId.hint = getString(R.string.label_reference_id)
            inputExamDate.hint = getString(R.string.label_claim_date)
            inputScore.hint = getString(R.string.label_outcome)
            layoutCourseId.prefixText = null
            setCourseIdEditable(isEditable = true)
            amountPaidLayout.visibility = View.VISIBLE
            bindEnterToNext(inputCourseName, inputCourseId)
            bindEnterToDatePicker(inputCourseId)
            bindEnterToNext(inputScore, inputAmountPaid)
            bindEnterToNext(inputAmountPaid, inputRemarks)
            txtCourseSuggestionState.visibility = View.GONE
            layoutCourseSuggestions.visibility = View.GONE
        } else {
            setCourseIdEditable(isEditable = false)
            amountPaidLayout.visibility = View.GONE
            bindEnterToDatePicker(inputCourseName)
            layoutCourseId.prefixText = getString(R.string.prefix_course_id)
            bindEnterToNext(inputScore, inputRemarks)
            setupNptelCourseSuggestions()
        }

        findViewById<View>(R.id.btnBackNptel).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.btnPickCertificate).setOnClickListener {
            pickCertificate.launch(SUPPORTED_DOCUMENT_TYPES)
        }

        findViewById<Button>(R.id.btnPickPayment).setOnClickListener {
            pickPayment.launch(SUPPORTED_DOCUMENT_TYPES)
        }

        btnSubmitNptel.setOnClickListener {
            submitForm()
        }

        txtCourseLink.setOnClickListener {
            openSelectedCourseLink()
        }
    }

    override fun onDestroy() {
        uiHandler.removeCallbacks(refreshSuggestionsRunnable)
        super.onDestroy()
    }

    private fun setupNptelCourseSuggestions() {
        inputCourseName.doAfterTextChanged { editable ->
            if (isApplyingSuggestion) {
                return@doAfterTextChanged
            }

            val currentText = editable?.toString().orEmpty()
            val selectedCourse = selectedSuggestedCourse
            if (selectedCourse != null && normalizeCourseText(currentText) != selectedCourse.normalizedName) {
                if (inputCourseId.text?.toString()?.trim() == selectedCourse.courseId) {
                    inputCourseId.setText("")
                }
                selectedSuggestedCourse = null
                updateCourseLink(null)
            }

            uiHandler.removeCallbacks(refreshSuggestionsRunnable)
            uiHandler.postDelayed(refreshSuggestionsRunnable, COURSE_SUGGESTION_DEBOUNCE_MS)
        }

        setSuggestionState(getString(R.string.status_loading_nptel_courses), showList = false)
        loadNptelCourseCatalog(forceRefresh = true)
    }

    private fun loadNptelCourseCatalog(forceRefresh: Boolean = false) {
        synchronized(nptelCatalogLock) {
            if (cachedNptelCourses != null && !forceRefresh) {
                refreshCourseSuggestions()
                return
            }
            if (isNptelCatalogLoading) {
                return
            }
            isNptelCatalogLoading = true
        }

        Thread {
            val shouldLoadBundledCatalog = synchronized(nptelCatalogLock) {
                cachedNptelCourses.isNullOrEmpty()
            }

            if (shouldLoadBundledCatalog) {
                val bundledCourses = loadBundledNptelCourses()
                if (bundledCourses.isNotEmpty()) {
                    uiHandler.post {
                        synchronized(nptelCatalogLock) {
                            if (cachedNptelCourses.isNullOrEmpty()) {
                                cachedNptelCourses = bundledCourses
                            }
                        }
                        refreshCourseSuggestions()
                    }
                }
            }

            val fetchedCourses = try {
                fetchNptelCourses()
            } catch (_: Exception) {
                emptyList()
            }

            uiHandler.post {
                synchronized(nptelCatalogLock) {
                    isNptelCatalogLoading = false
                    if (fetchedCourses.isNotEmpty()) {
                        cachedNptelCourses = fetchedCourses
                    }
                }

                refreshCourseSuggestions()
            }
        }.start()
    }

    private fun loadBundledNptelCourses(): List<NptelCourse> {
        return try {
            val rawJson = assets.open(NPTEL_BUNDLED_CATALOG_ASSET).bufferedReader().use { reader ->
                reader.readText()
            }
            val items = JSONArray(rawJson)
            buildList {
                for (index in 0 until items.length()) {
                    val item = items.optJSONObject(index) ?: continue
                    val courseName = item.optString("name").trim()
                    val courseId = item.optString("courseId").trim()
                    if (courseName.isBlank() || courseId.isBlank()) {
                        continue
                    }
                    add(
                        NptelCourse(
                            name = courseName,
                            courseId = courseId,
                            professorName = item.optString("professorName").trim(),
                            credits = item.optInt("credits").takeIf { it > 0 },
                            detailUrl = item.optString("detailUrl").trim(),
                            normalizedName = normalizeCourseText(courseName)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchNptelCourses(): List<NptelCourse> {
        val document = Jsoup.connect(NPTEL_COURSES_URL)
            .userAgent(NPTEL_USER_AGENT)
            .timeout(NPTEL_CATALOG_TIMEOUT_MS)
            .get()

        return document
            .select("a.course-card[href^=/courses/], a[href^=/courses/]")
            .mapNotNull { courseElement ->
                val courseName = courseElement.selectFirst("div.name, .name")?.text()?.trim().orEmpty()
                val href = courseElement.attr("href").trim()
                if (courseName.isBlank() || href.isBlank()) {
                    return@mapNotNull null
                }

                val courseId = href.substringAfterLast('/').substringBefore('?').trim()
                if (courseId.isBlank()) {
                    return@mapNotNull null
                }

                val absoluteUrl = if (href.startsWith("http", ignoreCase = true)) {
                    href
                } else {
                    "https://nptel.ac.in$href"
                }

                val professorName = courseElement
                    .selectFirst(".faculty, .faculty_name, .instructor, .teacher")
                    ?.text()
                    ?.trim()
                    .orEmpty()

                val credits = extractCreditsFromText(
                    listOfNotNull(
                        courseElement.selectFirst(".credit, .credits, .course-credit")?.text(),
                        courseElement.text()
                    ).joinToString(" ")
                )

                NptelCourse(
                    name = courseName,
                    courseId = courseId,
                    professorName = professorName,
                    credits = credits,
                    detailUrl = absoluteUrl,
                    normalizedName = normalizeCourseText(courseName)
                )
            }
            .distinctBy { course -> "${course.courseId}:${course.normalizedName}" }
            .sortedBy { it.name.lowercase(Locale.getDefault()) }
    }

    private fun refreshCourseSuggestions() {
        if (claimType != getString(R.string.apply_nptel)) {
            return
        }

        val query = inputCourseName.text?.toString().orEmpty()
        val normalizedQuery = normalizeCourseText(query)
        val queryWords = tokenizeMeaningfulWords(query)

        if (query.isBlank()) {
            setSuggestionState(null, showList = false)
            return
        }

        if (queryWords.size < MIN_MATCHING_WORDS) {
            setSuggestionState(getString(R.string.hint_nptel_suggestions_min_words), showList = false)
            return
        }

        val catalog = cachedNptelCourses
        if (catalog == null) {
            val statusText = if (isNptelCatalogLoading) {
                getString(R.string.status_loading_nptel_courses)
            } else {
                getString(R.string.message_nptel_suggestions_unavailable)
            }
            setSuggestionState(statusText, showList = false)
            if (!isNptelCatalogLoading) {
                loadNptelCourseCatalog()
            }
            return
        }

        val rankedCourses = catalog
            .mapNotNull { course -> rankCourseSuggestion(course, normalizedQuery, queryWords) }
            .sortedWith(
                compareByDescending<RankedCourseSuggestion> { it.exactPhraseMatch }
                    .thenByDescending { it.startsWithPhrase }
                    .thenByDescending { it.containsPhrase }
                    .thenByDescending { it.matchesAllQueryWords }
                    .thenByDescending { it.matchingWordCount }
                    .thenBy { it.course.name.length }
                    .thenBy { it.course.name.lowercase(Locale.getDefault()) }
            )
            .take(MAX_COURSE_SUGGESTIONS)

        if (rankedCourses.isEmpty()) {
            setSuggestionState(getString(R.string.message_nptel_suggestions_empty), showList = false)
            return
        }

        renderCourseSuggestions(rankedCourses.map { it.course })
    }

    private fun rankCourseSuggestion(
        course: NptelCourse,
        normalizedQuery: String,
        queryWords: List<String>
    ): RankedCourseSuggestion? {
        val courseWords = tokenizeMeaningfulWords(course.name).toSet()
        val matchingWordCount = queryWords.count { it in courseWords }
        if (matchingWordCount < MIN_MATCHING_WORDS) {
            return null
        }

        return RankedCourseSuggestion(
            course = course,
            exactPhraseMatch = course.normalizedName == normalizedQuery,
            startsWithPhrase = course.normalizedName.startsWith(normalizedQuery),
            containsPhrase = course.normalizedName.contains(normalizedQuery),
            matchesAllQueryWords = matchingWordCount == queryWords.size,
            matchingWordCount = matchingWordCount
        )
    }

    private fun renderCourseSuggestions(courses: List<NptelCourse>) {
        layoutCourseSuggestions.removeAllViews()

        courses.forEachIndexed { index, course ->
            val suggestionView = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (index > 0) {
                        topMargin = resources.getDimensionPixelSize(R.dimen.spacing_small)
                    }
                }
                background = getDrawable(R.drawable.bg_glass_input)
                setPadding(
                    resources.getDimensionPixelSize(R.dimen.nptel_input_horizontal_padding),
                    resources.getDimensionPixelSize(R.dimen.nptel_suggestion_vertical_padding),
                    resources.getDimensionPixelSize(R.dimen.nptel_input_horizontal_padding),
                    resources.getDimensionPixelSize(R.dimen.nptel_suggestion_vertical_padding)
                )
                setTextColor(getColor(R.color.cit_text_primary))
                textSize = 13f
                ellipsize = TextUtils.TruncateAt.END
                maxLines = 2
                text = getString(R.string.label_nptel_course_item, course.name)
                setOnClickListener {
                    applyCourseSuggestion(course)
                }
            }
            layoutCourseSuggestions.addView(suggestionView)
        }

        setSuggestionState(getString(R.string.label_nptel_suggestions), showList = true)
    }

    private fun applyCourseSuggestion(course: NptelCourse) {
        isApplyingSuggestion = true
        inputCourseName.setText(course.name)
        inputCourseName.setSelection(course.name.length)
        inputCourseId.setText(course.courseId)
        selectedSuggestedCourse = course
        isApplyingSuggestion = false
        // Collapse suggestions after a pick; they will appear again when the user edits text.
        setSuggestionState(null, showList = false)
        updateCourseLink(course)
        if (course.credits == null || course.professorName.isBlank()) {
            enrichSelectedCourse(course)
        }
    }

    private fun updateCourseLink(course: NptelCourse?) {
        if (course == null) {
            txtCourseLink.visibility = View.GONE
            txtCourseLinkHint.visibility = View.GONE
            return
        }
        txtCourseLink.visibility = View.VISIBLE
        txtCourseLinkHint.visibility = View.VISIBLE
    }

    private fun openSelectedCourseLink() {
        val course = selectedSuggestedCourse ?: return
        val url = course.detailUrl.ifBlank { "https://nptel.ac.in/courses/${course.courseId}" }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }

    private fun enrichSelectedCourse(course: NptelCourse) {
        val effectiveUrl = course.detailUrl.ifBlank { "https://nptel.ac.in/courses/${course.courseId}" }
        Thread {
            val document = try {
                Jsoup.connect(effectiveUrl)
                    .userAgent(NPTEL_USER_AGENT)
                    .timeout(NPTEL_CATALOG_TIMEOUT_MS)
                    .get()
            } catch (_: Exception) { return@Thread }
            val resolvedCredits = extractCreditsFromText(document.text())
            val resolvedProfessor = extractProfessorFromDocument(document)
            synchronized(nptelCatalogLock) {
                cachedNptelCourses = cachedNptelCourses?.map {
                    if (it.courseId == course.courseId && it.normalizedName == course.normalizedName) {
                        it.copy(
                            credits = resolvedCredits ?: it.credits,
                            professorName = resolvedProfessor?.takeIf { p -> p.isNotBlank() } ?: it.professorName
                        )
                    } else { it }
                }
            }
            val selected = selectedSuggestedCourse
            if (selected != null && selected.courseId == course.courseId && selected.normalizedName == course.normalizedName) {
                selectedSuggestedCourse = selected.copy(
                    credits = resolvedCredits ?: selected.credits,
                    professorName = resolvedProfessor?.takeIf { p -> p.isNotBlank() } ?: selected.professorName
                )
            }
        }.start()
    }

    private fun extractProfessorFromDocument(document: org.jsoup.nodes.Document): String? {
        for (script in document.select("script[type=application/ld+json]")) {
            try {
                val json = JSONObject(script.data())
                val instructorObj = json.optJSONObject("instructor")
                    ?: json.optJSONArray("instructor")?.optJSONObject(0)
                val name = instructorObj?.optString("name")?.trim()
                if (!name.isNullOrBlank()) return name
            } catch (_: Exception) { }
        }
        listOf(
            ".faculty-name", ".instructor-name", ".course-instructor",
            "[itemprop=instructor]", "[itemprop=author]",
            ".faculty_name", ".instructorName"
        ).forEach { selector ->
            val found = document.selectFirst(selector)?.text()?.trim()
            if (!found.isNullOrBlank()) return found
        }
        val match = PROFESSOR_REGEX.find(document.text())
        if (match != null) {
            return "Prof. ${match.groupValues[1].trim()}"
        }
        return null
    }

    private fun extractCreditsFromText(value: String): Int? {
        val match = CREDIT_REGEX.find(value)
        val raw = match?.groupValues?.getOrNull(1)
        return raw?.toIntOrNull()?.takeIf { it in 1..12 }
    }

    private fun setSuggestionState(message: String?, showList: Boolean) {
        txtCourseSuggestionState.text = message.orEmpty()
        txtCourseSuggestionState.visibility = if (message.isNullOrBlank()) View.GONE else View.VISIBLE
        layoutCourseSuggestions.visibility = if (showList && layoutCourseSuggestions.childCount > 0) View.VISIBLE else View.GONE
        if (!showList) {
            layoutCourseSuggestions.removeAllViews()
        }
    }

    private fun normalizeCourseText(value: String): String {
        return value
            .lowercase(Locale.getDefault())
            .replace(NON_ALPHANUMERIC_REGEX, " ")
            .replace(MULTIPLE_SPACES_REGEX, " ")
            .trim()
    }

    private fun tokenizeMeaningfulWords(value: String): List<String> {
        return normalizeCourseText(value)
            .split(' ')
            .filter { token ->
                token.length >= MIN_TOKEN_LENGTH && token !in IGNORED_QUERY_WORDS
            }
            .distinct()
    }

    private fun parseScoreValue(value: String): Int? {
        return SCORE_NUMBER_REGEX.find(value)?.value?.toIntOrNull()
    }

    private fun submitForm() {
        val studentName = inputStudentName.text?.toString()?.trim().orEmpty()
        val registerNumber = inputRegisterNumber.text?.toString()?.trim().orEmpty()
        val department = inputDepartment.text?.toString()?.trim().orEmpty()
        val year = inputYear.text?.toString()?.trim().orEmpty()
        val courseName = inputCourseName.text?.toString()?.trim().orEmpty()
        val courseId = inputCourseId.text?.toString()?.trim().orEmpty()
        val claimDate = inputExamDate.text?.toString()?.trim().orEmpty()
        val score = inputScore.text?.toString()?.trim().orEmpty()
        val amountPaid = if (claimType == getString(R.string.apply_nptel)) {
            getString(R.string.label_amount_fixed_nptel)
        } else {
            inputAmountPaid.text?.toString()?.trim().orEmpty()
        }

        clearFieldErrors()

        var firstInvalidView: View? = null
        if (studentName.isEmpty()) {
            inputStudentName.error = getString(R.string.error_fill_required_fields)
            firstInvalidView = firstInvalidView ?: inputStudentName
        }
        if (!registerNumber.matches(registerNumberPattern)) {
            layoutRegisterNumber.error = getString(R.string.error_register_number_invalid)
            firstInvalidView = firstInvalidView ?: inputRegisterNumber
        }
        if (department.isEmpty()) {
            layoutDepartment.error = getString(R.string.error_select_department)
            firstInvalidView = firstInvalidView ?: inputDepartment
        }
        if (year.isEmpty()) {
            layoutYear.error = getString(R.string.error_fill_required_fields)
            firstInvalidView = firstInvalidView ?: inputYear
        } else if (year !in setOf("1", "2", "3", "4")) {
            layoutYear.error = getString(R.string.error_year_invalid)
            firstInvalidView = firstInvalidView ?: inputYear
        }
        if (courseName.isEmpty()) {
            layoutCourseName.error = getString(R.string.error_fill_required_fields)
            firstInvalidView = firstInvalidView ?: inputCourseName
        }
        if (courseId.isEmpty()) {
            layoutCourseId.error = if (claimType == getString(R.string.apply_nptel)) {
                getString(R.string.error_select_nptel_suggestion)
            } else {
                getString(R.string.error_fill_required_fields)
            }
            firstInvalidView = firstInvalidView ?: if (claimType == getString(R.string.apply_nptel)) inputCourseName else inputCourseId
        }
        if (claimDate.isEmpty()) {
            layoutExamDate.error = getString(R.string.error_claim_date_required)
            firstInvalidView = firstInvalidView ?: inputExamDate
        }
        if (score.isEmpty()) {
            layoutScore.error = getString(R.string.error_fill_required_fields)
            firstInvalidView = firstInvalidView ?: inputScore
        } else if (claimType == getString(R.string.apply_nptel)) {
            val numericScore = parseScoreValue(score)
            if (numericScore != null && numericScore < MIN_NPTEL_SCORE) {
                layoutScore.error = getString(R.string.error_score_below_minimum)
                firstInvalidView = firstInvalidView ?: inputScore
            }
        }

        if (claimType != getString(R.string.apply_nptel)) {
            val amountValue = amountPaid.toDoubleOrNull()
            if (amountValue == null || amountValue <= 0.0) {
                amountPaidInputLayout.error = getString(R.string.error_amount_invalid)
                firstInvalidView = firstInvalidView ?: inputAmountPaid
            }
        }

        if (firstInvalidView != null) {
            scrollToInvalidField(firstInvalidView)
            Toast.makeText(this, R.string.error_fix_form_fields, Toast.LENGTH_SHORT).show()
            return
        }

        if (certificateUri == null || paymentUri == null) {
            Toast.makeText(this, R.string.error_documents_required, Toast.LENGTH_SHORT).show()
            return
        }

        if (certificateUri == paymentUri) {
            Toast.makeText(this, R.string.error_documents_must_differ, Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true, getString(R.string.status_upload_starting))

        if (BuildConfig.ENABLE_FIREBASE_AUTH) {
            SessionManager.ensureBackendSession(
                context = this,
                onReady = {
                    saveClaimToBackend(studentName, registerNumber, department, year, courseName, courseId, claimDate, score, amountPaid)
                },
                onFailure = { message ->
                    setLoading(false, message, keepStatusVisible = true)
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            )
        } else {
            saveClaimToBackend(studentName, registerNumber, department, year, courseName, courseId, claimDate, score, amountPaid)
        }
    }

    private fun saveClaimToBackend(
        studentName: String,
        registerNumber: String,
        department: String,
        year: String,
        courseName: String,
        courseId: String,
        claimDate: String,
        score: String,
        amountPaid: String
    ) {
        val db = FirebaseFirestore.getInstance()
        val docRef = db.collection("claims").document()
        val currentUser = FirebaseAuth.getInstance().currentUser
        val userKey = SessionManager.getActiveUserKey(this)

        val certificateName = getDisplayName(certificateUri!!)
        val paymentName = getDisplayName(paymentUri!!)
        val nptelCredits = if (claimType == getString(R.string.apply_nptel)) {
            selectedSuggestedCourse?.credits
        } else {
            null
        }
        var certificateUploadResult: UploadedDocument? = null
        var paymentUploadResult: UploadedDocument? = null
        var uploadFailed = false
        val uploadLock = Any()

        fun maybeProceedAfterUploads() {
            val certificateUpload = certificateUploadResult
            val paymentUpload = paymentUploadResult
            if (uploadFailed || certificateUpload == null || paymentUpload == null) {
                return
            }

            val data = hashMapOf(
                "claimType" to claimType,
                "title" to courseName,
                "claimDate" to claimDate,
                "userKey" to userKey,
                "userId" to (currentUser?.uid ?: ""),
                "isGuest" to (SessionManager.isGuestMode(this)),
                "studentName" to studentName,
                "registerNumber" to registerNumber,
                "department" to department,
                "year" to year,
                "courseName" to courseName,
                "courseId" to courseId,
                "nptelCredits" to (nptelCredits ?: 0),
                "examDate" to claimDate,
                "score" to score,
                "amountPaid" to amountPaid,
                "remarks" to inputRemarks.text?.toString()?.trim().orEmpty(),
                "certificateUrl" to certificateUpload.publicUrl,
                "paymentUrl" to paymentUpload.publicUrl,
                "certificateStoragePath" to certificateUpload.storagePath,
                "paymentStoragePath" to paymentUpload.storagePath,
                "certificateFileName" to certificateName,
                "paymentFileName" to paymentName,
                "status" to getString(R.string.claim_status_pending),
                "submittedAtEpochMs" to System.currentTimeMillis(),
                "submittedAt" to FieldValue.serverTimestamp()
            )

            if (SessionManager.isGuestMode(this)) {
                LocalClaimStore.saveClaim(this, data)
                updateStatus(getString(R.string.status_claim_submitted))
                Toast.makeText(this, getString(R.string.message_claim_submitted, claimType), Toast.LENGTH_SHORT).show()
                setLoading(false, getString(R.string.status_claim_submitted), keepStatusVisible = true)
                startActivity(Intent(this, ClaimStatusActivity::class.java))
                finish()
                return
            }

            // Show success immediately after uploads complete
            updateStatus(getString(R.string.status_claim_submitted))
            Toast.makeText(this, getString(R.string.message_claim_submitted, claimType), Toast.LENGTH_SHORT).show()
            setLoading(false, getString(R.string.status_claim_submitted), keepStatusVisible = true)

            // Save to Firestore in background (fire-and-forget)
            docRef.set(data)

            // Navigate to claim status
            startActivity(Intent(this, ClaimStatusActivity::class.java))
            finish()
        }

        fun handleUploadFailure(message: String) {
            synchronized(uploadLock) {
                if (uploadFailed) {
                    return
                }
                uploadFailed = true
            }
            setLoading(false, message, keepStatusVisible = true)
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }

        updateStatus(getString(R.string.status_uploading_documents))
        uploadDocument(
            uri = certificateUri!!,
            fileName = certificateName,
            failurePrefix = getString(R.string.error_certificate_upload_failed),
            onSuccess = { upload ->
                synchronized(uploadLock) {
                    if (uploadFailed) {
                        return@uploadDocument
                    }
                    certificateUploadResult = upload
                }
                maybeProceedAfterUploads()
            },
            onFailure = { message ->
                handleUploadFailure(message)
            }
        )

        uploadDocument(
            uri = paymentUri!!,
            fileName = paymentName,
            failurePrefix = getString(R.string.error_payment_upload_failed),
            onSuccess = { upload ->
                synchronized(uploadLock) {
                    if (uploadFailed) {
                        return@uploadDocument
                    }
                    paymentUploadResult = upload
                }
                maybeProceedAfterUploads()
            },
            onFailure = { message ->
                handleUploadFailure(message)
            }
        )
    }


    private fun setLoading(isLoading: Boolean, statusMessage: String? = null, keepStatusVisible: Boolean = false) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        btnSubmitNptel.isEnabled = !isLoading
        if (statusMessage != null) {
            txtUploadStatus.text = statusMessage
        }
        txtUploadStatus.visibility = if (isLoading || keepStatusVisible || !txtUploadStatus.text.isNullOrBlank()) {
            View.VISIBLE
        } else {
            View.GONE
        }
        btnSubmitNptel.text = if (isLoading) getString(R.string.action_sending) else getString(R.string.action_submit)
    }

    private fun updateStatus(message: String) {
        txtUploadStatus.text = message
        txtUploadStatus.visibility = View.VISIBLE
    }

    private fun clearFieldErrors() {
        inputStudentName.error = null
        layoutRegisterNumber.error = null
        layoutDepartment.error = null
        layoutYear.error = null
        layoutCourseName.error = null
        layoutCourseId.error = null
        layoutExamDate.error = null
        layoutScore.error = null
        if (amountPaidLayout.visibility == View.VISIBLE) {
            amountPaidInputLayout.error = null
        }
    }

    private fun scrollToInvalidField(field: View) {
        field.requestFocus()
        formScrollView.post {
            formScrollView.smoothScrollTo(0, (field.top - resources.getDimensionPixelSize(R.dimen.nptel_error_scroll_offset)).coerceAtLeast(0))
        }
        if (!field.isFocusable && field.isClickable) {
            field.performClick()
        }
    }

    private fun handleSelectedDocument(
        uri: Uri?,
        assignUri: (Uri?) -> Unit,
        fileNameView: TextView,
        documentLabel: String
    ) {
        if (uri == null) {
            assignUri(null)
            fileNameView.text = getString(R.string.label_no_file)
            return
        }

        val validationError = validateDocument(uri, documentLabel)
        if (validationError != null) {
            assignUri(null)
            fileNameView.text = getString(R.string.label_no_file)
            Toast.makeText(this, validationError, Toast.LENGTH_LONG).show()
            return
        }

        try {
            contentResolver.takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // Some document providers only grant temporary read access.
        }

        assignUri(uri)
        fileNameView.text = getDisplayName(uri)
    }

    private fun validateDocument(uri: Uri, documentLabel: String): String? {
        val mimeType = contentResolver.getType(uri).orEmpty().lowercase()
        val fileName = getDisplayName(uri).lowercase()
        val isValidType = mimeType == PDF_MIME_TYPE || mimeType.startsWith(IMAGE_MIME_PREFIX) ||
            SUPPORTED_DOCUMENT_EXTENSIONS.any { fileName.endsWith(it) }

        if (!isValidType) {
            return getString(R.string.error_document_type_invalid, documentLabel)
        }

        val fileSizeBytes = getFileSize(uri)
        if (fileSizeBytes != null && fileSizeBytes > MAX_DOCUMENT_SIZE_BYTES) {
            return getString(R.string.error_document_too_large, documentLabel, MAX_DOCUMENT_SIZE_MB)
        }

        return null
    }

    private fun uploadDocument(
        uri: Uri,
        fileName: String,
        failurePrefix: String,
        onSuccess: (UploadedDocument) -> Unit,
        onFailure: (String) -> Unit
    ) {
        uploadExecutor.execute {
            try {
                val uploadResult = performSupabaseUpload(uri, fileName)
                runOnUiThread {
                    onSuccess(uploadResult)
                }
            } catch (error: Exception) {
                runOnUiThread {
                    val message = "$failurePrefix: ${error.message}"
                    onFailure(message)
                }
            }
        }
    }

    private fun performSupabaseUpload(uri: Uri, fileName: String): UploadedDocument {
        if (SUPABASE_URL.isBlank() || SUPABASE_ANON_KEY.isBlank()) {
            throw IllegalStateException(getString(R.string.error_upload_not_configured))
        }

        val inputStream = try {
            contentResolver.openInputStream(uri)
        } catch (error: SocketTimeoutException) {
            throw IllegalStateException(getString(R.string.error_upload_timed_out))
        } catch (error: Exception) {
            throw IllegalStateException(error.message ?: getString(R.string.error_document_open_failed, getString(R.string.app_name)))
        }

        if (inputStream == null) {
            throw IllegalStateException(getString(R.string.error_document_open_failed, getString(R.string.app_name)))
        }

        val mimeType = contentResolver.getType(uri).orEmpty().ifBlank { DEFAULT_UPLOAD_MIME_TYPE }
        val storagePath = buildSupabaseStoragePath(fileName)
        val fileSize = getFileSize(uri) ?: -1L
        val connection = (URL(supabaseObjectUploadUrl(storagePath)).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = NETWORK_TIMEOUT_MS
            readTimeout = NETWORK_TIMEOUT_MS
            doInput = true
            doOutput = true
            useCaches = false
            // Set content length for streaming optimization
            if (fileSize > 0) {
                setFixedLengthStreamingMode(fileSize)
            }
            setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
            setRequestProperty("apikey", SUPABASE_ANON_KEY)
            setRequestProperty("x-upsert", "true")
            setRequestProperty("Content-Type", mimeType)
            // Optimize connection for faster transfers
            setRequestProperty("Connection", "keep-alive")
        }

        try {
            connection.outputStream.use { output ->
                val buffer = ByteArray(UPLOAD_BUFFER_SIZE)
                var bytesRead = inputStream.read(buffer)
                while (bytesRead != -1) {
                    output.write(buffer, 0, bytesRead)
                    bytesRead = inputStream.read(buffer)
                }
                output.flush()
            }

            val responseCode = connection.responseCode
            val responseText = readResponseText(
                if (responseCode in 200..299) connection.inputStream else connection.errorStream
            )

            if (responseCode !in 200..299) {
                val message = parseSupabaseErrorMessage(responseText, responseCode)
                throw IllegalStateException(message)
            }

            return UploadedDocument(
                publicUrl = supabasePublicObjectUrl(storagePath),
                storagePath = storagePath
            )
        } catch (_: SocketTimeoutException) {
            throw IllegalStateException(getString(R.string.error_upload_timed_out))
        } finally {
            try {
                inputStream.close()
            } catch (_: Exception) {
            }
            connection.disconnect()
        }
    }

    private fun buildSupabaseStoragePath(fileName: String): String {
        val safeFileName = fileName
            .trim()
            .ifBlank { "document" }
            .replace(UNSAFE_FILENAME_CHARS_REGEX, "_")

        val claimSegment = claimType
            .trim()
            .lowercase(Locale.getDefault())
            .replace(UNSAFE_PATH_CHARS_REGEX, "-")
            .trim('-')
            .ifBlank { "claim" }

        val folder = SUPABASE_STORAGE_FOLDER.trim('/').ifBlank { "claims" }
        return "$folder/$claimSegment/${System.currentTimeMillis()}_$safeFileName"
    }

    private fun supabaseObjectUploadUrl(storagePath: String): String {
        val encodedPath = encodeStoragePath(storagePath)
        return "${SUPABASE_URL.trimEnd('/')}/storage/v1/object/$SUPABASE_STORAGE_BUCKET/$encodedPath"
    }

    private fun supabasePublicObjectUrl(storagePath: String): String {
        val encodedPath = encodeStoragePath(storagePath)
        return "${SUPABASE_URL.trimEnd('/')}/storage/v1/object/public/$SUPABASE_STORAGE_BUCKET/$encodedPath"
    }

    private fun encodeStoragePath(path: String): String {
        return path
            .split('/')
            .filter { it.isNotBlank() }
            .joinToString("/") { segment ->
                URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
            }
    }

    private fun parseSupabaseErrorMessage(responseText: String, responseCode: Int): String {
        if (responseText.isBlank()) {
            return "HTTP $responseCode"
        }

        return try {
            val json = JSONObject(responseText)
            json.optString("message")
                .ifBlank { json.optString("error") }
                .ifBlank { "HTTP $responseCode" }
        } catch (_: Exception) {
            "HTTP $responseCode"
        }
    }

    private fun readResponseText(stream: java.io.InputStream?): String {
        if (stream == null) {
            return ""
        }

        return BufferedReader(InputStreamReader(stream)).use { reader ->
            buildString {
                var line = reader.readLine()
                while (line != null) {
                    append(line)
                    line = reader.readLine()
                }
            }
        }
    }

    private fun showDepartmentPicker() {
        val departments = resources.getStringArray(R.array.department_options)
        AlertDialog.Builder(this)
            .setItems(departments) { _, which ->
                inputDepartment.setText(departments[which])
                inputYear.requestFocus()
            }
            .show()
    }

    private fun showExamDatePicker() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                val selectedDate = Calendar.getInstance().apply {
                    set(year, month, dayOfMonth)
                }
                inputExamDate.setText(dateFormatter.format(selectedDate.time))
                inputScore.requestFocus()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun bindEnterToDatePicker(currentField: TextView) {
        bindEnterToAction(currentField) {
            showExamDatePicker()
        }
    }

    private fun bindEnterToNext(currentField: TextView, nextField: View) {
        bindEnterToAction(currentField) {
            nextField.requestFocus()
        }
    }

    private fun bindEnterToAction(currentField: TextView, onAdvance: () -> Unit) {
        currentField.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_ACTION_DONE) {
                onAdvance()
                true
            } else {
                false
            }
        }
        currentField.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                onAdvance()
                true
            } else {
                false
            }
        }
    }

    private fun setCourseIdEditable(isEditable: Boolean) {
        inputCourseId.isFocusable = isEditable
        inputCourseId.isFocusableInTouchMode = isEditable
        inputCourseId.isClickable = isEditable
        inputCourseId.isCursorVisible = isEditable
        inputCourseId.isLongClickable = isEditable
        inputCourseId.keyListener = if (isEditable) inputCourseIdKeyListener else null
    }

    private fun getFileExtension(fileName: String): String {
        val dotIndex = fileName.lastIndexOf('.')
        return if (dotIndex >= 0) fileName.substring(dotIndex) else ""
    }

    private fun getFileSize(uri: Uri): Long? {
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
            if (sizeIndex >= 0 && it.moveToFirst() && !it.isNull(sizeIndex)) {
                return it.getLong(sizeIndex)
            }
        }
        return null
    }

    private fun getDisplayName(uri: Uri): String {
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && it.moveToFirst()) {
                return it.getString(nameIndex)
            }
        }
        return uri.lastPathSegment ?: getString(R.string.label_no_file)
    }

    companion object {
        private val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        private const val NETWORK_TIMEOUT_MS = 20000
        private const val NPTEL_CATALOG_TIMEOUT_MS = 20000
        private val uploadExecutor: ExecutorService = Executors.newFixedThreadPool(2)
        private const val UPLOAD_BUFFER_SIZE = 262144 // 256KB for efficient I/O
        private const val COURSE_SUGGESTION_DEBOUNCE_MS = 250L
        private const val MAX_COURSE_SUGGESTIONS = 6
        private const val MIN_MATCHING_WORDS = 1
        private const val MIN_TOKEN_LENGTH = 2
        private const val MIN_NPTEL_SCORE = 60
        private val SUPABASE_URL = BuildConfig.SUPABASE_URL
        private val SUPABASE_ANON_KEY = BuildConfig.SUPABASE_ANON_KEY
        private val SUPABASE_STORAGE_BUCKET = BuildConfig.SUPABASE_STORAGE_BUCKET
        private val SUPABASE_STORAGE_FOLDER = BuildConfig.SUPABASE_STORAGE_FOLDER
        private const val DEFAULT_UPLOAD_MIME_TYPE = "application/octet-stream"
        private const val PDF_MIME_TYPE = "application/pdf"
        private const val IMAGE_MIME_PREFIX = "image/"
        private const val MAX_DOCUMENT_SIZE_MB = 10
        private const val MAX_DOCUMENT_SIZE_BYTES = MAX_DOCUMENT_SIZE_MB * 1024 * 1024L
        private const val EXTRA_CLAIM_TYPE = "extra_claim_type"
        private const val EXTRA_FORM_TITLE = "extra_form_title"
        private const val NPTEL_BUNDLED_CATALOG_ASSET = "nptel_courses_seed.json"
        private const val NPTEL_COURSES_URL = "https://nptel.ac.in/courses"
        private const val NPTEL_USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
        private val CREDIT_REGEX = Regex("\\b(\\d{1,2})\\s*(credit|credits|cr)\\b", RegexOption.IGNORE_CASE)
        private val PROFESSOR_REGEX = Regex("""(?:[Bb]y\s+)?(?:Prof\.?|Professor)\s+([A-Z][A-Za-z .]{2,35})(?=\s*[|,]|\s+(?:IIT|NIT|BITS|IISER|TIFR|ISI|IISc|University|College))""")
        private val SCORE_NUMBER_REGEX = Regex("\\d+")
        private val NON_ALPHANUMERIC_REGEX = Regex("[^a-z0-9]+")
        private val MULTIPLE_SPACES_REGEX = Regex("\\s+")
        private val UNSAFE_FILENAME_CHARS_REGEX = Regex("[^A-Za-z0-9._-]")
        private val UNSAFE_PATH_CHARS_REGEX = Regex("[^a-z0-9]+")
        private val IGNORED_QUERY_WORDS = setOf("a", "an", "and", "by", "for", "in", "of", "on", "the", "to", "with")
        private val SUPPORTED_DOCUMENT_TYPES = arrayOf(PDF_MIME_TYPE, "image/*")
        private val SUPPORTED_DOCUMENT_EXTENSIONS = setOf(".pdf", ".jpg", ".jpeg", ".png", ".webp")
        private val nptelCatalogLock = Any()

        @Volatile
        private var cachedNptelCourses: List<NptelCourse>? = null

        @Volatile
        private var isNptelCatalogLoading = false

        fun createIntent(context: Context, claimType: String, formTitle: String): Intent {
            return Intent(context, NptelFormActivity::class.java).apply {
                putExtra(EXTRA_CLAIM_TYPE, claimType)
                putExtra(EXTRA_FORM_TITLE, formTitle)
            }
        }

        private data class UploadedDocument(
            val publicUrl: String,
            val storagePath: String
        )

        private data class NptelCourse(
            val name: String,
            val courseId: String,
            val professorName: String,
            val credits: Int?,
            val detailUrl: String,
            val normalizedName: String
        )

        private data class RankedCourseSuggestion(
            val course: NptelCourse,
            val exactPhraseMatch: Boolean,
            val startsWithPhrase: Boolean,
            val containsPhrase: Boolean,
            val matchesAllQueryWords: Boolean,
            val matchingWordCount: Int
        )
    }
}
