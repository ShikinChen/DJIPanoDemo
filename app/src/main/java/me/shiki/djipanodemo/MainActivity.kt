package me.shiki.djipanodemo

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import me.shiki.djipanodemo.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Example of a call to a native method
        binding.sampleText.text = opencvVersion()
    }

    /**
     * A native method that is implemented by the 'djipanodemo' native library,
     * which is packaged with this application.
     */
    external fun opencvVersion(): String

    companion object {
        // Used to load the 'djipanodemo' library on application startup.
        init {
            System.loadLibrary("djipanodemo")
        }
    }
}