package com.pijlpuzzel.game

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity(), GameView.Listener {

    private lateinit var gameView: GameView
    private lateinit var levelText: TextView
    private lateinit var livesText: TextView
    private lateinit var leftText: TextView
    private lateinit var privacyButton: Button
    private lateinit var ads: AdManager

    private val prefs by lazy { getSharedPreferences("progress", Context.MODE_PRIVATE) }
    private var level = 1
    private var lives = MAX_LIVES
    private var mistakes = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        applySystemBarPadding(findViewById(R.id.root))

        gameView = findViewById(R.id.game_view)
        levelText = findViewById(R.id.level_text)
        livesText = findViewById(R.id.lives_text)
        leftText = findViewById(R.id.left_text)
        privacyButton = findViewById(R.id.privacy_button)
        gameView.listener = this

        findViewById<Button>(R.id.hint_button).setOnClickListener { askHint() }
        findViewById<Button>(R.id.restart_button).setOnClickListener { startLevel(level) }
        privacyButton.setOnClickListener { ads.showPrivacyOptions() }

        ads = AdManager(this, findViewById<FrameLayout>(R.id.banner_container))
        ads.start {
            privacyButton.visibility = if (ads.privacyOptionsRequired) View.VISIBLE else View.GONE
        }

        startLevel(prefs.getInt(KEY_LEVEL, 1))
    }

    /** Vanaf Android 15 tekent de app achter de status- en navigatiebalk; schuif de inhoud daarvan weg. */
    private fun applySystemBarPadding(root: View) {
        root.setOnApplyWindowInsetsListener { v, insets ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
    }

    private fun startLevel(n: Int) {
        level = n
        lives = MAX_LIVES
        mistakes = 0
        prefs.edit().putInt(KEY_LEVEL, level).apply()
        gameView.loadBoard(LevelGenerator.generate(level))
        updateHud()
    }

    private fun updateHud() {
        levelText.text = getString(R.string.level_n, level)
        livesText.text = "♥".repeat(lives) + "♡".repeat(MAX_LIVES - lives)
        leftText.text = getString(R.string.arrows_left, gameView.board?.remaining ?: 0)
    }

    override fun onArrowRemoved(remaining: Int) = updateHud()

    override fun onMistake() {
        lives--
        mistakes++
        updateHud()
        if (lives <= 0) {
            gameView.inputEnabled = false
            showGameOver()
        }
    }

    override fun onCleared() {
        val stars = when {
            mistakes == 0 -> "★★★"
            mistakes == 1 -> "★★☆"
            else -> "★☆☆"
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.level_done, level))
            .setMessage(stars)
            .setCancelable(false)
            .setPositiveButton(R.string.next) { _, _ -> nextLevel() }
            .show()
    }

    private fun nextLevel() {
        val finished = level
        val go = { startLevel(finished + 1) }
        // Tussendoor-advertentie na elke derde level, niet in de eerste levels.
        if (finished >= 3 && finished % 3 == 0) ads.showInterstitial(go) else go()
    }

    private fun showGameOver() {
        val builder = AlertDialog.Builder(this)
            .setTitle(R.string.game_over)
            .setMessage(R.string.game_over_msg)
            .setCancelable(false)
            .setNegativeButton(R.string.retry) { _, _ -> startLevel(level) }
        if (ads.rewardedReady) {
            builder.setPositiveButton(R.string.watch_for_life) { _, _ ->
                ads.showRewarded(
                    onReward = {
                        lives = 1
                        gameView.inputEnabled = true
                        updateHud()
                    },
                    onUnavailable = { startLevel(level) },
                )
            }
        }
        builder.show()
    }

    private fun askHint() {
        if (!ads.rewardedReady) {
            Toast.makeText(this, R.string.no_ad, Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.hint)
            .setMessage(R.string.hint_msg)
            .setPositiveButton(R.string.watch) { _, _ ->
                ads.showRewarded(
                    onReward = { gameView.showHint() },
                    onUnavailable = {
                        Toast.makeText(this, R.string.no_ad, Toast.LENGTH_SHORT).show()
                    },
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onPause() {
        ads.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        ads.resume()
    }

    override fun onDestroy() {
        ads.destroy()
        super.onDestroy()
    }

    companion object {
        private const val MAX_LIVES = 3
        private const val KEY_LEVEL = "level"
    }
}
