package com.chinaway.android.myapplication

import android.content.Intent

interface IPushAction {

    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean

    fun toggle() : Boolean?
}