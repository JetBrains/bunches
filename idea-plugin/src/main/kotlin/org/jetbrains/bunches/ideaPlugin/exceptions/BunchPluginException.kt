package org.jetbrains.bunches.ideaPlugin.exceptions

open class BunchPluginException(msg: String) : Exception(msg)
class BaseDirNotFoundPluginException() : BunchPluginException("Base dir not found.")
class BunchFileNotFoundPluginException() : BunchPluginException(".bunch file not found.")
class BunchFileDoesNotExistsPluginException() : BunchPluginException(".bunch file does not exists.")
class BunchFileFormatPluginException() : BunchPluginException(".bunch file format is incorrect.")