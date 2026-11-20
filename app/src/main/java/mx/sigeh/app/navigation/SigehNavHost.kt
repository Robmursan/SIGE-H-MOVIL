package mx.sigeh.app.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import mx.sigeh.app.core.designsystem.components.SigehBottomBar
import mx.sigeh.app.core.designsystem.components.SigehTab
import mx.sigeh.app.features.agenda.AgendaRoute
import mx.sigeh.app.features.incidencias.IncidenciasRoute
import mx.sigeh.app.features.incidencias.RegistrarIncidenciaRoute
import mx.sigeh.app.features.incorporacion.RecepcionEventualRoute
import mx.sigeh.app.features.login.LoginRoute
import mx.sigeh.app.features.movimientos.MovimientosRoute
import mx.sigeh.app.features.movimientos.PaseSalidaRoute
import mx.sigeh.app.features.paselista.PaseListaRoute
import mx.sigeh.app.features.paselista.ResumenPaseRoute
import mx.sigeh.app.features.selectorambito.SelectorAmbitoRoute
import mx.sigeh.app.features.sincronizacion.ConflictoRoute
import mx.sigeh.app.features.sincronizacion.SincronizacionRoute

/**
 * Login → SelectorAmbito → grafo principal (Pase, Incidencias, Movimientos, Agenda).
 * Los destinos de detalle no muestran la barra inferior.
 */
@Composable
fun SigehNavHost(
    navController: NavHostController,
    inicio: SigehDestino,
    modifier: Modifier = Modifier,
) {
    val entrada by navController.currentBackStackEntryAsState()
    val tabActual = entrada?.destination?.tabActual()

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (tabActual != null) {
                SigehBottomBar(actual = tabActual, onSeleccionar = { navController.irAPestana(it) })
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = inicio,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            composable<SigehDestino.Login> { entry ->
                LoginRoute(
                    sesionVencida = entry.toRoute<SigehDestino.Login>().sesionVencida,
                    onSesionIniciada = {
                        navController.navigate(SigehDestino.SelectorAmbito()) {
                            popUpTo(navController.graph.id) { inclusive = true }
                        }
                    },
                )
            }

            composable<SigehDestino.SelectorAmbito> { entry ->
                SelectorAmbitoRoute(
                    alcanceVencido = entry.toRoute<SigehDestino.SelectorAmbito>().alcanceVencido,
                    onContinuar = {
                        navController.navigate(SigehDestino.Principal) { launchSingleTop = true }
                    },
                    onSesionCerrada = { navController.irALogin(sesionVencida = false) },
                )
            }

            navigation<SigehDestino.Principal>(startDestination = SigehDestino.PaseLista) {
                composable<SigehDestino.PaseLista> {
                    PaseListaRoute(
                        onRegresar = { navController.volverAlSelector() },
                        onAbrirSincronizacion = { navController.navigate(SigehDestino.Sincronizacion) },
                        onAbrirConflicto = { navController.navigate(SigehDestino.Conflicto(it)) },
                        onAbrirResumen = { navController.navigate(SigehDestino.ResumenPase) },
                    )
                }
                composable<SigehDestino.Incidencias> {
                    IncidenciasRoute(
                        onAbrirSincronizacion = { navController.navigate(SigehDestino.Sincronizacion) },
                        onRegistrar = { navController.navigate(SigehDestino.RegistrarIncidencia(it)) },
                    )
                }
                composable<SigehDestino.Movimientos> {
                    MovimientosRoute(
                        onAbrirSincronizacion = { navController.navigate(SigehDestino.Sincronizacion) },
                        onRegistrarPaseSalida = { navController.navigate(SigehDestino.PaseSalida) },
                    )
                }
                composable<SigehDestino.Agenda> {
                    AgendaRoute(onAbrirSincronizacion = { navController.navigate(SigehDestino.Sincronizacion) })
                }
            }

            composable<SigehDestino.RegistrarIncidencia> {
                RegistrarIncidenciaRoute(onCerrar = { navController.popBackStack() })
            }
            composable<SigehDestino.ResumenPase> {
                ResumenPaseRoute(
                    onRegresar = { navController.popBackStack() },
                    onAbrirConflicto = { navController.navigate(SigehDestino.Conflicto(it)) },
                    onAbrirRecepcion = { navController.navigate(SigehDestino.RecepcionEventual(it)) },
                )
            }
            composable<SigehDestino.PaseSalida> {
                PaseSalidaRoute(onCerrar = { navController.popBackStack() })
            }
            composable<SigehDestino.RecepcionEventual> {
                RecepcionEventualRoute(onCerrar = { navController.popBackStack() })
            }
            composable<SigehDestino.Sincronizacion> {
                SincronizacionRoute(
                    onRegresar = { navController.popBackStack() },
                    onResolver = { navController.navigate(SigehDestino.Conflicto(it)) },
                )
            }
            composable<SigehDestino.Conflicto> {
                ConflictoRoute(
                    onRegresar = { navController.popBackStack() },
                    onResuelto = { navController.popBackStack() },
                )
            }
        }
    }
}

private fun NavDestination.tabActual(): SigehTab? =
    SigehTab.entries.firstOrNull { tab -> hierarchy.any { it.hasRoute(tab.destino::class) } }

private fun NavHostController.irAPestana(tab: SigehTab) {
    navigate(tab.destino) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Regresa al selector de ámbito conservando su estado (o lo abre si no estaba en la pila). */
private fun NavHostController.volverAlSelector() {
    if (!popBackStack<SigehDestino.SelectorAmbito>(inclusive = false)) {
        navigate(SigehDestino.SelectorAmbito()) {
            popUpTo(graph.id) { inclusive = true }
        }
    }
}

/** Cerrar sesión o sesión vencida: Login sin historial. */
internal fun NavHostController.irALogin(sesionVencida: Boolean) {
    navigate(SigehDestino.Login(sesionVencida = sesionVencida)) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}

/** Alcance vencido: se vuelve al selector sin historial. */
internal fun NavHostController.irASelectorPorAlcanceVencido() {
    navigate(SigehDestino.SelectorAmbito(alcanceVencido = true)) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}
