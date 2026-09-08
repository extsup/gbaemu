package com.terra.gba.ui

import android.view.MotionEvent
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.terra.gba.core.GpspCore

private val Ink = Color(0xFF070A12); private val Lime = Color(0xFFB9FF65)
@Composable fun EmulatorScreen() {
    Box(Modifier.fillMaxSize().background(Ink)) {
        AndroidView(factory = { GlesGameSurface(it) }, modifier = Modifier.fillMaxWidth().aspectRatio(1.5f).align(Alignment.Center))
        Surface(color=Color(0xAA101727), shape=RoundedCornerShape(18.dp), modifier=Modifier.padding(20.dp).align(Alignment.TopCenter)) {
            Text("TERRAGBA   •   GPU READY", color=Color(0xFFEAF0FB), style=MaterialTheme.typography.labelMedium, modifier=Modifier.padding(horizontal=14.dp,vertical=8.dp))
        }
        DPad(Modifier.align(Alignment.BottomStart).padding(26.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(14.dp), modifier=Modifier.align(Alignment.BottomEnd).padding(34.dp)) {
            GameButton("B", GpspCore.B, Color(0xFF7764FF)); GameButton("A", GpspCore.A, Lime)
        }
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp), modifier=Modifier.align(Alignment.BottomCenter).padding(bottom=28.dp)) {
            GameButton("SELECT", GpspCore.SELECT, Color(0xFF26334A), small=true); GameButton("START", GpspCore.START, Color(0xFF26334A), small=true)
        }
        Row(horizontalArrangement=Arrangement.SpaceBetween, modifier=Modifier.fillMaxWidth().padding(horizontal=164.dp,vertical=26.dp).align(Alignment.TopCenter)) {
            GameButton("L",GpspCore.L,Color(0xFF1F2B40),small=true); GameButton("R",GpspCore.R,Color(0xFF1F2B40),small=true)
        }
    }
}
@Composable private fun DPad(modifier: Modifier) = Box(modifier.size(142.dp)) {
    GameButton("↑",GpspCore.UP,Color(0xFF26334A), true, Modifier.align(Alignment.TopCenter))
    GameButton("←",GpspCore.LEFT,Color(0xFF26334A), true, Modifier.align(Alignment.CenterStart))
    GameButton("→",GpspCore.RIGHT,Color(0xFF26334A), true, Modifier.align(Alignment.CenterEnd))
    GameButton("↓",GpspCore.DOWN,Color(0xFF26334A), true, Modifier.align(Alignment.BottomCenter))
}
@Composable private fun GameButton(label:String, code:Int, color:Color, small:Boolean=false, modifier:Modifier=Modifier) {
    val size=if(small) 46.dp else 66.dp
    Surface(color=color.copy(alpha=.9f), contentColor=if(color==Lime) Ink else Color.White, shape=CircleShape, shadowElevation=8.dp,
        modifier=modifier.size(if(small && label.length>2) 72.dp else size, size).pointerInput(code) { detectTapGestures(onPress={ if(GpspCore.available) GpspCore.setButton(code,true); tryAwaitRelease(); if(GpspCore.available) GpspCore.setButton(code,false) }) }) {
        Box(contentAlignment=Alignment.Center) { Text(label, style=if(small) MaterialTheme.typography.labelSmall else MaterialTheme.typography.titleLarge) }
    }
}
