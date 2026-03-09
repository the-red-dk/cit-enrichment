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
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

class NptelFormActivity : AppCompatActivity() {
    private val registerNumberPattern = Regex("^[A-Za-z0-9/-]{4,20}$")

    private var certificateUri: Uri? = null
    private var paymentUri: Uri? = null

    private lateinit var progressBar: ProgressBar
    private lateinit var txtCertificateName: TextView
    private lateinit var txtPaymentName: TextView
    private lateinit var txtFormTitle: TextView
    private lateinit var txtUploadStatus: TextView
    private lateinit var formScrollView: ScrollView
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
        txtPaymentName = findViewById(R.id.txtPaymentName)
        txtFormTitle = findViewById(R.id.txtFormTitle)
        txtUploadStatus = findViewById(R.id.txtUploadStatus)
        formScrollView = findViewById(R.id.scrollNptelForm)
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
        bindEnterToNext(inputRegisterNumber, inputDepartment)
        bindEnterToNext(inputYear, inputCourseName)
        bindEnterToNext(inputCourseName, inputCourseId)
        bindEnterToNext(inputExamDate, inputScore)
        bindEnterToDatePicker(inputCourseId)

        inputRemarks.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitForm()
                true
            } else {
                false
            }
        }
        inputRemarks.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                submitForm()
                true
            } else {
                false
            }
        }

        if (claimType != getString(R.string.apply_nptel)) {
            inputCourseName.hint = getString(R.string.label_claim_title)
            inputCourseId.hint = getString(R.string.label_reference_id)
            inputExamDate.hint = getString(R.string.label_claim_date)
            inputScore.hint = getString(R.string.label_outcome)
            amountPaidLayout.visibility = View.VISIBLE
            bindEnterToNext(inputScore, inputAmountPaid)
            bindEnterToNext(inputAmountPaid, inputRemarks)
        } else {
            amountPaidLayout.visibility = View.GONE
            bindEnterToNext(inputScore, inputRemarks)
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
            layoutCourseId.error = getString(R.string.error_fill_required_fields)
            firstInvalidView = firstInvalidView ?: inputCourseId
        }
        if (claimDate.isEmpty()) {
            layoutExamDate.error = getString(R.string.error_claim_date_required)
            firstInvalidView = firstInvalidView ?: inputExamDate
        }
        if (score.isEmpty()) {
            layoutScore.error = getString(R.string.error_fill_required_fields)
            firstInvalidView = firstInvalidView ?: inputScore
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
        updateStatus(getString(R.string.status_uploading_certificate))
        uploadDocument(
            uri = certificateUri!!,
            fileName = certificateName,
            failurePrefix = getString(R.string.error_certificate_upload_failed)
        ) { certificateUpload ->
            updateStatus(getString(R.string.status_uploading_payment))
            uploadDocument(
                uri = paymentUri!!,
                fileName = paymentName,
                failurePrefix = getString(R.string.error_payment_upload_failed)
            ) { paymentUpload ->
                updateStatus(getString(R.string.status_saving_claim))
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
                    "examDate" to claimDate,
                    "score" to score,
                    "amountPaid" to amountPaid,
                    "remarks" to inputRemarks.text?.toString()?.trim().orEmpty(),
                    "certificateUrl" to certificateUpload.secureUrl,
                    "paymentUrl" to paymentUpload.secureUrl,
                    "certificateStoragePath" to certificateUpload.publicId,
                    "paymentStoragePath" to paymentUpload.publicId,
                    "certificateFileName" to certificateName,
                    "paymentFileName" to paymentName,
                    "status" to getString(R.string.claim_status_pending),
                    "submittedAt" to FieldValue.serverTimestamp()
                )

                docRef.set(data)
                    .also { task ->
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (!task.isComplete) {
                                setLoading(false, getString(R.string.error_claim_save_taking_long), keepStatusVisible = true)
                            }
                        }, CLAIM_SAVE_TIMEOUT_MS)
                    }
                    .addOnSuccessListener {
                        updateStatus(getString(R.string.status_claim_submitted))
                        Toast.makeText(this, getString(R.string.message_claim_submitted, claimType), Toast.LENGTH_SHORT).show()
                        setLoading(false, getString(R.string.status_claim_submitted), keepStatusVisible = true)
                        startActivity(Intent(this, ClaimStatusActivity::class.java))
                        finish()
                    }
                    .addOnFailureListener { error ->
                        val message = "Save failed: ${error.message}"
                        setLoading(false, message, keepStatusVisible = true)
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    }
            }
        }
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
        onSuccess: (UploadedDocument) -> Unit
    ) {
        Thread {
            try {
                val uploadResult = performCloudinaryUpload(uri, fileName)
                runOnUiThread {
                    onSuccess(uploadResult)
                }
            } catch (error: Exception) {
                runOnUiThread {
                    val message = "$failurePrefix: ${error.message}"
                    setLoading(false, message, keepStatusVisible = true)
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun performCloudinaryUpload(uri: Uri, fileName: String): UploadedDocument {
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

        val boundary = "Boundary-${UUID.randomUUID()}"
        val lineEnd = "\r\n"
        val mimeType = contentResolver.getType(uri).orEmpty().ifBlank { DEFAULT_UPLOAD_MIME_TYPE }
        val connection = (URL(cloudinaryUploadUrl()).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = NETWORK_TIMEOUT_MS
            readTimeout = NETWORK_TIMEOUT_MS
            doInput = true
            doOutput = true
            useCaches = false
            setChunkedStreamingMode(0)
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }

        try {
            DataOutputStream(connection.outputStream).use { output ->
                writeFormField(output, boundary, "upload_preset", CLOUDINARY_UPLOAD_PRESET)
                writeFormField(output, boundary, "folder", CLOUDINARY_FOLDER)
                writeFileField(output, boundary, "file", fileName, mimeType, inputStream)
                output.writeBytes("--$boundary--$lineEnd")
                output.flush()
            }

            val responseCode = connection.responseCode
            val responseText = readResponseText(
                if (responseCode in 200..299) connection.inputStream else connection.errorStream
            )
            val json = JSONObject(responseText)

            if (responseCode !in 200..299) {
                val message = json.optJSONObject("error")?.optString("message")
                    ?: json.optString("message")
                    ?: "HTTP $responseCode"
                throw IllegalStateException(message)
            }

            val secureUrl = json.optString("secure_url")
            val publicId = json.optString("public_id")
            if (secureUrl.isBlank() || publicId.isBlank()) {
                throw IllegalStateException("Cloudinary upload response was incomplete.")
            }

            return UploadedDocument(
                secureUrl = secureUrl,
                publicId = publicId
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

    private fun writeFormField(output: DataOutputStream, boundary: String, name: String, value: String) {
        val lineEnd = "\r\n"
        output.writeBytes("--$boundary$lineEnd")
        output.writeBytes("Content-Disposition: form-data; name=\"$name\"$lineEnd$lineEnd")
        output.writeBytes(value)
        output.writeBytes(lineEnd)
    }

    private fun writeFileField(
        output: DataOutputStream,
        boundary: String,
        fieldName: String,
        fileName: String,
        mimeType: String,
        inputStream: java.io.InputStream
    ) {
        val lineEnd = "\r\n"
        output.writeBytes("--$boundary$lineEnd")
        output.writeBytes(
            "Content-Disposition: form-data; name=\"$fieldName\"; filename=\"$fileName\"$lineEnd"
        )
        output.writeBytes("Content-Type: $mimeType$lineEnd$lineEnd")

        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var bytesRead = inputStream.read(buffer)
        while (bytesRead != -1) {
            output.write(buffer, 0, bytesRead)
            bytesRead = inputStream.read(buffer)
        }
        output.writeBytes(lineEnd)
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

    private fun cloudinaryUploadUrl(): String {
        return "https://api.cloudinary.com/v1_1/$CLOUDINARY_CLOUD_NAME/auto/upload"
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
        currentField.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_ACTION_DONE) {
                showExamDatePicker()
                true
            } else {
                false
            }
        }
        currentField.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                showExamDatePicker()
                true
            } else {
                false
            }
        }
    }

    private fun bindEnterToNext(currentField: TextView, nextField: View) {
        currentField.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_ACTION_DONE) {
                nextField.requestFocus()
                true
            } else {
                false
            }
        }
        currentField.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN) {
                nextField.requestFocus()
                true
            } else {
                false
            }
        }
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
        private const val CLAIM_SAVE_TIMEOUT_MS = 15000L
        private const val NETWORK_TIMEOUT_MS = 20000
        private const val CLOUDINARY_CLOUD_NAME = "dnjaiy2cv"
        private const val CLOUDINARY_UPLOAD_PRESET = "cit_claim_updates"
        private const val CLOUDINARY_FOLDER = "cit-student-enrichment/claims"
        private const val DEFAULT_UPLOAD_MIME_TYPE = "application/octet-stream"
        private const val PDF_MIME_TYPE = "application/pdf"
        private const val IMAGE_MIME_PREFIX = "image/"
        private const val MAX_DOCUMENT_SIZE_MB = 10
        private const val MAX_DOCUMENT_SIZE_BYTES = MAX_DOCUMENT_SIZE_MB * 1024 * 1024L
        private const val EXTRA_CLAIM_TYPE = "extra_claim_type"
        private const val EXTRA_FORM_TITLE = "extra_form_title"
        private val SUPPORTED_DOCUMENT_TYPES = arrayOf(PDF_MIME_TYPE, "image/*")
        private val SUPPORTED_DOCUMENT_EXTENSIONS = setOf(".pdf", ".jpg", ".jpeg", ".png", ".webp")

        fun createIntent(context: Context, claimType: String, formTitle: String): Intent {
            return Intent(context, NptelFormActivity::class.java).apply {
                putExtra(EXTRA_CLAIM_TYPE, claimType)
                putExtra(EXTRA_FORM_TITLE, formTitle)
            }
        }

        private data class UploadedDocument(
            val secureUrl: String,
            val publicId: String
        )
    }
}
