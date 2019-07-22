package com.example.mediacodecdemo.codec

import android.graphics.Bitmap

/**
 * Created by liangbo.su@ubnt on 2019-07-22
 */
class ColorUtil {
    companion object {
        private fun encodeYUV420SP(yuv420sp: ByteArray, argb: IntArray, width: Int, height: Int) {
            val frameSize = width * height

            var yIndex = 0
            var uvIndex = frameSize

            var R: Int
            var G: Int
            var B: Int
            var Y: Int
            var U: Int
            var V: Int
            var index = 0
            for (j in 0 until height) {
                for (i in 0 until width) {
                    R = argb[index] and 0xff0000 shr 16
                    G = argb[index] and 0xff00 shr 8
                    B = argb[index] and 0xff shr 0

                    // well known RGB to YUV algorithm
                    Y = (66 * R + 129 * G + 25 * B + 128 shr 8) + 16
                    V = (-38 * R - 74 * G + 112 * B + 128 shr 8) + 128 // Previously U
                    U = (112 * R - 94 * G - 18 * B + 128 shr 8) + 128 // Previously V

                    yuv420sp[yIndex++] = (if (Y < 0) 0 else if (Y > 255) 255 else Y).toByte()
                    if (j % 2 == 0 && index % 2 == 0) {
                        yuv420sp[uvIndex++] = (if (V < 0) 0 else if (V > 255) 255 else V).toByte()
                        yuv420sp[uvIndex++] = (if (U < 0) 0 else if (U > 255) 255 else U).toByte()
                    }

                    index++
                }
            }
        }

        fun getNV12(inputWidth: Int, inputHeight: Int, bitmap: Bitmap): ByteArray {

            val argb = IntArray(inputWidth * inputHeight)

            bitmap.getPixels(argb, 0, inputWidth, 0, 0, inputWidth, inputHeight)

            val yuv = ByteArray(inputWidth * inputHeight * 3 / 2)

            encodeYUV420SP(yuv, argb, inputWidth, inputHeight)

            bitmap.recycle()

            return yuv
        }
    }
}