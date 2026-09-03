package com.unni.simplemusicplayer

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.guava.await
import android.provider.MediaStore

class MainActivity : ComponentActivity() {
    private lateinit var controllerFuture: ListenableFuture<MediaController>
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { loadAndShow() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
        }
        controllerFuture = MediaController.Builder(
            this, SessionToken(this, ComponentName(this, PlaybackService::class.java))
        ).buildAsync()
        setContent { MusicApp() }
        loadAndShow()
    }

    private fun loadAndShow() {
        // MediaStore loading is done in the composable through a small helper below.
    }

    @Composable
    fun MusicApp() {
        var songs by remember { mutableStateOf(emptyList<Song>()) }
        var query by remember { mutableStateOf("") }
        var controller by remember { mutableStateOf<MediaController?>(null) }
        LaunchedEffect(Unit) {
            controller = controllerFuture.await()
            songs = querySongs()
        }
        val filtered = songs.filter {
            it.title.contains(query, true) || it.artist.contains(query, true)
        }
        MaterialTheme(colorScheme = darkColorScheme()) {
            Surface(Modifier.fillMaxSize(), color = Color(0xFF090A0D)) {
                Column(Modifier.fillMaxSize().padding(18.dp)) {
                    Text("Music", style = MaterialTheme.typography.headlineLarge, color = Color.White)
                    Text("Simple • Local • Offline", color = Color.Gray)
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value=query, onValueChange={query=it},
                        modifier=Modifier.fillMaxWidth(),
                        placeholder={Text("Search songs")},
                        leadingIcon={Icon(Icons.Default.Search, null)},
                        singleLine=true,
                        shape=RoundedCornerShape(18.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    LazyColumn(Modifier.weight(1f)) {
                        items(filtered) { song ->
                            ListItem(
                                headlineContent={Text(song.title, color=Color.White)},
                                supportingContent={Text(song.artist, color=Color.Gray)},
                                trailingContent={IconButton({
                                    controller?.setMediaItem(MediaItem.fromUri(song.uri))
                                    controller?.prepare()
                                    controller?.play()
                                }) { Icon(Icons.Default.PlayArrow, null, tint=Color.White) }},
                                modifier=Modifier.fillMaxWidth()
                            )
                        }
                    }
                    val c=controller
                    if(c!=null) MiniPlayer(c)
                }
            }
        }
    }

    @Composable
    fun MiniPlayer(c: MediaController) {
        var playing by remember { mutableStateOf(c.isPlaying) }
        Column(Modifier.fillMaxWidth().background(Color(0xFF15171C), RoundedCornerShape(20.dp)).padding(14.dp)) {
            Text(c.mediaMetadata.title ?: "Nothing playing", color=Color.White, maxLines=1)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.Center, modifier=Modifier.fillMaxWidth()) {
                IconButton({c.seekToPreviousMediaItem()}) { Icon(Icons.Default.SkipPrevious, null, tint=Color.White) }
                IconButton({
                    if(c.isPlaying) c.pause() else c.play()
                    playing=c.isPlaying
                }) { Icon(if(playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint=Color.White) }
                IconButton({c.seekToNextMediaItem()}) { Icon(Icons.Default.SkipNext, null, tint=Color.White) }
            }
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("Bass", color=Color.LightGray, modifier=Modifier.width(45.dp))
                Slider(value=PlaybackAudio.bass, onValueChange={PlaybackAudio.setBass(it)}, valueRange=-12f..12f, modifier=Modifier.weight(1f))
            }
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("Treble", color=Color.LightGray, modifier=Modifier.width(45.dp))
                Slider(value=PlaybackAudio.treble, onValueChange={PlaybackAudio.setTreble(it)}, valueRange=-12f..12f, modifier=Modifier.weight(1f))
            }
            VisualizerBars(c)
        }
    }

    @Composable
    fun VisualizerBars(c: MediaController) {
        var tick by remember { mutableLongStateOf(0L) }
        LaunchedEffect(c) {
            while(true) { kotlinx.coroutines.delay(80); tick++ }
        }
        Row(Modifier.fillMaxWidth().height(55.dp), horizontalArrangement=Arrangement.SpaceEvenly, verticalAlignment=Alignment.Bottom) {
            repeat(18) { i ->
                val h = if(c.isPlaying) 10 + ((kotlin.math.sin((tick+i)*0.8)*0.5+0.5)*40).toInt() else 8
                Box(Modifier.width(5.dp).height(h.dp).background(Color(0xFF65D6FF), RoundedCornerShape(4.dp)))
            }
        }
    }

    private fun querySongs(): List<Song> {
        val list=mutableListOf<Song>()
        val projection=arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DATA)
        contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0", null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { cur ->
            val id=cur.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val title=cur.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artist=cur.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val data=cur.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            while(cur.moveToNext()) {
                val path=cur.getString(data) ?: continue
                val lower=path.lowercase()
                if(lower.endsWith(".mp3")||lower.endsWith(".flac")||lower.endsWith(".wav")||lower.endsWith(".ac3")||
                   lower.endsWith(".m4a")||lower.endsWith(".aac")||lower.endsWith(".ogg")) {
                    list += Song(cur.getLong(id), cur.getString(title) ?: "Unknown", cur.getString(artist) ?: "Unknown", path)
                }
            }
        }
        return list
    }
}
data class Song(val id:Long,val title:String,val artist:String,val uri:String)
