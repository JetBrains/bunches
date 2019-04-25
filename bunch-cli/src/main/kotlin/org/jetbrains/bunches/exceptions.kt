package org.jetbrains.bunches

open class BunchException(msg: String? = null) : Exception(msg)
open class BunchParametersException(msg: String) : BunchException(msg)