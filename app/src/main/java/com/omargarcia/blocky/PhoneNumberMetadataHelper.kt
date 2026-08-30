package com.omargarcia.blocky

import android.content.Context
import io.michaelrocks.libphonenumber.android.PhoneNumberUtil
import io.michaelrocks.libphonenumber.android.Phonenumber.PhoneNumber
import java.util.Locale

data class PhoneNumberDetails(
    val rawNumber: String,
    val formattedInternational: String,
    val formattedNational: String,
    val countryCode: Int,
    val regionCode: String,
    val countryName: String,
    val flagEmoji: String,
    val location: String,
    val numberType: String,
    val isValid: Boolean
)

class PhoneNumberMetadataHelper(context: Context) {

    private val phoneUtil: PhoneNumberUtil = PhoneNumberUtil.createInstance(context)

    fun getNumberDetails(rawNumber: String, locale: Locale = Locale.getDefault()): PhoneNumberDetails {
        val trimmed = rawNumber.trim()
        val isSpanish = locale.language.equals("es", ignoreCase = true)

        if (trimmed.isBlank() || trimmed.equals("Private / Unknown", ignoreCase = true) || trimmed.equals("Unknown", ignoreCase = true)) {
            return PhoneNumberDetails(
                rawNumber = rawNumber,
                formattedInternational = rawNumber,
                formattedNational = rawNumber,
                countryCode = 0,
                regionCode = "",
                countryName = "",
                flagEmoji = "📵",
                location = "",
                numberType = if (isSpanish) "Desconocido" else "Unknown",
                isValid = false
            )
        }

        val defaultRegion = if (locale.country.isNotBlank()) locale.country else "MX"

        return try {
            val protoNumber: PhoneNumber = phoneUtil.parse(trimmed, defaultRegion)
            val isValid = phoneUtil.isValidNumber(protoNumber)
            val regionCode = phoneUtil.getRegionCodeForNumber(protoNumber) ?: defaultRegion
            val countryCode = protoNumber.countryCode

            val countryName = if (regionCode.isNotBlank()) {
                try {
                    Locale.Builder().setRegion(regionCode).build().getDisplayCountry(locale).ifBlank { regionCode }
                } catch (_: Exception) {
                    regionCode
                }
            } else {
                ""
            }

            val formattedIntl = phoneUtil.format(protoNumber, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL)
            val formattedNatl = phoneUtil.format(protoNumber, PhoneNumberUtil.PhoneNumberFormat.NATIONAL)

            // Resolve exact City / State location
            val nationalNumberStr = protoNumber.nationalNumber.toString()
            var cityLocation = ""

            if (countryCode == 52) {
                // Mexico LADA resolver
                cityLocation = resolveMexicoLada(nationalNumberStr)
            } else if (countryCode == 1) {
                // USA / Canada Area Code resolver
                cityLocation = resolveNanpAreaCode(nationalNumberStr)
            }

            if (cityLocation.isBlank()) {
                cityLocation = countryName
            }

            // User-friendly line type description
            val numberTypeEnum = phoneUtil.getNumberType(protoNumber)
            val numberTypeStr = when (numberTypeEnum) {
                PhoneNumberUtil.PhoneNumberType.FIXED_LINE -> if (isSpanish) "Teléfono Fijo" else "Landline"
                PhoneNumberUtil.PhoneNumberType.MOBILE -> if (isSpanish) "Celular" else "Mobile"
                PhoneNumberUtil.PhoneNumberType.FIXED_LINE_OR_MOBILE -> if (isSpanish) "Celular / Teléfono Fijo" else "Mobile / Landline"
                PhoneNumberUtil.PhoneNumberType.TOLL_FREE -> if (isSpanish) "Línea Gratuita (800)" else "Toll-Free (800)"
                PhoneNumberUtil.PhoneNumberType.PREMIUM_RATE -> if (isSpanish) "Tarifa Especial" else "Premium Rate"
                PhoneNumberUtil.PhoneNumberType.SHARED_COST -> if (isSpanish) "Costo Compartido" else "Shared Cost"
                PhoneNumberUtil.PhoneNumberType.VOIP -> if (isSpanish) "VoIP (Telefonía por Internet)" else "VoIP (Internet Phone)"
                PhoneNumberUtil.PhoneNumberType.PERSONAL_NUMBER -> if (isSpanish) "Número Personal" else "Personal Number"
                PhoneNumberUtil.PhoneNumberType.PAGER -> if (isSpanish) "Localizador / Pager" else "Pager"
                PhoneNumberUtil.PhoneNumberType.UAN -> if (isSpanish) "Acceso Universal (UAN)" else "Universal Access (UAN)"
                PhoneNumberUtil.PhoneNumberType.VOICEMAIL -> if (isSpanish) "Buzón de Voz" else "Voicemail"
                else -> if (isSpanish) "Teléfono Estándar" else "Standard Phone"
            }

            PhoneNumberDetails(
                rawNumber = rawNumber,
                formattedInternational = formattedIntl,
                formattedNational = formattedNatl,
                countryCode = countryCode,
                regionCode = regionCode,
                countryName = countryName,
                flagEmoji = countryCodeToEmojiFlag(regionCode),
                location = cityLocation,
                numberType = numberTypeStr,
                isValid = isValid
            )
        } catch (_: Exception) {
            PhoneNumberDetails(
                rawNumber = rawNumber,
                formattedInternational = rawNumber,
                formattedNational = rawNumber,
                countryCode = 0,
                regionCode = "",
                countryName = "",
                flagEmoji = "📞",
                location = "",
                numberType = if (isSpanish) "Desconocido" else "Unknown",
                isValid = false
            )
        }
    }

