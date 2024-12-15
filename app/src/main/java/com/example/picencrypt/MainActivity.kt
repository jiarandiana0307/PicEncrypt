package com.example.picencrypt

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.TweenSpec
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.picencrypt.ui.theme.PicEncryptTheme
import com.example.picencrypt.utils.BlockScramble
import com.example.picencrypt.utils.ImageScramble.Image
import com.example.picencrypt.utils.ImageScramble.ProcessType
import com.example.picencrypt.utils.PerPixelScramble
import com.example.picencrypt.utils.PicEncryptRowColumnScramble
import com.example.picencrypt.utils.PicEncryptRowScramble
import com.example.picencrypt.utils.RowPixelScramble
import com.example.picencrypt.utils.TomatoScramble
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.concurrent.thread
import kotlin.math.round

enum class Algorithm {
    TOMATO,
    BLOCK,
    ROW_PIXEL,
    PER_PIXEL,
    PIC_ENCRYPT_ROW,
    PIC_ENCRYPT_ROW_AND_COLUMN
}

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.Q)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PicEncrypt()
        }
    }
}

fun isPicEncryptAlgorithm(algorithm: Algorithm): Boolean {
    return algorithm == Algorithm.PIC_ENCRYPT_ROW || algorithm == Algorithm.PIC_ENCRYPT_ROW_AND_COLUMN
}

fun checkPicEncryptKeyValidity(key: String): Boolean {
    try {
        key.toDouble()
    } catch (e: NumberFormatException) {
        return false
    }
    return key.toDouble() > 0 && key.toDouble() < 1
}

