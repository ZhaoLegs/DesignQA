package android.content
class Context { val resources = Resources() }
class Resources { val displayMetrics = DisplayMetrics() }
class DisplayMetrics { val density = 1f }
interface SharedPreferences {
    fun getString(key: String, default: String?): String?
    fun getBoolean(key: String, default: Boolean): Boolean
    fun edit(): Editor
    interface Editor {
        fun putString(key: String, value: String?): Editor
        fun putBoolean(key: String, value: Boolean): Editor
        fun apply()
    }
}
