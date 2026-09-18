// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.domain

data class SurplusInput(
    // Entrate Lavorativa
    val stipendioMensile: Double = 2000.0,
    val premioRisultatoNettoAnnuale: Double = 3500.0,
    val tredicesimaQuattordicesimaNetto: Double = 1650.0,
    val bonusEventualiPersonaliMensile: Double = 0.0,
    val bonusEventualiPersonaliMensileFinoEta: Int = 100,

    // Entrate Pensione
    val pensioneMensileNetta: Double = 1200.0,
    val tredicesimaQuattordicesimaNettoPensione: Double = 0.0,
    val bonusEventualiPersonaliPensioneMensile: Double = 0.0,
    val bonusEventualiPersonaliPensioneMensileFinoEta: Int = 100,
    val altreEntrateMensiliPensione: Double = 0.0,

    // Spese
    val mutuoAffitto: Double = 600.0,
    val mutuoAffittoFinoEta: Int = 60,

    val condominioLavorativa: Double = 100.0,
    val bolletteLavorativa: Double = 120.0,
    val ciboLavorativa: Double = 300.0,
    val veicoliLavorativa: Double = 350.0,
    val palestraLavorativa: Double = 50.0,
    val trasportiViaggiLavorativa: Double = 40.0,
    val saluteLavorativa: Double = 50.0,
    val vacanzeLavorativa: Double = 150.0,
    val shoppingLavorativa: Double = 70.0,
    val altroLavorativa: Double = 50.0,

    val condominioPensione: Double = 100.0,
    val bollettePensione: Double = 100.0,
    val ciboPensione: Double = 250.0,
    val veicoliPensione: Double = 200.0,
    val palestraPensione: Double = 30.0,
    val trasportiViaggiPensione: Double = 60.0,
    val salutePensione: Double = 80.0,
    val vacanzePensione: Double = 100.0,
    val shoppingPensione: Double = 50.0,
    val altroPensione: Double = 50.0
) {
    fun getEntrateMensiliLavorativa(): Double = stipendioMensile + (premioRisultatoNettoAnnuale / 12) + (tredicesimaQuattordicesimaNetto / 12) + bonusEventualiPersonaliMensile
    fun getEntrateMensiliPensione(): Double = pensioneMensileNetta + altreEntrateMensiliPensione + (tredicesimaQuattordicesimaNettoPensione/12) + bonusEventualiPersonaliPensioneMensile

    fun getUsciteMensiliLavorativa(conMutuo: Boolean): Double {
        val mutuo = if (conMutuo) mutuoAffitto else 0.0
        return mutuo + condominioLavorativa + bolletteLavorativa + ciboLavorativa + veicoliLavorativa + palestraLavorativa + trasportiViaggiLavorativa + saluteLavorativa + vacanzeLavorativa + shoppingLavorativa + altroLavorativa
    }

    fun getUsciteMensiliPensione(conMutuo: Boolean): Double {
        val mutuo = if (conMutuo) mutuoAffitto else 0.0
        return mutuo + condominioPensione + bollettePensione + ciboPensione + veicoliPensione + palestraPensione + trasportiViaggiPensione + salutePensione + vacanzePensione + shoppingPensione + altroPensione
    }

    fun calculateSurplusGiornalieroLavorativa(conMutuo: Boolean): Double {
        return (getEntrateMensiliLavorativa() - getUsciteMensiliLavorativa(conMutuo)) * 12 / 365
    }

    fun calculateSurplusGiornalieroPensione(conMutuo: Boolean): Double {
        return (getEntrateMensiliPensione() - getUsciteMensiliPensione(conMutuo)) * 12 / 365
    }

    fun calculateSurplusGiornalieroMedioLavorativa(): Double {
        return (calculateSurplusGiornalieroLavorativa(true) + calculateSurplusGiornalieroLavorativa(false)) / 2.0
    }

    fun calculateSurplusGiornalieroMedioPensione(): Double {
        return (calculateSurplusGiornalieroPensione(true) + calculateSurplusGiornalieroPensione(false)) / 2.0
    }
}