@SuppressLint("SimpleDateFormat", "UnusedMaterial3ScaffoldPaddingParameter")
@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun PicEncrypt(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    class MyImage(val uri: Uri) {
        var filename: String
        var bitmap: Bitmap
        var thumbnail: Bitmap

        init {
            filename = loadFilename()
            bitmap = BitmapFactory.decodeStream(context.contentResolver.openInputStream(uri))
            thumbnail = loadThumbnail()
        }

        fun loadFilename(): String {
            val newFilename: String
            val projection = arrayOf(
                MediaStore.Images.Media.DISPLAY_NAME
            )
            val cursor =
                context.contentResolver.query(uri, projection, null, null, null)
            if (cursor != null) {
                cursor.moveToFirst()
                val filenameIndex =
                    cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                newFilename = cursor.getString(filenameIndex).toString()
                cursor.close()
            } else {
                newFilename = ""
            }

            return newFilename
        }

        fun loadThumbnail(): Bitmap {
            val maxSize = 800
            val ratio: Float = if (bitmap.width > bitmap.height) {
                if (bitmap.width > maxSize) bitmap.width.toFloat() / maxSize else 1f
            } else {
                if (bitmap.height > maxSize) bitmap.height.toFloat() / maxSize else 1f
            }
            return ThumbnailUtils.extractThumbnail(bitmap, (bitmap.width.toFloat() / ratio).toInt(), (bitmap.height.toFloat() / ratio).toInt())
        }

        fun process(algorithm: Algorithm, processType: ProcessType, key: String = "0.666") {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            var image = Image(pixels, bitmap.width, bitmap.height)
            image = when (algorithm) {
                Algorithm.TOMATO -> TomatoScramble(image).process(processType)
                Algorithm.BLOCK -> BlockScramble(image, key).process(processType)
                Algorithm.ROW_PIXEL -> RowPixelScramble(image, key).process(processType)
                Algorithm.PER_PIXEL -> PerPixelScramble(image, key).process(processType)
                Algorithm.PIC_ENCRYPT_ROW -> PicEncryptRowScramble(image, key.toDouble()).process(processType)
                else -> PicEncryptRowColumnScramble(image, key.toDouble()).process(processType)
            }
            bitmap = Bitmap.createBitmap(image.pixels, image.width, image.height, Bitmap.Config.ARGB_8888)
            thumbnail = loadThumbnail()
        }

        fun rotate(degree: Float) {
            val matrix = Matrix()
            matrix.setRotate(degree)
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            thumbnail = loadThumbnail()
        }

        fun reload() {
            filename = loadFilename()
            bitmap = BitmapFactory.decodeStream(context.contentResolver.openInputStream(uri))
            thumbnail = loadThumbnail()
        }

        fun save(index: Int = 0): Boolean {
            val dateTime =
                SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
            var ms = System.currentTimeMillis()
            ms -= ms / 1000 * 1000 + index
            val s = String.format("%03d", ms)
            val imageName = "PicEncrypt_${dateTime + s}.png"
            val appName = context.getString(R.string.app_name)
            val saveDir = File(
                Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES
                ), appName
            )
            var isSaveImageSucceeded = false
            var fileAbsPath = ""

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(
                        MediaStore.MediaColumns.DISPLAY_NAME,
                        imageName
                    )
                    put(
                        MediaStore.MediaColumns.MIME_TYPE,
                        "image/png"
                    )
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        "Pictures/${appName}"
                    )
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                )

                uri?.let {
                    context.contentResolver.openOutputStream(it)
                        .use { outputStream ->
                            if (outputStream?.let { it1 ->
                                    bitmap.compress(
                                        Bitmap.CompressFormat.PNG,
                                        100,
                                        it1
                                    )
                                }!!) {
                                fileAbsPath =
                                    "${saveDir}/Pictures/${appName}/" + imageName
                                isSaveImageSucceeded = true
                            }
                        }
                }

            } else {
                if (!saveDir.exists()) {
                    saveDir.mkdir()
                }
                val filePath = File(saveDir, imageName)
                try {
                    val outputStream =
                        FileOutputStream(
                            File(
                                saveDir,
                                imageName
                            )
                        )
                    bitmap.compress(
                        Bitmap.CompressFormat.PNG,
                        100,
                        outputStream
                    )
                    outputStream.flush()
                    outputStream.close()

                    // Update album to add new image.
                    context.sendBroadcast(
                        Intent(
                            Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                            Uri.parse("file://" + filePath.absolutePath)
                        )
                    )
                    fileAbsPath = filePath.absolutePath
                    isSaveImageSucceeded = true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            return isSaveImageSucceeded
        }

    }

    val focusManager = LocalFocusManager.current
    val images = ArrayList<MyImage>()
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    var newImageUriCount by remember { mutableStateOf(0) }
    var progress by remember { mutableStateOf(0f) }
    var isShowProgress by remember { mutableStateOf(false) }
    var isShowMethodAndKeySettingOnTopLayer by remember { mutableStateOf(false) }
    var isShowInfo by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf(true) }
    var selectedAlgorithm by remember { mutableStateOf(Algorithm.TOMATO) }
    var keyString by remember { mutableStateOf("0.666") }
    var isDropdownMenuExpanded by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = {
            val uri = it.data?.data
            val newImageUris = ArrayList<Uri>()
            if (uri == null) {
                val imagesName = it.data?.clipData
                if (imagesName != null) {
                    for (i in 0 until imagesName.itemCount) {
                        newImageUris.add(imagesName.getItemAt(i).uri)
                    }
                }
            } else {
                newImageUris.add(uri)
            }

            if (newImageUris.size >= 1) {
                Toast.makeText(
                    context,
                    context.getString(R.string.start_loading_images),
                    Toast.LENGTH_SHORT
                ).show()
                thread {
                    isShowProgress = true
                    progress = 0f
                    newImageUriCount = newImageUris.size
                    for (i in 0 until newImageUris.size) {
                        images.add(MyImage(newImageUris[i]))
                        progress = (i + 1).toFloat() / newImageUris.size
                        update = !update
                    }
                    newImageUriCount = 0
                    isShowProgress = false
                    Looper.prepare()
                    Toast.makeText(
                        context,
                        context.getString(R.string.image_loading_completed),
                        Toast.LENGTH_SHORT
                    ).show()
                    Looper.loop()
                }
            }
        }
    )

    @Composable
    fun SetMethodAndKey(isExpanded: Boolean = true) {
        val itemStringIds = arrayOf(
            R.string.tomato_algorithm,
            R.string.block_algorithm,
            R.string.row_pixel_algorithm,
            R.string.per_pixel_algorithm,
            R.string.pic_encrypt_row_pixel,
            R.string.pic_encrypt_row_and_column_pixel
        )

        ExposedDropdownMenuBox(
            expanded = isDropdownMenuExpanded && isExpanded,
            onExpandedChange = {
                isDropdownMenuExpanded = !isDropdownMenuExpanded
            },
            modifier.padding(5.dp)
        ) {
            TextField(
                value = stringResource(id = itemStringIds[selectedAlgorithm.ordinal]),
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                label = { Text(text = stringResource(id = R.string.select_algorithm)) },
                colors = TextFieldDefaults.textFieldColors(
                    disabledTextColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
                modifier = Modifier
                    .menuAnchor()
                    .clip(RoundedCornerShape(20.dp))
            )
            ExposedDropdownMenu(
                expanded = isDropdownMenuExpanded && isExpanded,
                onDismissRequest = { isDropdownMenuExpanded = false }
            ) {
                for (algorithm in Algorithm.values()) {
                    val itemStringId = itemStringIds[algorithm.ordinal]
                    DropdownMenuItem(
                        text = { Text(text = stringResource(id = itemStringId)) },
                        onClick = {
                            selectedAlgorithm = algorithm
                            isDropdownMenuExpanded = false
                        }
                    )
                }
            }
        }

        if (selectedAlgorithm != Algorithm.TOMATO) {
            val keyboardType: KeyboardType =
                if (isPicEncryptAlgorithm(selectedAlgorithm)) {
                    KeyboardType.Decimal
                } else {
                    KeyboardType.Text
                }

            TextField(
                value = keyString,
                onValueChange = { keyString = it },
                singleLine = true,
                label = { Text(text = stringResource(id = R.string.key_text_field_label)) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                colors = TextFieldDefaults.textFieldColors(
                    disabledTextColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
                modifier = Modifier
                    .padding(vertical = 5.dp)
                    .clip(RoundedCornerShape(20.dp))
            )
        }
    }

    PicEncryptTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(text = stringResource(id = R.string.app_name)) },
                        navigationIcon = {
                            Image(
                                painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(80.dp)
                            )
                        },
                        actions = {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(horizontal = 15.dp)
                                    .clickable {
                                        isShowInfo = !isShowInfo
                                    }
                            )
                        }
                    )
                }
            ) {
                Box {
                    Spacer(modifier = Modifier.height((if (update) 90 else 90).dp))

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = if (images.size > 0) Arrangement.Top else Arrangement.Center,
                        modifier = modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(0.dp, 0.dp, 0.dp, 150.dp)
                            .pointerInput(key1 = null) {
                                detectTapGestures(onTap = {
                                    focusManager.clearFocus()
                                })
                            }
                    ) {
                        Spacer(modifier = Modifier.height((if (update) 90 else 90).dp))

                        Button(
                            onClick = {
                                launcher.launch(Intent(Intent.ACTION_GET_CONTENT).apply {
                                    type = "image/*"
                                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                                })
                            },
                            modifier = Modifier.padding(vertical = 5.dp)
                        ) {
                            Text(text = stringResource(id = R.string.select_image_btn))
                        }

                        if (images.size > 0) {
                            val isEnableExpanded = !isShowMethodAndKeySettingOnTopLayer
                            SetMethodAndKey(isEnableExpanded)

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp)
                            ) {

                                fun processImage(processType: ProcessType) {
                                    if (isPicEncryptAlgorithm(selectedAlgorithm) && !checkPicEncryptKeyValidity(
                                            keyString
                                        )
                                    ) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.invalid_key_toast),
                                            Toast.LENGTH_LONG
                                        ).show()
                                        return
                                    }

                                    val prompt =
                                        if (processType == ProcessType.ENCRYPT) {
                                            context.getString(R.string.start_encryption)
                                        } else {
                                            context.getString(R.string.start_decryption)
                                        }

                                    Toast.makeText(
                                        context,
                                        prompt,
                                        Toast.LENGTH_SHORT
                                    ).show()

                                    thread {
                                        isShowProgress = true
                                        progress = 0f
                                        for (i in 0 until images.size) {
                                            images[i].process(selectedAlgorithm, processType, keyString)
                                            progress = (i + 1).toFloat() / images.size
                                            update = !update
                                        }
                                        isShowProgress = false

                                        Looper.prepare()
                                        Toast.makeText(
                                            context,
                                            context.getString(
                                                if (processType == ProcessType.ENCRYPT)
                                                    R.string.encryption_completed
                                                else R.string.decryption_completed),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        Looper.loop()
                                    }
                                }

                                Button(
                                    onClick = { processImage(ProcessType.ENCRYPT) },
                                    Modifier.width(120.dp)
                                ) {
                                    Text(text = stringResource(id = R.string.encrypt_btn))
                                }

                                Button(
                                    onClick = { processImage(ProcessType.DECRYPT)  },
                                    modifier = Modifier.width(120.dp)
                                ) {
                                    Text(text = stringResource(id = R.string.decrypt_btn))
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp)
                            ) {
                                Button(
                                    onClick = {
                                        thread {
                                            isShowProgress = true
                                            progress = 0f
                                            for (i in images.size - 1 downTo 0) {
                                                try {
                                                    images[i].reload()
                                                } catch (e: FileNotFoundException) {
                                                    images.removeAt(i)
                                                    Toast.makeText(
                                                        context,
                                                        context.getString(R.string.file_not_found),
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                                progress = (images.size - i).toFloat() / images.size
                                                update = !update
                                            }
                                            isShowProgress = false
                                        }
                                    },
                                    modifier = Modifier.width(120.dp)
                                ) {
                                    Text(text = stringResource(id = R.string.reset_btn))
                                }

                                Button(
                                    onClick = {
                                        images.clear()
                                        update = !update
                                    },
                                    modifier = Modifier.width(120.dp)
                                ) {
                                    Text(text = stringResource(id = R.string.clear_btn))
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp)
                            ) {
                                Button(
                                    onClick = {
                                        thread {
                                            isShowProgress = true
                                            progress = 0f
                                            for (i in 0 until images.size) {
                                                images[i].rotate(180f)
                                                progress = (i + 1).toFloat() / images.size
                                                update = !update
                                            }
                                            isShowProgress = false
                                        }
                                    },
                                    modifier = Modifier
                                        .width(120.dp)
                                ) {
                                    Text(text = stringResource(id = R.string.rotate180_btn))
                                }

                                Button(
                                    onClick = {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.start_saving),
                                            Toast.LENGTH_SHORT
                                        ).show()

                                        thread {
                                            isShowProgress = true
                                            progress = 0f
                                            for (i in 0 until images.size) {
                                                images[i].save(i)
                                                progress = (i + 1).toFloat() / images.size
                                                update = !update
                                            }
                                            isShowProgress = false

                                            Looper.prepare()
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.succeeded_save_image_toast),
                                                Toast.LENGTH_LONG
                                            )
                                                .show()
                                            Looper.loop()
                                        }

                                    },
                                    modifier = Modifier
                                        .width(120.dp)
                                ) {
                                    Text(text = stringResource(id = R.string.save_btn))
                                }
                            }

                            if (isShowProgress) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier
                                        .padding(vertical = 5.dp)
                                        .fillMaxWidth()
                                        .height(30.dp)
                                ) {
                                    LinearProgressIndicator(
                                        progress = progress,
                                        modifier = Modifier
                                            .fillMaxWidth(0.8f)
                                            .padding(horizontal = 10.dp)
                                    )
                                    val total = if (newImageUriCount > 0) {
                                        round(images.size.toFloat() / progress).toInt()
                                    } else {
                                        images.size
                                    }
                                    Text(
                                        text = "${round(total * progress).toInt()}/$total",
                                        fontSize = 12.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                            } else {
                                Spacer(modifier = Modifier.height(40.dp))
                            }

                            for (i in 0 until images.size) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.SpaceEvenly,
                                    modifier = Modifier
                                        .fillMaxWidth(0.95f)
                                        .padding(0.dp, 0.dp, 0.dp, 15.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(
                                            if (isSystemInDarkTheme()) Color(50, 50, 50)
                                            else Color(245, 240, 242)
                                        )
                                ) {
                                    Box(
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(0.dp, 0.dp, 50.dp, 0.dp)
                                        ) {
                                            SelectionContainer {
                                                Text(
                                                    text = images[i].filename,
                                                    fontSize = if (update) 12.sp else 12.sp,
                                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 15.dp)
                                                )
                                            }

                                            Image(
                                                bitmap = images[i].thumbnail.asImageBitmap(),
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .padding(15.dp, 0.dp, 15.dp, 10.dp)
                                                    .clip(
                                                        RoundedCornerShape(10.dp)
                                                    )
                                            )
                                        }
                                        Column(
                                            horizontalAlignment = Alignment.End,
                                            verticalArrangement = Arrangement.Top,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 10.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.clip(CircleShape)
                                            ) {
                                                Icon(
                                                    Icons.Rounded.Close,
                                                    null,
                                                    modifier = Modifier
                                                        .clickable {
                                                            images.removeAt(i)
                                                            update = !update
                                                        }
                                                        .padding(10.dp)
                                                )
                                            }

                                            Row(
                                                modifier = Modifier.clip(CircleShape)
                                            ) {
                                                Icon(
                                                    Icons.Rounded.Settings,
                                                    null,
                                                    modifier = Modifier
                                                        .clickable {
                                                            isShowMethodAndKeySettingOnTopLayer =
                                                                true
                                                            update = !update
                                                        }
                                                        .padding(10.dp)
                                                )
                                            }

                                            Row(
                                                modifier = Modifier.clip(CircleShape)
                                            ) {
                                                Icon(
                                                    Icons.Rounded.ArrowBack,
                                                    null,
                                                    modifier = Modifier
                                                        .clickable {
                                                            if (isPicEncryptAlgorithm(
                                                                    selectedAlgorithm
                                                                ) && !checkPicEncryptKeyValidity(
                                                                    keyString
                                                                )
                                                            ) {
                                                                Toast
                                                                    .makeText(
                                                                        context,
                                                                        context.getString(R.string.invalid_key_toast),
                                                                        Toast.LENGTH_LONG
                                                                    )
                                                                    .show()
                                                                return@clickable
                                                            }

                                                            Toast
                                                                .makeText(
                                                                    context,
                                                                    context.getString(R.string.start_encryption),
                                                                    Toast.LENGTH_SHORT
                                                                )
                                                                .show()
                                                            thread {
                                                                images[i].process(
                                                                    selectedAlgorithm,
                                                                    ProcessType.ENCRYPT,
                                                                    keyString
                                                                )
                                                                update = !update
                                                            }
                                                        }
                                                        .padding(10.dp)
                                                )
                                            }

                                            Row(
                                                modifier = Modifier.clip(CircleShape)
                                            ) {
                                                Icon(
                                                    Icons.Rounded.ArrowForward,
                                                    null,
                                                    modifier = Modifier
                                                        .clickable {
                                                            if (isPicEncryptAlgorithm(
                                                                    selectedAlgorithm
                                                                ) && !checkPicEncryptKeyValidity(
                                                                    keyString
                                                                )
                                                            ) {
                                                                Toast
                                                                    .makeText(
                                                                        context,
                                                                        context.getString(R.string.invalid_key_toast),
                                                                        Toast.LENGTH_LONG
                                                                    )
                                                                    .show()
                                                                return@clickable
                                                            }

                                                            Toast
                                                                .makeText(
                                                                    context,
                                                                    context.getString(R.string.start_decryption),
                                                                    Toast.LENGTH_SHORT
                                                                )
                                                                .show()
                                                            thread {
                                                                images[i].process(
                                                                    selectedAlgorithm,
                                                                    ProcessType.DECRYPT,
                                                                    keyString
                                                                )
                                                                update = !update
                                                            }
                                                        }
                                                        .padding(10.dp)
                                                )
                                            }

                                            Row(
                                                modifier = Modifier.clip(CircleShape)
                                            ) {
                                                Icon(
                                                    painterResource(id = R.drawable.crop_rotate_119243),
                                                    null,
                                                    modifier = Modifier
                                                        .clickable {
                                                            images[i].rotate(180f)
                                                            update = !update
                                                        }
                                                        .padding(10.dp)
                                                )
                                            }

                                            Row(
                                                modifier = Modifier.clip(CircleShape)
                                            ) {
                                                Icon(
                                                    Icons.Rounded.Refresh,
                                                    null,
                                                    modifier = Modifier
                                                        .clickable {
                                                            images[i].reload()
                                                            update = !update
                                                        }
                                                        .padding(10.dp)
                                                )
                                            }

                                            Row(
                                                modifier = Modifier.clip(CircleShape)
                                            ) {
                                                Icon(
                                                    painterResource(id = R.drawable.import_download_icon_176152),
                                                    null,
                                                    modifier = Modifier
                                                        .clickable {
                                                            Toast
                                                                .makeText(
                                                                    context,
                                                                    context.getString(R.string.start_saving),
                                                                    Toast.LENGTH_SHORT
                                                                )
                                                                .show()
                                                            thread {
                                                                val result = images[i].save(i)
                                                                Looper.prepare()
                                                                Toast
                                                                    .makeText(
                                                                        context,
                                                                        if (result) context.getString(
                                                                            R.string.succeeded_save_image_toast
                                                                        )
                                                                        else context.getString(R.string.failed_save_image_toast),
                                                                        Toast.LENGTH_SHORT
                                                                    )
                                                                    .show()
                                                                Looper.loop()
                                                            }
                                                        }
                                                        .padding(10.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (images.size > 0) {
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.Bottom,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(30.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color(149, 117, 205, 255))
                            ) {
                                Icon(
                                    Icons.Rounded.Add,
                                    null,
                                    modifier = Modifier
                                        .width(40.dp)
                                        .height(40.dp)
                                        .clickable {
                                            launcher.launch(Intent(Intent.ACTION_GET_CONTENT).apply {
                                                type = "image/*"
                                                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                                            })
                                        }
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Row(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(Color(149, 117, 205, 255))
                            ) {
                                Icon(
                                    Icons.Rounded.KeyboardArrowUp,
                                    null,
                                    modifier = Modifier
                                        .width(40.dp)
                                        .height(40.dp)
                                        .clickable {
                                            coroutineScope.launch {
                                                scrollState.animateScrollTo(0, TweenSpec(800))
                                            }
                                        }
                                )
                            }
                        }
                    }

                    if (isShowMethodAndKeySettingOnTopLayer) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0, 0, 0, 180))
                                .clickable {
                                    isShowMethodAndKeySettingOnTopLayer = false
                                }
                        ) {
                            SetMethodAndKey()
                        }
                    }

                    if (isShowInfo) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0, 0, 0, 180))
                                .clickable(false) {}
                        ) {

                            Column(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .fillMaxWidth(0.8f)
                                    .background(
                                        if (isSystemInDarkTheme()) Color(
                                            50,
                                            50,
                                            50
                                        ) else Color(250, 250, 250)
                                    )
                            ) {
                                Box(
                                    contentAlignment = Alignment.TopCenter
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.End,
                                        verticalArrangement = Arrangement.Top,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.clip(CircleShape)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = null,
                                                modifier = Modifier
                                                    .clickable { isShowInfo = false }
                                                    .padding(10.dp)
                                            )
                                        }
                                    }

                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        Image(
                                            painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .padding(0.dp, 0.dp, 0.dp, 10.dp)
                                                .size(80.dp)
                                        )

                                        Text(
                                            text = stringResource(id = R.string.app_name),
                                            modifier = Modifier.padding(0.dp, 0.dp, 0.dp, 10.dp)
                                        )

                                        Text(
                                            text = "v${
                                                context.packageManager.getPackageInfo(
                                                    context.packageName,
                                                    0
                                                ).versionName
                                            }",
                                            modifier = Modifier.padding(0.dp, 0.dp, 0.dp, 10.dp)
                                        )
                                        
                                        Spacer(modifier = Modifier.height(10.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}