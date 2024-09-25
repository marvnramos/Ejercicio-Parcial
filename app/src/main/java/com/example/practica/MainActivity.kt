@file:Suppress("DEPRECATION")

package com.example.practica

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class MainActivity : ComponentActivity() {
    private var imageUri: Uri? = null
    private var selectedFileUri: Uri? = null

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            Toast.makeText(this, "Foto guardada en: $imageUri", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "No se pudo tomar la foto", Toast.LENGTH_SHORT).show()
        }
    }

    private val openDocumentLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            selectedFileUri = it
            leerArchivoSeleccionado(this, it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PermisosApp(
                onTakePictureClick = { tomarFoto() },
                onOpenDocumentClick = { abrirExploradorArchivos() }
            )
        }
    }

    private fun tomarFoto() {
        val context = this

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            imageUri = createImageUri(context)
            imageUri?.let {
                takePictureLauncher.launch(it)
            } ?: run {
                Toast.makeText(this, "No se pudo crear el URI para la foto", Toast.LENGTH_SHORT).show()
            }
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 0)
        }
    }

    private fun createImageUri(context: Context): Uri? {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "foto_${System.currentTimeMillis()}.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Camera")
            }
        }
        return context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    }

    private fun abrirExploradorArchivos() {
        openDocumentLauncher.launch(arrayOf("text/plain"))
    }

    private fun leerArchivoSeleccionado(context: Context, uri: Uri) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bufferedReader = BufferedReader(InputStreamReader(inputStream))
            val contenido = bufferedReader.use { it.readText() }

            Toast.makeText(context, "Contenido del archivo: $contenido", Toast.LENGTH_LONG).show()

            bufferedReader.close()
            inputStream?.close()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Error al leer el archivo", Toast.LENGTH_SHORT).show()
        }
    }
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun PermisosApp(onTakePictureClick: () -> Unit, onOpenDocumentClick: () -> Unit) {
    Scaffold {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val viewModel: PermisosViewModel = viewModel()
            PermisosScreen(viewModel, onTakePictureClick, onOpenDocumentClick)
        }
    }
}

@Composable
fun PermisosScreen(viewModel: PermisosViewModel, onTakePictureClick: () -> Unit, onOpenDocumentClick: () -> Unit) {
    val context = LocalContext.current
    val activity = context as Activity

    val permisosEstado by viewModel.permisosEstado.collectAsState()

    Text(
        text = "¡Bienvenid@! 👋",
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold
    )

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = "Testeando Gestión de Permisos",
        style = MaterialTheme.typography.bodyMedium,
        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
    )

    Spacer(modifier = Modifier.height(24.dp))

    PermisoCard(
        label = "Solicitar permisos de ubicación",
        estado = permisosEstado[Manifest.permission.ACCESS_FINE_LOCATION] ?: "No solicitado",
        icon = painterResource(id = R.drawable.location),
        onClick = {
            solicitarPermiso(activity, Manifest.permission.ACCESS_FINE_LOCATION, viewModel)
        },
        onActionClick = {
            obtenerUbicacion(activity)
        },
        actionEnabled = permisosEstado[Manifest.permission.ACCESS_FINE_LOCATION] == "Concedido"
    )

    Spacer(modifier = Modifier.height(24.dp))

    PermisoCard(
        label = "Solicitar permisos de cámara",
        estado = permisosEstado[Manifest.permission.CAMERA] ?: "No solicitado",
        icon = painterResource(id = R.drawable.camera),
        onClick = {
            solicitarPermiso(activity, Manifest.permission.CAMERA, viewModel)
        },
        onActionClick = onTakePictureClick,
        actionEnabled = permisosEstado[Manifest.permission.CAMERA] == "Concedido"
    )

    Spacer(modifier = Modifier.height(24.dp))

    PermisoCard(
        label = "Solicitar permisos de almacenamiento",
        estado = permisosEstado[getStoragePermission()] ?: "No solicitado",
        icon = painterResource(id = R.drawable.storage),
        onClick = {
            solicitarPermisoAlmacenamiento(activity, viewModel)
        },
        onActionClick = onOpenDocumentClick,
        actionEnabled = permisosEstado[getStoragePermission()] == "Concedido"
    )
}