data class SurplusInputUI(
    val stipendioMensile: String,
    val premioRisultatoNettoAnnuale: String,
    val tredicesimaQuattordicesimaNetto: String,
    val bonusEventualiPersonaliMensile: String,
    val bonusEventualiPersonaliMensileFinoEta: String,
    val pensioneMensileNetta: String,
    val tredicesimaQuattordicesimaNettoPensione: String,
    val bonusEventualiPersonaliPensioneMensile: String,
    val bonusEventualiPersonaliPensioneMensileFinoEta: String,
    val altreEntrateMensiliPensione: String,
    val mutuoAffitto: String,
    val mutuoAffittoFinoEta: String,
    val condominioLavorativa: String,
    val bolletteLavorativa: String,
    val ciboLavorativa: String,
    val veicoliLavorativa: String,
    val palestraLavorativa: String,
    val trasportiViaggiLavorativa: String,
    val saluteLavorativa: String,
    val vacanzeLavorativa: String,
    val shoppingLavorativa: String,
    val altroLavorativa: String,
    val condominioPensione: String,
    val bollettePensione: String,
    val ciboPensione: String,
    val veicoliPensione: String,
    val palestraPensione: String,
    val trasportiViaggiPensione: String,
    val salutePensione: String,
    val vacanzePensione: String,
    val shoppingPensione: String,
    val altroPensione: String
) {
    companion object {
        fun from(inputs: SurplusInput) = SurplusInputUI(
            stipendioMensile = inputs.stipendioMensile.toString(),
            premioRisultatoNettoAnnuale = inputs.premioRisultatoNettoAnnuale.toString(),
            tredicesimaQuattordicesimaNetto = inputs.tredicesimaQuattordicesimaNetto.toString(),
            bonusEventualiPersonaliMensile = inputs.bonusEventualiPersonaliMensile.toString(),
            bonusEventualiPersonaliMensileFinoEta = inputs.bonusEventualiPersonaliMensileFinoEta.toString(),
            pensioneMensileNetta = inputs.pensioneMensileNetta.toString(),
            tredicesimaQuattordicesimaNettoPensione = inputs.tredicesimaQuattordicesimaNettoPensione.toString(),
            bonusEventualiPersonaliPensioneMensile = inputs.bonusEventualiPersonaliPensioneMensile.toString(),
            bonusEventualiPersonaliPensioneMensileFinoEta = inputs.bonusEventualiPersonaliPensioneMensileFinoEta.toString(),
            altreEntrateMensiliPensione = inputs.altreEntrateMensiliPensione.toString(),
            mutuoAffitto = inputs.mutuoAffitto.toString(),
            mutuoAffittoFinoEta = inputs.mutuoAffittoFinoEta.toString(),
            condominioLavorativa = inputs.condominioLavorativa.toString(),
            bolletteLavorativa = inputs.bolletteLavorativa.toString(),
            ciboLavorativa = inputs.ciboLavorativa.toString(),
            veicoliLavorativa = inputs.veicoliLavorativa.toString(),
            palestraLavorativa = inputs.palestraLavorativa.toString(),
            trasportiViaggiLavorativa = inputs.trasportiViaggiLavorativa.toString(),
            saluteLavorativa = inputs.saluteLavorativa.toString(),
            vacanzeLavorativa = inputs.vacanzeLavorativa.toString(),
            shoppingLavorativa = inputs.shoppingLavorativa.toString(),
            altroLavorativa = inputs.altroLavorativa.toString(),
            condominioPensione = inputs.condominioPensione.toString(),
            bollettePensione = inputs.bollettePensione.toString(),
            ciboPensione = inputs.ciboPensione.toString(),
            veicoliPensione = inputs.veicoliPensione.toString(),
            palestraPensione = inputs.palestraPensione.toString(),
            trasportiViaggiPensione = inputs.trasportiViaggiPensione.toString(),
            salutePensione = inputs.salutePensione.toString(),
            vacanzePensione = inputs.vacanzePensione.toString(),
            shoppingPensione = inputs.shoppingPensione.toString(),
            altroPensione = inputs.altroPensione.toString()
        )
    }
}
