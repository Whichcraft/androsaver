package com.androsaver.visualizer

/** Range-preserving bilinear interpolation, matching Effect._resize_field. */
internal object FieldResize {
    fun resize(source:FloatArray,oldW:Int,oldH:Int,newW:Int,newH:Int):FloatArray {
        require(oldW>0&&oldH>0&&newW>0&&newH>0&&source.size==oldW*oldH)
        return FloatArray(newW*newH){i ->
            val x=(i%newW).toFloat()/(newW-1).coerceAtLeast(1)*(oldW-1)
            val y=(i/newW).toFloat()/(newH-1).coerceAtLeast(1)*(oldH-1)
            val x0=x.toInt();val y0=y.toInt();val x1=(x0+1).coerceAtMost(oldW-1);val y1=(y0+1).coerceAtMost(oldH-1)
            val tx=x-x0;val ty=y-y0
            (source[y0*oldW+x0]*(1f-tx)+source[y0*oldW+x1]*tx)*(1f-ty)+(source[y1*oldW+x0]*(1f-tx)+source[y1*oldW+x1]*tx)*ty
        }
    }
}
