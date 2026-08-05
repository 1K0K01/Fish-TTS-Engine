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

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SecurePrefs

    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = SecurePrefs(this)

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

        binding.btnClearCache.setOnClickListener {
            PcmCache(this).clear()
            Toast.makeText(this, "캐시 삭제 완료", Toast.LENGTH_SHORT).show()
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

        renderVoices()
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
            }
            container.addView(emptyView)
            return
        }

        val defaultId = prefs.defaultVoiceId

        for (voice in voices) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPadding(0, dp(4), 0, dp(4))
            }

            val label = TextView(this).apply {
                text = "${voice.name}\n${voice.modelId}"
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            val defaultButton = Button(this).apply {
                text = if (voice.modelId == defaultId) "기본" else "기본으로"
                isEnabled = voice.modelId != defaultId

                setOnClickListener {
                    prefs.setDefaultVoice(voice.modelId)
                    renderVoices()
                }
            }

            val deleteButton = Button(this).apply {
                text = "삭제"

                setOnClickListener {
                    prefs.deleteVoice(voice.modelId)
                    renderVoices()
                    Toast.makeText(this@MainActivity, "보이스 삭제 완료", Toast.LENGTH_SHORT).show()
                }
            }

            row.addView(label)
            row.addView(defaultButton)
            row.addView(deleteButton)

            container.addView(row)
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
                tts?.setLanguage(locale)

                val voiceName = prefs.defaultVoice()?.name
                if (voiceName != null) {
                    tts?.setVoice(voiceName)
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
