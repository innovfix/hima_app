package com.gmwapp.hima.dialogs

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.gmwapp.hima.R
import com.gmwapp.hima.databinding.DialogFeedbackFormBinding
import com.gmwapp.hima.databinding.ItemFeedbackQuestionMultiBinding
import com.gmwapp.hima.databinding.ItemFeedbackQuestionRatingBinding
import com.gmwapp.hima.databinding.ItemFeedbackQuestionSingleBinding
import com.gmwapp.hima.databinding.ItemFeedbackQuestionTextBinding
import com.gmwapp.hima.retrofit.ApiManager
import com.gmwapp.hima.retrofit.callbacks.NetworkCallback
import com.gmwapp.hima.retrofit.responses.FeedbackErrorResponse
import com.gmwapp.hima.retrofit.responses.FeedbackFormData
import com.gmwapp.hima.retrofit.responses.FeedbackQuestion
import com.gmwapp.hima.retrofit.responses.FeedbackQuestionType
import com.gmwapp.hima.retrofit.responses.FeedbackSubmitResponse
import com.gmwapp.hima.utils.showAppToast
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import retrofit2.Call
import retrofit2.Response

class FeedbackFormDialog(
    private val context: Context,
    private val form: FeedbackFormData,
    private val apiManager: ApiManager,
    private val userId: Int,
    private val onTerminal: (() -> Unit)? = null,
) {
    private var dialog: Dialog? = null
    private lateinit var binding: DialogFeedbackFormBinding
    private val handles = LinkedHashMap<Int, AnswerHandle>()
    private val gson = Gson()
    private var submitting = false
    private var terminalFired = false

    private interface AnswerHandle {
        val rowView: View
        fun getValue(): Any?
        fun isAnswered(): Boolean
        fun showError(message: String?)
    }

    fun show() {
        // Defensive: an admin-misconfigured empty form should not be re-served forever.
        // info.md §"Empty form": call /skip immediately so the target row settles.
        if (form.questions.isEmpty()) {
            apiManager.skipFeedback(userId, form.id, object : NetworkCallback<FeedbackSubmitResponse> {
                override fun onResponse(call: Call<FeedbackSubmitResponse>, response: Response<FeedbackSubmitResponse>) {}
                override fun onFailure(call: Call<FeedbackSubmitResponse>, t: Throwable) {}
                override fun onNoNetwork() {}
            })
            fireTerminal()
            return
        }

        dialog = Dialog(context)
        dialog?.requestWindowFeature(Window.FEATURE_NO_TITLE)
        binding = DialogFeedbackFormBinding.inflate(LayoutInflater.from(context))
        dialog?.setContentView(binding.root)
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        if (form.allowSkip) {
            dialog?.setCancelable(true)
            dialog?.setCanceledOnTouchOutside(true)
        } else {
            dialog?.setCancelable(false)
            dialog?.setCanceledOnTouchOutside(false)
        }
        dialog?.setOnDismissListener { fireTerminal() }

        binding.tvTitle.text = form.title
        if (form.description.isNullOrBlank()) {
            binding.tvDescription.visibility = View.GONE
        } else {
            binding.tvDescription.visibility = View.VISIBLE
            binding.tvDescription.text = form.description
        }
        binding.btnSkip.visibility = if (form.allowSkip) View.VISIBLE else View.GONE
        binding.ivClose.visibility = if (form.allowSkip) View.VISIBLE else View.GONE

        // Server returns questions ordered by sort_order (info.md §1) — render in array order.
        form.questions.forEachIndexed { index, q -> inflateQuestion(index + 1, q) }

        binding.ivClose.setOnClickListener { onSkipPressed() }
        binding.btnSkip.setOnClickListener { onSkipPressed() }
        binding.btnSubmit.setOnClickListener { onSubmitPressed() }

        // Cap the scrollable area so long forms scroll instead of pushing the
        // action buttons below the screen.
        val maxScrollHeight = (context.resources.displayMetrics.heightPixels * 0.55f).toInt()
        binding.scrollQuestions.post {
            if (binding.scrollQuestions.height > maxScrollHeight) {
                val lp = binding.scrollQuestions.layoutParams
                lp.height = maxScrollHeight
                binding.scrollQuestions.layoutParams = lp
            }
        }

        dialog?.show()
    }

    private fun inflateQuestion(displayIndex: Int, q: FeedbackQuestion) {
        val container = binding.questionContainer
        val inflater = LayoutInflater.from(context)
        val handle: AnswerHandle = when (q.questionType) {
            FeedbackQuestionType.RATING -> buildRatingRow(displayIndex, q, inflater, container)
            FeedbackQuestionType.TEXT -> buildTextRow(displayIndex, q, inflater, container)
            FeedbackQuestionType.SINGLE -> buildSingleRow(displayIndex, q, inflater, container)
            FeedbackQuestionType.MULTI -> buildMultiRow(displayIndex, q, inflater, container)
            else -> buildTextRow(displayIndex, q, inflater, container) // unknown type → fall back to text
        }
        container.addView(handle.rowView)
        handles[q.id] = handle
    }

    private fun buildHeader(displayIndex: Int, q: FeedbackQuestion): CharSequence {
        val base = "$displayIndex. ${q.questionText}"
        if (!q.isRequired) return base
        val builder = SpannableStringBuilder(base).append(" *")
        val redStart = builder.length - 1
        builder.setSpan(
            ForegroundColorSpan(Color.RED),
            redStart,
            builder.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        return builder
    }

    private fun applyHelpText(view: TextView, helpText: String?) {
        if (helpText.isNullOrBlank()) {
            view.visibility = View.GONE
        } else {
            view.visibility = View.VISIBLE
            view.text = helpText
        }
    }

    private fun bindError(errorView: TextView, message: String?) {
        if (message.isNullOrBlank()) {
            errorView.visibility = View.GONE
            errorView.text = null
        } else {
            errorView.visibility = View.VISIBLE
            errorView.text = message
        }
    }

    private fun buildRatingRow(
        displayIndex: Int,
        q: FeedbackQuestion,
        inflater: LayoutInflater,
        parent: ViewGroup,
    ): AnswerHandle {
        val itemBinding = ItemFeedbackQuestionRatingBinding.inflate(inflater, parent, false)
        itemBinding.tvQuestion.text = buildHeader(displayIndex, q)
        applyHelpText(itemBinding.tvHelpText, q.helpText)

        val min = q.minRating ?: 1
        val max = q.maxRating ?: 5
        val starCount = (max - min + 1).coerceAtLeast(1)
        val density = context.resources.displayMetrics.density
        val starSize = (36 * density).toInt()
        val starMargin = (4 * density).toInt()

        val stars = mutableListOf<ImageView>()
        var selected = 0

        fun render() {
            stars.forEachIndexed { idx, iv ->
                if (idx < selected) {
                    iv.setImageResource(R.drawable.ic_star_filled)
                    iv.setColorFilter(Color.parseColor("#FFD700"), android.graphics.PorterDuff.Mode.SRC_IN)
                } else {
                    iv.setImageResource(R.drawable.ic_star_empty)
                    iv.clearColorFilter()
                }
            }
        }

        for (i in 0 until starCount) {
            val iv = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(starSize, starSize).also {
                    it.marginEnd = starMargin
                }
                setImageResource(R.drawable.ic_star_empty)
                contentDescription = "Star ${i + 1}"
                setOnClickListener {
                    selected = i + 1
                    bindError(itemBinding.tvError, null)
                    render()
                }
            }
            stars.add(iv)
            itemBinding.starRow.addView(iv)
        }

        return object : AnswerHandle {
            override val rowView: View = itemBinding.root
            override fun getValue(): Any? = if (selected == 0) null else (min + selected - 1)
            override fun isAnswered(): Boolean = selected > 0
            override fun showError(message: String?) = bindError(itemBinding.tvError, message)
        }
    }

    private fun buildTextRow(
        displayIndex: Int,
        q: FeedbackQuestion,
        inflater: LayoutInflater,
        parent: ViewGroup,
    ): AnswerHandle {
        val itemBinding = ItemFeedbackQuestionTextBinding.inflate(inflater, parent, false)
        itemBinding.tvQuestion.text = buildHeader(displayIndex, q)
        applyHelpText(itemBinding.tvHelpText, q.helpText)

        val maxLen = q.maxLength
        if (maxLen != null && maxLen > 0) {
            itemBinding.tilAnswer.counterMaxLength = maxLen
            itemBinding.tilAnswer.isCounterEnabled = true
        }
        itemBinding.etAnswer.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (maxLen != null && (s?.length ?: 0) > maxLen) {
                    itemBinding.tilAnswer.error = "Maximum $maxLen characters"
                } else {
                    itemBinding.tilAnswer.error = null
                }
                if (!s.isNullOrBlank()) bindError(itemBinding.tvError, null)
            }
        })

        return object : AnswerHandle {
            override val rowView: View = itemBinding.root
            override fun getValue(): Any? = itemBinding.etAnswer.text?.toString()?.trim()
            override fun isAnswered(): Boolean = !itemBinding.etAnswer.text.isNullOrBlank()
            override fun showError(message: String?) = bindError(itemBinding.tvError, message)
        }
    }

    private fun buildSingleRow(
        displayIndex: Int,
        q: FeedbackQuestion,
        inflater: LayoutInflater,
        parent: ViewGroup,
    ): AnswerHandle {
        val itemBinding = ItemFeedbackQuestionSingleBinding.inflate(inflater, parent, false)
        itemBinding.tvQuestion.text = buildHeader(displayIndex, q)
        applyHelpText(itemBinding.tvHelpText, q.helpText)

        q.options.orEmpty().forEach { label ->
            val rb = RadioButton(context).apply {
                id = View.generateViewId()
                text = label
                tag = label
                setTextColor(ContextCompat.getColor(context, android.R.color.black))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            itemBinding.rgOptions.addView(rb)
        }
        itemBinding.rgOptions.setOnCheckedChangeListener { _, _ ->
            bindError(itemBinding.tvError, null)
        }

        return object : AnswerHandle {
            override val rowView: View = itemBinding.root
            override fun getValue(): Any? {
                val checkedId = itemBinding.rgOptions.checkedRadioButtonId
                if (checkedId == -1) return null
                val rb = itemBinding.rgOptions.findViewById<RadioButton>(checkedId) ?: return null
                return rb.tag as? String
            }
            override fun isAnswered(): Boolean = itemBinding.rgOptions.checkedRadioButtonId != -1
            override fun showError(message: String?) = bindError(itemBinding.tvError, message)
        }
    }

    private fun buildMultiRow(
        displayIndex: Int,
        q: FeedbackQuestion,
        inflater: LayoutInflater,
        parent: ViewGroup,
    ): AnswerHandle {
        val itemBinding = ItemFeedbackQuestionMultiBinding.inflate(inflater, parent, false)
        itemBinding.tvQuestion.text = buildHeader(displayIndex, q)
        applyHelpText(itemBinding.tvHelpText, q.helpText)

        val checkBoxes = mutableListOf<CheckBox>()
        q.options.orEmpty().forEach { label ->
            val cb = CheckBox(context).apply {
                text = label
                tag = label
                setTextColor(ContextCompat.getColor(context, android.R.color.black))
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setOnCheckedChangeListener { _, _ -> bindError(itemBinding.tvError, null) }
            }
            checkBoxes.add(cb)
            itemBinding.optionsContainer.addView(cb)
        }

        return object : AnswerHandle {
            override val rowView: View = itemBinding.root
            override fun getValue(): Any = checkBoxes.filter { it.isChecked }.map { it.tag as String }
            override fun isAnswered(): Boolean = checkBoxes.any { it.isChecked }
            override fun showError(message: String?) = bindError(itemBinding.tvError, message)
        }
    }

    private fun onSubmitPressed() {
        if (submitting) return

        // Client-side required check (info.md §"Edge cases" — Validation error keeps dialog open).
        var firstOffender: View? = null
        form.questions.forEach { q ->
            val handle = handles[q.id] ?: return@forEach
            if (q.isRequired && !handle.isAnswered()) {
                handle.showError("Required")
                if (firstOffender == null) firstOffender = handle.rowView
            } else {
                handle.showError(null)
            }
        }
        if (firstOffender != null) {
            context.showAppToast("Please answer all required questions", Toast.LENGTH_SHORT)
            firstOffender?.let { v ->
                binding.scrollQuestions.post { binding.scrollQuestions.smoothScrollTo(0, v.top) }
            }
            return
        }

        // Build answers per info.md §2 — exactly one value field per entry.
        val answersArray = JsonArray()
        form.questions.forEach { q ->
            val handle = handles[q.id] ?: return@forEach
            val v = handle.getValue() ?: return@forEach
            val obj = JsonObject().apply {
                addProperty("question_id", q.id)
                when (q.questionType) {
                    FeedbackQuestionType.RATING -> if (v is Int && v > 0) addProperty("answer_rating", v)
                    FeedbackQuestionType.TEXT -> if (v is String && v.isNotBlank()) addProperty("answer_text", v)
                    FeedbackQuestionType.SINGLE -> if (v is String) add("answer_options", gson.toJsonTree(listOf(v)))
                    FeedbackQuestionType.MULTI -> {
                        @Suppress("UNCHECKED_CAST")
                        val list = v as? List<String>
                        if (!list.isNullOrEmpty()) add("answer_options", gson.toJsonTree(list))
                    }
                }
            }
            // Only send entries that actually carry a value field.
            if (obj.size() > 1) answersArray.add(obj)
        }

        val answersJson = gson.toJson(answersArray)
        submitting = true
        binding.btnSubmit.isEnabled = false
        binding.btnSkip.isEnabled = false

        apiManager.submitFeedback(userId, form.id, answersJson, object : NetworkCallback<FeedbackSubmitResponse> {
            override fun onResponse(
                call: Call<FeedbackSubmitResponse>,
                response: Response<FeedbackSubmitResponse>
            ) {
                submitting = false
                Log.d(TAG, "submit: code=${response.code()} body=${response.body()}")

                when {
                    response.isSuccessful && response.body()?.success == true -> {
                        context.showAppToast(
                            response.body()?.message ?: "Thanks for your feedback",
                            Toast.LENGTH_SHORT
                        )
                        dismiss() // fires onTerminal via OnDismissListener
                    }

                    // Per-question validation — info.md §2 "Validation error".
                    response.code() == 400 -> {
                        val parsed = parseErrorBody(response)
                        val perQuestion = parsed?.errors
                        if (!perQuestion.isNullOrEmpty()) {
                            applyServerErrors(perQuestion)
                        } else {
                            context.showAppToast(
                                parsed?.message ?: "Invalid request",
                                Toast.LENGTH_SHORT
                            )
                        }
                        binding.btnSubmit.isEnabled = true
                        binding.btnSkip.isEnabled = true
                    }

                    // 409 already submitted/skipped, 404 target/form not found — terminal.
                    response.code() == 409 || response.code() == 404 -> {
                        Log.d(TAG, "submit terminal http ${response.code()}")
                        dismiss()
                    }

                    else -> {
                        Log.e(TAG, "submit unexpected code=${response.code()}")
                        context.showAppToast("Try again later", Toast.LENGTH_SHORT)
                        binding.btnSubmit.isEnabled = true
                        binding.btnSkip.isEnabled = true
                    }
                }
            }

            override fun onFailure(call: Call<FeedbackSubmitResponse>, t: Throwable) {
                submitting = false
                binding.btnSubmit.isEnabled = true
                binding.btnSkip.isEnabled = true
                Log.e(TAG, "submit failed", t)
                context.showAppToast("Try again later", Toast.LENGTH_SHORT)
            }

            override fun onNoNetwork() {
                submitting = false
                binding.btnSubmit.isEnabled = true
                binding.btnSkip.isEnabled = true
                Log.w(TAG, "submit no network")
                context.showAppToast("No internet connection", Toast.LENGTH_SHORT)
            }
        })
    }

    private fun parseErrorBody(response: Response<*>): FeedbackErrorResponse? {
        val raw = try {
            response.errorBody()?.string()
        } catch (e: Exception) {
            Log.e(TAG, "errorBody read failed", e)
            null
        } ?: return null
        return try {
            gson.fromJson(raw, FeedbackErrorResponse::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "errorBody parse failed: $raw", e)
            null
        }
    }

    private fun applyServerErrors(errors: Map<String, String>) {
        var firstOffender: View? = null
        // Clear any prior errors that aren't in this response.
        handles.forEach { (qId, handle) ->
            val err = errors[qId.toString()]
            handle.showError(err)
            if (err != null && firstOffender == null) firstOffender = handle.rowView
        }
        firstOffender?.let { v ->
            binding.scrollQuestions.post { binding.scrollQuestions.smoothScrollTo(0, v.top) }
        }
    }

    private fun onSkipPressed() {
        if (!form.allowSkip) return // buttons should be hidden; defense in depth.
        // Per info.md §3, /skip is idempotent. Dismiss immediately regardless of network outcome.
        apiManager.skipFeedback(userId, form.id, object : NetworkCallback<FeedbackSubmitResponse> {
            override fun onResponse(call: Call<FeedbackSubmitResponse>, response: Response<FeedbackSubmitResponse>) {
                Log.d(TAG, "skip: code=${response.code()} body=${response.body()}")
            }
            override fun onFailure(call: Call<FeedbackSubmitResponse>, t: Throwable) {
                Log.e(TAG, "skip failed", t)
            }
            override fun onNoNetwork() {
                Log.w(TAG, "skip no network")
            }
        })
        dismiss()
    }

    fun dismiss() {
        dialog?.dismiss()
        dialog = null
    }

    private fun fireTerminal() {
        if (terminalFired) return
        terminalFired = true
        onTerminal?.invoke()
    }

    companion object {
        private const val TAG = "FeedbackFormDialog"
    }
}
