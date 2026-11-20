package mx.sigeh.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import mx.sigeh.app.core.auth.EstadoSesion
import mx.sigeh.app.core.designsystem.theme.SigehTheme
import mx.sigeh.app.navigation.SigehDestino
import mx.sigeh.app.navigation.SigehNavHost
import mx.sigeh.app.navigation.irALogin
import mx.sigeh.app.navigation.irASelectorPorAlcanceVencido

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SigehTheme {
                SigehRaiz()
            }
        }
    }
}

@Composable
private fun SigehRaiz(viewModel: SigehAppViewModel = hiltViewModel()) {
    val estadoSesion by viewModel.estadoSesion.collectAsStateWithLifecycle()
    if (estadoSesion == EstadoSesion.CARGANDO) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        )
        return
    }

    val navController = rememberNavController()
    // El destino inicial se decide una sola vez; después, la navegación reacciona a los cambios.
    val inicio: SigehDestino = remember {
        if (estadoSesion == EstadoSesion.ACTIVA) SigehDestino.SelectorAmbito() else SigehDestino.Login(
            sesionVencida = estadoSesion == EstadoSesion.VENCIDA,
        )
    }

    LaunchedEffect(estadoSesion) {
        val enLogin = navController.currentDestination?.hasRoute(SigehDestino.Login::class) == true
        if (estadoSesion == EstadoSesion.VENCIDA && !enLogin) {
            navController.irALogin(sesionVencida = true)
        }
    }
    LaunchedEffect(viewModel) {
        viewModel.eventos.collect { evento ->
            when (evento) {
                EventoApp.AlcanceVencido -> navController.irASelectorPorAlcanceVencido()
            }
        }
    }

    SigehNavHost(navController = navController, inicio = inicio)
}