@Composable
fun PermisoCard(
    label: String,
    estado: String,
    icon: Painter,
    onClick: () -> Unit,
    onActionClick: () -> Unit,
    actionEnabled: Boolean
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .background(Color.White),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    painter = icon,
                    contentDescription = "",
                    tint = Color.Unspecified,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = if (estado == "Concedido") Icons.Default.CheckCircle else Icons.Default.Close,
                    contentDescription = null,
                    tint = if (estado == "Concedido") Color.Green else Color.Red,
                    modifier = Modifier.size(48.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = label, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onClick) {
                Text(text = label)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onActionClick, enabled = actionEnabled) {
                Text(text = "Realizar acción")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Estado: $estado", fontSize = 14.sp, textAlign = TextAlign.Center)
        }
    }
}

fun solicitarPermiso(activity: Activity, permiso: String, viewModel: PermisosViewModel) {
    when {
        ContextCompat.checkSelfPermission(activity, permiso) == PackageManager.PERMISSION_GRANTED -> {
            Toast.makeText(activity, "Permiso ya concedido", Toast.LENGTH_SHORT).show()
            viewModel.actualizarEstadoPermiso(permiso, "Concedido")
        }
        ActivityCompat.shouldShowRequestPermissionRationale(activity, permiso) -> {
            Toast.makeText(activity, "Es necesario el permiso para continuar", Toast.LENGTH_SHORT).show()
            ActivityCompat.requestPermissions(activity, arrayOf(permiso), 0)
        }
        else -> {
            ActivityCompat.requestPermissions(activity, arrayOf(permiso), 0)
        }
    }
}

fun solicitarPermisoAlmacenamiento(activity: Activity, viewModel: PermisosViewModel) {
    val permisosAlmacenamiento = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO
        )
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    when {
        permisosAlmacenamiento.all { permiso -> ContextCompat.checkSelfPermission(activity, permiso) == PackageManager.PERMISSION_GRANTED } -> {
            Toast.makeText(activity, "Permisos de almacenamiento ya concedidos", Toast.LENGTH_SHORT).show()
            viewModel.actualizarEstadoPermiso(getStoragePermission(), "Concedido")
        }
        ActivityCompat.shouldShowRequestPermissionRationale(activity, permisosAlmacenamiento[0]) -> {
            Toast.makeText(activity, "Es necesario el permiso para continuar", Toast.LENGTH_SHORT).show()
            ActivityCompat.requestPermissions(activity, permisosAlmacenamiento, 0)
        }
        else -> {
            ActivityCompat.requestPermissions(activity, permisosAlmacenamiento, 0)
        }
    }
}

fun obtenerUbicacion(activity: Activity) {
    if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
        val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(activity)
        val locationRequest = LocationRequest.create().apply {
            interval = 10000
            fastestInterval = 5000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    if (location != null) {
                        Toast.makeText(activity, "Ubicación: ${location.latitude}, ${location.longitude}", Toast.LENGTH_LONG).show()
                        fusedLocationClient.removeLocationUpdates(this)
                    }
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    } else {
        Toast.makeText(activity, "Permiso de ubicación no concedido", Toast.LENGTH_SHORT).show()
    }
}

class PermisosViewModel : ViewModel() {
    private val _permisosEstado = MutableStateFlow<Map<String, String>>(emptyMap())
    val permisosEstado: StateFlow<Map<String, String>> = _permisosEstado

    fun actualizarEstadoPermiso(permiso: String, estado: String) {
        _permisosEstado.value = _permisosEstado.value.toMutableMap().apply {
            put(permiso, estado)
        }
    }
}

fun getStoragePermission(): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
}
