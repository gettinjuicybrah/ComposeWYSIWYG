package com.joeybasile.composewysiwyg

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform