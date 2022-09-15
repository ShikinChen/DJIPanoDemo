package me.shiki.djipanodemo

/**
 *
 * @author shiki
 * @date 2022/9/14
 *
 */
class OpenCVUtils private constructor() {
    companion object {
        init {
            System.loadLibrary("opencv_utils")
        }

        @JvmStatic
        private external fun native_OpenCVVersion(): String

        @JvmStatic
        private external fun native_Stitch(
            imgPathList: Array<String>,
            outFilePath: String,
            width: Int,
            isCrop: Boolean
        ): Boolean

        @JvmStatic
        fun openCVVersion(): String {
            return native_OpenCVVersion()
        }

        @JvmStatic
        fun stitch(imgPathList: List<String>, outFilePath: String, width: Int = 1080, isCrop: Boolean = true): Boolean {
            return native_Stitch(imgPathList.toTypedArray(), outFilePath, width, isCrop)
        }
    }

}