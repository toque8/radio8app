package app.radio8

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private var audioService: AudioService? = null
    private var isBound = false
    
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as AudioService.LocalBinder
            audioService = binder.getService()
            isBound = true
        }
        
        override fun onServiceDisconnected(arg0: ComponentName) {
            isBound = false
            audioService = null
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        val intent = Intent(this, AudioService::class.java)
        startService(intent)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
        
        findViewById<Button>(R.id.play_btn).setOnClickListener {
            audioService?.playRandomTrack() ?: run {
                Toast.makeText(this, "Service not connected", Toast.LENGTH_SHORT).show()
            }
        }
        
        findViewById<Button>(R.id.stop_btn).setOnClickListener {
            audioService?.pauseResumePlayback() ?: run {
                Toast.makeText(this, "Service not connected", Toast.LENGTH_SHORT).show()
            }
        }
        
        findViewById<Button>(R.id.next_btn).setOnClickListener {
            audioService?.nextTrack() ?: run {
                Toast.makeText(this, "Service not connected", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }
}
