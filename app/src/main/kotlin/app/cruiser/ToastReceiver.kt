package app.cruiser

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class ToastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val msg = intent.getStringExtra("msg") ?: "testing cruiser"
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
}