    private fun resolveMexicoLada(nationalNumber: String): String {
        if (nationalNumber.length < 2) return ""
        // Check 2-digit major metropolises
        val lada2 = nationalNumber.substring(0, 2)
        when (lada2) {
            "55", "56" -> return "Ciudad de México (CDMX)"
            "33" -> return "Guadalajara, Jalisco"
            "81" -> return "Monterrey, Nuevo León"
        }

        if (nationalNumber.length < 3) return ""
        val lada3 = nationalNumber.substring(0, 3)
        return MEXICO_LADA_MAP[lada3] ?: ""
    }

    private fun resolveNanpAreaCode(nationalNumber: String): String {
        if (nationalNumber.length < 3) return ""
        val areaCode = nationalNumber.substring(0, 3)
        return NANP_AREA_CODE_MAP[areaCode] ?: ""
    }

    companion object {
        fun countryCodeToEmojiFlag(countryCode: String?): String {
            if (countryCode.isNullOrBlank() || countryCode.length != 2) return "🌐"
            val upper = countryCode.uppercase(Locale.US)
            val firstChar = Character.codePointAt(upper, 0) - 0x41 + 0x1F1E6
            val secondChar = Character.codePointAt(upper, 1) - 0x41 + 0x1F1E6
            return try {
                val chars1 = Character.toChars(firstChar)
                val chars2 = Character.toChars(secondChar)
                String(chars1) + String(chars2)
            } catch (_: Exception) {
                "🌐"
            }
        }

        private val MEXICO_LADA_MAP = mapOf(
            // Campeche
            "981" to "Campeche, Campeche",
            "982" to "Champotón / Escárcega, Campeche",
            "938" to "Ciudad del Carmen, Campeche",
            "996" to "Calkiní / Hecelchakán, Campeche",

            // Yucatán
            "999" to "Mérida, Yucatán",
            "985" to "Valladolid, Yucatán",
            "986" to "Tizimín, Yucatán",
            "988" to "Izamal, Yucatán",
            "991" to "Motul, Yucatán",
            "997" to "Ticul / Oxkutzcab, Yucatán",

            // Quintana Roo
            "998" to "Cancún, Quintana Roo",
            "984" to "Playa del Carmen / Tulum, Quintana Roo",
            "983" to "Chetumal, Quintana Roo",
            "987" to "Cozumel, Quintana Roo",

            // Tabasco
            "993" to "Villahermosa, Tabasco",
            "937" to "Cárdenas, Tabasco",
            "936" to "Macuspana, Tabasco",
            "934" to "Tenosique, Tabasco",
            "933" to "Comalcalco, Tabasco",
            "932" to "Teapa, Tabasco",
            "914" to "Jalpa de Méndez, Tabasco",
            "913" to "Frontera, Tabasco",

            // Chiapas
            "961" to "Tuxtla Gutiérrez, Chiapas",
            "962" to "Tapachula, Chiapas",
            "967" to "San Cristóbal de las Casas, Chiapas",
            "963" to "Comitán, Chiapas",
            "968" to "Cintalapa, Chiapas",
            "966" to "Tonalá / Arriaga, Chiapas",
            "916" to "Palenque, Chiapas",
            "965" to "Villaflores, Chiapas",

            // Veracruz
            "229" to "Veracruz / Boca del Río, Veracruz",
            "228" to "Xalapa, Veracruz",
            "921" to "Coatzacoalcos, Veracruz",
            "922" to "Minatitlán, Veracruz",
            "782" to "Poza Rica, Veracruz",
            "783" to "Tuxpan, Veracruz",
            "271" to "Córdoba, Veracruz",
            "272" to "Orizaba, Veracruz",
            "294" to "San Andrés Tuxtla, Veracruz",
            "288" to "Cosamaloapan, Veracruz",
            "287" to "Tierra Blanca / Tuxtepec, Veracruz",
            "284" to "Isla, Veracruz",
            "784" to "Papantla, Veracruz",
            "785" to "Martínez de la Torre, Veracruz",

            // Puebla & Tlaxcala
            "222" to "Puebla, Puebla",
            "238" to "Tehuacán, Puebla",
            "248" to "San Martín Texmelucan, Puebla",
            "244" to "Atlixco, Puebla",
            "231" to "Teziutlán, Puebla",
            "764" to "Huauchinango, Puebla",
            "797" to "Zacatlán, Puebla",
            "246" to "Tlaxcala, Tlaxcala",
            "241" to "Apizaco, Tlaxcala",
            "247" to "Huamantla, Tlaxcala",
            "276" to "Zacatelco, Tlaxcala",

            // Oaxaca
            "951" to "Oaxaca de Juárez, Oaxaca",
            "971" to "Salina Cruz / Tehuantepec, Oaxaca",
            "972" to "Ixtepec, Oaxaca",
            "954" to "Puerto Escondido, Oaxaca",
            "958" to "Huatulco, Oaxaca",
            "953" to "Huajuapan de León, Oaxaca",

            // Jalisco
            "322" to "Puerto Vallarta, Jalisco",
            "378" to "Tepatitlán, Jalisco",
            "392" to "Ocotlán, Jalisco",
            "341" to "Ciudad Guzmán, Jalisco",
            "374" to "Tequila, Jalisco",
            "395" to "San Juan de los Lagos, Jalisco",
            "357" to "Ameca, Jalisco",
            "348" to "Arandas, Jalisco",
            "317" to "Autlán de Navarro, Jalisco",
            "388" to "Mascota, Jalisco",
            "358" to "Tamazula, Jalisco",
            "342" to "Sayula, Jalisco",

            // Nuevo León & Tamaulipas
            "828" to "Cadereyta Jiménez, Nuevo León",
            "826" to "Montemorelos, Nuevo León",
            "821" to "Linares, Nuevo León",
            "824" to "Sabinas Hidalgo, Nuevo León",
            "834" to "Ciudad Victoria, Tamaulipas",
            "833" to "Tampico / Cd. Madero, Tamaulipas",
            "899" to "Reynosa, Tamaulipas",
            "868" to "Matamoros, Tamaulipas",
            "867" to "Nuevo Laredo, Tamaulipas",
            "831" to "Ciudad Mante, Tamaulipas",
            "897" to "Miguel Alemán, Tamaulipas",
            "841" to "San Fernando, Tamaulipas",
            "891" to "Camargo, Tamaulipas",
            "832" to "González / Aldama, Tamaulipas",
            "894" to "Valle Hermoso, Tamaulipas",
            "892" to "Río Bravo, Tamaulipas",

            // Estado de México
            "722" to "Toluca / Metepec, Estado de México",
            "729" to "Toluca Metropolitana, Estado de México",
            "595" to "Texcoco, Estado de México",
            "593" to "Zumpango, Estado de México",
            "594" to "Teotihuacán, Estado de México",
            "597" to "Chalco / Amecameca, Estado de México",
            "726" to "Valle de Bravo, Estado de México",
            "728" to "Lerma, Estado de México",
            "714" to "Ixtapan de la Sal, Estado de México",
            "712" to "Atlacomulco, Estado de México",

            // Guanajuato & Querétaro
            "477" to "León, Guanajuato",
            "462" to "Irapuato, Guanajuato",
            "461" to "Celaya, Guanajuato",
            "473" to "Guanajuato, Guanajuato",
            "464" to "Salamanca, Guanajuato",
            "415" to "San Miguel de Allende, Guanajuato",
            "417" to "Acámbaro, Guanajuato",
            "428" to "San Felipe, Guanajuato",
            "418" to "Dolores Hidalgo, Guanajuato",
            "456" to "Valle de Santiago, Guanajuato",
            "429" to "Pénjamo, Guanajuato",
            "445" to "Moroleón / Uriangato, Guanajuato",
            "469" to "Abasolo, Guanajuato",
            "476" to "San Francisco del Rincón, Guanajuato",
            "466" to "Cortazar, Guanajuato",
            "412" to "Villagrán, Guanajuato",
            "442" to "Santiago de Querétaro, Querétaro",
            "427" to "San Juan del Río, Querétaro",
            "441" to "Cadereyta, Querétaro",
            "448" to "Tequisquiapan, Querétaro",

            // San Luis Potosí, Aguascalientes, Zacatecas
            "444" to "San Luis Potosí, S.L.P.",
            "481" to "Ciudad Valles, S.L.P.",
            "488" to "Matehuala, S.L.P.",
            "487" to "Rioverde, S.L.P.",
            "483" to "Tamazunchale, S.L.P.",
            "449" to "Aguascalientes, Aguascalientes",
            "465" to "Calvillo, Aguascalientes",
            "495" to "Rincón de Romos, Aguascalientes",
            "492" to "Zacatecas / Guadalupe, Zacatecas",
            "493" to "Fresnillo, Zacatecas",
            "494" to "Jerez, Zacatecas",
            "433" to "Sombrerete, Zacatecas",
            "498" to "Río Grande, Zacatecas",
            "467" to "Tlaltenango, Zacatecas",
            "437" to "Jalpa, Zacatecas",
            "496" to "Loreto / Ojocaliente, Zacatecas",

            // Michoacán
            "443" to "Morelia, Michoacán",
            "452" to "Uruapan, Michoacán",
            "753" to "Lázaro Cárdenas, Michoacán",
            "351" to "Zamora, Michoacán",
            "353" to "Sahuayo / Jiquilpan, Michoacán",
            "453" to "Apatzingán, Michoacán",
            "715" to "Zitácuaro, Michoacán",
            "352" to "La Piedad, Michoacán",
            "454" to "Nueva Italia, Michoacán",
            "434" to "Pátzcuaro, Michoacán",
            "786" to "Maravatío, Michoacán",
            "438" to "Zacapu, Michoacán",
            "354" to "Los Reyes, Michoacán",
            "451" to "Quiroga, Michoacán",
            "435" to "Huetamo, Michoacán",
            "727" to "Ciudad Hidalgo, Michoacán",

            // Sinaloa & Sonora
            "667" to "Culiacán, Sinaloa",
            "669" to "Mazatlán, Sinaloa",
            "668" to "Los Mochis, Sinaloa",
            "687" to "Guasave, Sinaloa",
            "673" to "Guamúchil, Sinaloa",
            "694" to "Rosario, Sinaloa",
            "695" to "Escuinapa, Sinaloa",
            "672" to "Navolato, Sinaloa",
            "662" to "Hermosillo, Sonora",
            "644" to "Ciudad Obregón, Sonora",
            "631" to "Nogales, Sonora",
            "653" to "San Luis Río Colorado, Sonora",
            "622" to "Guaymas, Sonora",
            "642" to "Navojoa, Sonora",
            "637" to "Caborca, Sonora",
            "633" to "Agua Prieta, Sonora",
            "645" to "Cananea, Sonora",
            "638" to "Puerto Peñasco, Sonora",
            "647" to "Huatabampo, Sonora",
            "623" to "Magdalena de Kino, Sonora",

            // Chihuahua & Coahuila & Durango
            "614" to "Chihuahua, Chihuahua",
            "656" to "Ciudad Juárez, Chihuahua",
            "625" to "Cuauhtémoc, Chihuahua",
            "639" to "Delicias, Chihuahua",
            "627" to "Parral, Chihuahua",
            "648" to "Camargo, Chihuahua",
            "636" to "Nuevo Casas Grandes, Chihuahua",
            "659" to "Ojinaga, Chihuahua",
            "649" to "Jiménez, Chihuahua",
            "635" to "Creel, Chihuahua",
            "844" to "Saltillo, Coahuila",
            "871" to "Torreón / Gómez Palacio, Coahuila/Dgo",
            "866" to "Monclova, Coahuila",
            "878" to "Piedras Negras, Coahuila",
            "877" to "Ciudad Acuña, Coahuila",
            "861" to "Sabinas, Coahuila",
            "872" to "San Pedro / Matamoros, Coahuila",
            "869" to "Cuatro Ciénegas, Coahuila",
            "864" to "Múzquiz, Coahuila",
            "842" to "Parras de la Fuente, Coahuila",
            "618" to "Durango, Durango",
            "677" to "Santiago Papasquiaro, Durango",
            "675" to "Guadalupe Victoria, Durango",
            "674" to "El Salto, Durango",
            "676" to "Santa María del Oro, Durango",
            "671" to "Vicente Guerrero, Durango",

            // Baja California & Baja California Sur
            "664" to "Tijuana, Baja California",
            "686" to "Mexicali, Baja California",
            "646" to "Ensenada, Baja California",
            "665" to "Tecate, Baja California",
            "661" to "Playas de Rosarito, Baja California",
            "616" to "San Quintín, Baja California",
            "612" to "La Paz, Baja California Sur",
            "624" to "Los Cabos / San José del Cabo, B.C.S.",
            "613" to "Ciudad Constitución / Loreto, B.C.S.",
            "615" to "Santa Rosalía / Guerrero Negro, B.C.S.",

            // Hidalgo, Morelos, Guerrero, Nayarit, Colima
            "771" to "Pachuca, Hidalgo",
            "775" to "Tulancingo, Hidalgo",
            "773" to "Tula de Allende, Hidalgo",
            "772" to "Ixmiquilpan, Hidalgo",
            "778" to "Tizayuca, Hidalgo",
            "789" to "Huejutla, Hidalgo",
            "774" to "Actopan, Hidalgo",
            "761" to "Tepeji del Río, Hidalgo",
            "776" to "Apan / Tepeapulco, Hidalgo",
            "791" to "Cd. Sahagún, Hidalgo",
            "777" to "Cuernavaca, Morelos",
            "735" to "Cuautla, Morelos",
            "734" to "Jojutla, Morelos",
            "739" to "Yautepec / Tepoztlán, Morelos",
            "751" to "Puente de Ixtla, Morelos",
            "744" to "Acapulco, Guerrero",
            "747" to "Chilpancingo, Guerrero",
            "755" to "Zihuatanejo / Ixtapa, Guerrero",
            "733" to "Iguala, Guerrero",
            "762" to "Taxco, Guerrero",
            "757" to "Tlapa, Guerrero",
            "767" to "Ciudad Altamirano, Guerrero",
            "742" to "Tecpan, Guerrero",
            "745" to "Ometepec, Guerrero",
            "781" to "Atoyac de Álvarez, Guerrero",
            "311" to "Tepic, Nayarit",
            "323" to "Santiago Ixcuintla, Nayarit",
            "327" to "Compostela / Guayabitos, Nayarit",
            "324" to "Ixtlán del Río, Nayarit",
            "325" to "Tuxpan, Nayarit",
            "329" to "Bahía de Banderas / Punta Mita, Nayarit",
            "319" to "San Blas, Nayarit",
            "389" to "Acaponeta, Nayarit",
            "312" to "Colima, Colima",
            "314" to "Manzanillo, Colima",
            "313" to "Tecomán, Colima"
        )

        private val NANP_AREA_CODE_MAP = mapOf(
            "212" to "New York City, NY",
            "315" to "Syracuse, NY",
            "516" to "Long Island, NY",
            "518" to "Albany, NY",
            "585" to "Rochester, NY",
            "607" to "Binghamton, NY",
            "631" to "Long Island, NY",
            "646" to "New York City, NY",
            "716" to "Buffalo, NY",
            "718" to "New York City, NY",
            "845" to "Poughkeepsie, NY",
            "914" to "Westchester, NY",
            "917" to "New York City, NY",
            "929" to "New York City, NY",
            "213" to "Los Angeles, CA",
            "310" to "Los Angeles / Santa Monica, CA",
            "323" to "Los Angeles, CA",
            "408" to "San Jose, CA",
            "415" to "San Francisco, CA",
            "510" to "Oakland, CA",
            "619" to "San Diego, CA",
            "626" to "Pasadena, CA",
            "650" to "San Mateo / Palo Alto, CA",
            "661" to "Bakersfield, CA",
            "714" to "Anaheim / Orange County, CA",
            "760" to "Palm Springs, CA",
            "805" to "Santa Barbara, CA",
            "818" to "Los Angeles / Glendale, CA",
            "858" to "San Diego / La Jolla, CA",
            "909" to "San Bernardino, CA",
            "916" to "Sacramento, CA",
            "925" to "Concord, CA",
            "949" to "Irvine, CA",
            "951" to "Riverside, CA",
            "214" to "Dallas, TX",
            "281" to "Houston, TX",
            "469" to "Dallas, TX",
            "512" to "Austin, TX",
            "713" to "Houston, TX",
            "817" to "Fort Worth, TX",
            "832" to "Houston, TX",
            "915" to "El Paso, TX",
            "972" to "Dallas, TX",
            "210" to "San Antonio, TX",
            "305" to "Miami, FL",
            "407" to "Orlando, FL",
            "561" to "West Palm Beach, FL",
            "727" to "St. Petersburg, FL",
            "786" to "Miami, FL",
            "813" to "Tampa, FL",
            "904" to "Jacksonville, FL",
            "954" to "Fort Lauderdale, FL",
            "312" to "Chicago, IL",
            "773" to "Chicago, IL",
            "847" to "Evanston / Waukegan, IL",
            "630" to "Naperville, IL",
            "206" to "Seattle, WA",
            "425" to "Bellevue / Redmond, WA",
            "509" to "Spokane, WA",
            "503" to "Portland, OR",
            "971" to "Portland / Salem, OR",
            "404" to "Atlanta, GA",
            "678" to "Atlanta, GA",
            "770" to "Marietta, GA",
            "617" to "Boston, MA",
            "508" to "Worcester, MA",
            "781" to "Waltham, MA",
            "602" to "Phoenix, AZ",
            "480" to "Mesa / Scottsdale, AZ",
            "520" to "Tucson, AZ",
            "623" to "Glendale / Peoria, AZ",
            "303" to "Denver, CO",
            "720" to "Denver / Aurora, CO",
            "702" to "Las Vegas, NV",
            "775" to "Reno / Carson City, NV",
            "416" to "Toronto, ON",
            "647" to "Toronto, ON",
            "905" to "Mississauga, ON",
            "514" to "Montreal, QC",
            "438" to "Montreal, QC",
            "604" to "Vancouver, BC",
            "778" to "Vancouver, BC",
            "403" to "Calgary, AB",
            "780" to "Edmonton, AB"
        )
    }
}

