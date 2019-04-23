package org.jetbrains.bunches

open class BunchException(msg: String? = null) : Exception(msg)
open class BunchParametersException(msg: String) : Exception(msg)

class BunchSwitchException(msg: String?) : BunchException(msg)
class BunchSwitchParametersException(msg: String) : BunchParametersException(msg)
