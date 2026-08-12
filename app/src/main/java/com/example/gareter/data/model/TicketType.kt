package com.example.gareter.data.model

enum class TicketType(val label: String, val defaultPriceCents: Int, val validityMinutes: Int?) {
    PLEIN_TARIF("Plein tarif", 210, 90),
    CARNET("Carnet 10 trajets", 1800, null),       // pas d'expiration temporelle
    ABONNEMENT_MENSUEL("Abonnement mensuel", 4500, null),
    CONTREMARQUE("Contremarque", 0, 90),
}
