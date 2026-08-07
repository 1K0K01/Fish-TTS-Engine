package com.example.fishtts

import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.fishtts.databinding.ActivityMainBinding
import android.content.res.ColorStateList
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.button.MaterialButton
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SecurePrefs
    private lateinit var cache: PcmCache

    private var tts: TextToSpeech? = null

    // 캐시를 저장할 외부 폴더를 사용자가 고르는 시스템 폴더 선택기.
    // 여기서 고른 폴더는 앱을 삭제해도 그대로 남아 있습니다.
    private val pickCacheFolder = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            cache.linkFolder(uri)
            updateCacheStatus()
            Toast.makeText(this, "캐시 폴더 연동 완료", Toast.LENGTH_SHORT).show()
        }
    }

    // 설정 백업(JSON) 저장 위치를 고르는 선택기
    private val exportBackup = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(SettingsBackup.export(prefs).toByteArray())
                }
                Toast.makeText(this, "백업 완료", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "백업 실패: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // 백업 파일(JSON)을 고르는 선택기
    private val importBackup = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val text = contentResolver.openInputStream(uri)?.use {
                    it.readBytes().toString(Charsets.UTF_8)
                }
                if (text != null) {
                    SettingsBackup.import(prefs, text)
                    load()
                    Toast.makeText(this, "복원 완료", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "복원 실패: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 엣지투엣지(Android 15+)에서 상태표시줄/제스처바에 콘텐츠가
        // 가려지지 않도록 시스템 바 높이만큼 여백을 줍니다.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }

        prefs = SecurePrefs(this)
        cache = PcmCache(this)

        load()

        binding.btnSave.setOnClickListener {
            save()
            Toast.makeText(this, "설정 저장 완료", Toast.LENGTH_SHORT).show()
        }

        binding.btnOpenTts.setOnClickListener {
            try {
                startActivity(Intent("com.android.settings.TTS_SETTINGS"))
            } catch (e: Exception) {
                Toast.makeText(this, "TTS 설정을 열 수 없습니다", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnTest.setOnClickListener {
            test()
        }

        binding.btnCacheSettings.setOnClickListener {
            showCacheSettingsDialog()
        }

        binding.btnBackupExport.setOnClickListener {
            exportBackup.launch("fish-tts-backup.json")
        }

        binding.btnBackupImport.setOnClickListener {
            importBackup.launch(arrayOf("application/json"))
        }

        binding.btnAddVoice.setOnClickListener {
            addVoice()
        }
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }

    private fun load() {
        binding.etApiKey.setText(prefs.apiKey)
        binding.etEndpoint.setText(prefs.endpoint)
        binding.etModel.setText(prefs.ttsModel)
        binding.etLocale.setText(prefs.localeTag)
        binding.etSampleRate.setText(prefs.sampleRate.toString())
        binding.etFormat.setText(prefs.format)
        binding.etMaxChunk.setText(prefs.maxChunkChars.toString())
        binding.etExtra.setText(prefs.extraBodyJson)
        binding.cbCache.isChecked = prefs.cacheEnabled

        updateCacheStatus()
        renderVoices()
    }

    private fun updateCacheStatus() {
        binding.tvCacheStatus.text = "저장 위치: ${cache.currentStorageLabel()}"
    }

    private fun showCacheSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_cache_settings, null)
        val statusView = dialogView.findViewById<TextView>(R.id.tvCacheStatusDialog)
        val linkButton = dialogView.findViewById<MaterialButton>(R.id.btnLinkCacheFolderDialog)
        val clearButton = dialogView.findViewById<MaterialButton>(R.id.btnClearCacheDialog)

        statusView.text = "저장 위치: ${cache.currentStorageLabel()}"

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("캐시 설정")
            .setView(dialogView)
            .setNegativeButton("닫기", null)
            .create()

        linkButton.setOnClickListener {
            pickCacheFolder.launch(null)
            dialog.dismiss()
        }

        clearButton.setOnClickListener {
            cache.clear()
            Toast.makeText(this, "캐시 삭제 완료", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun save() {
        prefs.apiKey = binding.etApiKey.text.toString().trim()

        prefs.endpoint = binding.etEndpoint.text.toString().trim()
            .ifEmpty { FishApiClient.DEFAULT_ENDPOINT }

        prefs.ttsModel = binding.etModel.text.toString().trim()
            .ifEmpty { SecurePrefs.DEFAULT_TTS_MODEL }

        prefs.localeTag = binding.etLocale.text.toString().trim()
            .ifEmpty { "ko-KR" }

        prefs.sampleRate = binding.etSampleRate.text.toString().toIntOrNull() ?: 44100
        prefs.format = binding.etFormat.text.toString().trim().ifEmpty { "pcm" }
        prefs.maxChunkChars = binding.etMaxChunk.text.toString().toIntOrNull() ?: 900
        prefs.extraBodyJson = binding.etExtra.text.toString().trim()
        prefs.cacheEnabled = binding.cbCache.isChecked
    }

    private fun addVoice() {
        val name = binding.etNewVoiceName.text.toString().trim()
        val id = binding.etNewVoiceId.text.toString().trim()
        val locale = binding.etLocale.text.toString().trim().ifEmpty { "ko-KR" }

        if (prefs.addVoice(name, id, locale)) {
            binding.etNewVoiceName.text.clear()
            binding.etNewVoiceId.text.clear()
            renderVoices()
            Toast.makeText(this, "보이스 추가 완료", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "추가 실패: ID가 비었거나 이미 등록된 ID입니다", Toast.LENGTH_LONG).show()
        }
    }

    private fun renderVoices() {
        val container = binding.voiceList
        container.removeAllViews()

        val voices = prefs.getVoices()

        if (voices.isEmpty()) {
            val emptyView = TextView(this).apply {
                text = "저장된 보이스가 없습니다."
                setTextColor(ContextCompat.getColor(context, R.color.muted_foreground))
                textSize = 13f
                typeface = ResourcesCompat.getFont(context, R.font.pretendard_regular)
                setPadding(0, dp(8), 0, dp(8))
            }
            container.addView(emptyView)
            return
        }

        val defaultId = prefs.defaultVoiceId
        val inflater = layoutInflater

        voices.forEachIndexed { index, voice ->
            val row = inflater.inflate(R.layout.item_voice, container, false)

            val name = row.findViewById<TextView>(R.id.tvVoiceName)
            val idLabel = row.findViewById<TextView>(R.id.tvVoiceId)
            val defaultButton = row.findViewById<MaterialButton>(R.id.btnVoiceDefault)
            val deleteButton = row.findViewById<MaterialButton>(R.id.btnVoiceDelete)

            name.text = voice.name
            idLabel.text = voice.modelId

            val isDefault = voice.modelId == defaultId
            applyDefaultButtonState(defaultButton, isDefault)

            defaultButton.setOnClickListener {
                prefs.setDefaultVoice(voice.modelId)
                renderVoices()
            }

            deleteButton.setOnClickListener {
                prefs.deleteVoice(voice.modelId)
                renderVoices()
                Toast.makeText(this@MainActivity, "보이스 삭제 완료", Toast.LENGTH_SHORT).show()
            }

            container.addView(row)

            // divide-y: 마지막 행 제외하고 구분선 추가
            if (index != voices.lastIndex) {
                val divider = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(1)
                    )
                    setBackgroundColor(ContextCompat.getColor(context, R.color.border))
                }
                container.addView(divider)
            }
        }
    }

    /**
     * 기본 보이스일 때는 shadcn의 Badge(default variant, pill) 형태로,
     * 그렇지 않을 때는 Fish.Button.Small(outline) 형태로 보이도록 전환합니다.
     */
    private fun applyDefaultButtonState(button: MaterialButton, isDefault: Boolean) {
        button.text = if (isDefault) "기본" else "기본으로"
        button.isEnabled = !isDefault

        if (isDefault) {
            button.strokeWidth = 0
            button.cornerRadius = dp(999)
            button.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.foreground)
            )
            button.setTextColor(ContextCompat.getColor(this, R.color.primary_foreground))
            button.typeface = ResourcesCompat.getFont(this, R.font.pretendard_semibold)
        } else {
            button.strokeWidth = dp(1)
            button.strokeColor = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.border)
            )
            button.cornerRadius = dp(8)
            button.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, android.R.color.transparent)
            )
            button.setTextColor(ContextCompat.getColor(this, R.color.foreground))
            button.typeface = ResourcesCompat.getFont(this, R.font.pretendard_medium)
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun test() {
        save()

        tts?.shutdown()

        tts = TextToSpeech(this, { status ->
            if (status == TextToSpeech.SUCCESS) {
                val locale = prefs.defaultVoice()?.locale() ?: prefs.defaultLocale()
                tts?.language = locale

                val voiceName = prefs.defaultVoice()?.name
                if (voiceName != null) {
                    val voice = tts?.voices?.firstOrNull { it.name == voiceName }
                    if (voice != null) {
                        tts?.voice = voice
                    }
                }

                tts?.speak(
                    "Fish Audio 시스템 TTS 테스트입니다.",
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "fish_test"
                )
            } else {
                Toast.makeText(this, "TTS 초기화 실패", Toast.LENGTH_LONG).show()
            }
        }, packageName)
    }
}
