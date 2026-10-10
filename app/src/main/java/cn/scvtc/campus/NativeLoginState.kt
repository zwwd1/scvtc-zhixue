package cn.scvtc.campus

/** Contains UI state only. Passwords never enter observable or saved UI state. */
data class NativeLoginState(
    val account:String="",
    val busy:Boolean=false,
    val verified:Boolean=false,
    val requiresVerification:Boolean=false,
    val message:String=""
)
