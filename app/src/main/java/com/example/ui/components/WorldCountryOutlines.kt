package com.example.ui.components

/**
 * Geographic country & sovereign territory boundary outlines (Latitude, Longitude in degrees)
 * for rendering recognizable country borders on the 3D Earth Globe and 2D Ground-Track Map.
 */
data class CountryOutlinePolygon(
    val countryName: String,
    val isoCode: String,
    val coordinatesLatLon: List<Pair<Float, Float>>
)

object WorldCountryOutlines {

    val countries: List<CountryOutlinePolygon> = listOf(
        // ================= NORTH AMERICA =================
        CountryOutlinePolygon(
            countryName = "United States (Contiguous)",
            isoCode = "USA",
            coordinatesLatLon = listOf(
                48.5f to -124.7f, 49.0f to -116.0f, 49.0f to -104.0f, 49.0f to -95.1f,
                47.0f to -89.5f, 45.0f to -83.5f, 42.3f to -83.0f, 43.6f to -76.2f,
                45.0f to -71.5f, 47.0f to -67.8f, 44.8f to -66.9f, 42.0f to -70.0f,
                40.5f to -74.0f, 36.8f to -76.0f, 35.2f to -75.5f, 32.0f to -80.8f,
                25.2f to -80.2f, 25.0f to -81.1f, 29.0f to -83.0f, 30.2f to -88.5f,
                29.1f to -89.6f, 29.5f to -94.5f, 26.0f to -97.2f, 29.5f to -101.0f,
                31.8f to -106.5f, 31.3f to -111.0f, 32.5f to -117.1f, 34.5f to -120.5f,
                37.8f to -122.5f, 42.0f to -124.4f, 48.5f to -124.7f
            )
        ),
        CountryOutlinePolygon(
            countryName = "United States (Alaska)",
            isoCode = "USA-AK",
            coordinatesLatLon = listOf(
                60.0f to -141.0f, 69.6f to -141.0f, 71.3f to -156.5f, 68.5f to -166.0f,
                65.6f to -168.0f, 60.0f to -166.0f, 55.0f to -163.0f, 58.5f to -152.0f,
                60.0f to -141.0f
            )
        ),
        CountryOutlinePolygon(
            countryName = "Canada",
            isoCode = "CAN",
            coordinatesLatLon = listOf(
                49.0f to -123.5f, 54.7f to -130.5f, 60.0f to -141.0f, 69.6f to -141.0f,
                70.2f to -128.0f, 74.0f to -118.0f, 76.0f to -95.0f, 73.0f to -80.0f,
                63.0f to -65.0f, 60.0f to -64.5f, 52.0f to -55.8f, 47.0f to -60.5f,
                44.6f to -63.6f, 45.0f to -67.0f, 46.8f to -71.2f, 45.0f to -74.7f,
                43.6f to -79.4f, 42.3f to -83.0f, 46.5f to -84.3f, 48.8f to -88.0f,
                49.0f to -95.1f, 49.0f to -110.0f, 49.0f to -123.5f
            )
        ),
        CountryOutlinePolygon(
            countryName = "Mexico",
            isoCode = "MEX",
            coordinatesLatLon = listOf(
                32.5f to -117.1f, 31.3f to -111.0f, 31.8f to -106.5f, 29.5f to -101.0f,
                26.0f to -97.2f, 22.2f to -97.8f, 19.2f to -96.1f, 18.5f to -92.0f,
                21.5f to -90.0f, 21.2f to -86.8f, 18.0f to -88.0f, 14.6f to -92.2f,
                16.2f to -95.2f, 17.9f to -102.2f, 20.6f to -105.3f, 23.2f to -106.4f,
                27.9f to -111.0f, 31.3f to -113.5f, 23.0f to -110.0f, 28.0f to -114.0f,
                32.5f to -117.1f
            )
        ),
        CountryOutlinePolygon(
            countryName = "Central America",
            isoCode = "CAM",
            coordinatesLatLon = listOf(
                17.8f to -88.2f, 15.8f to -88.5f, 16.0f to -85.0f, 15.0f to -83.1f,
                11.0f to -83.7f, 9.5f to -82.5f, 8.9f to -79.5f, 7.8f to -77.5f,
                8.3f to -80.5f, 8.5f to -83.0f, 10.8f to -85.8f, 13.0f to -87.5f,
                14.5f to -92.2f, 17.8f to -88.2f
            )
        ),
        CountryOutlinePolygon(
            countryName = "Cuba",
            isoCode = "CUB",
            coordinatesLatLon = listOf(
                23.1f to -82.4f, 22.5f to -79.0f, 20.2f to -74.2f, 19.9f to -77.7f,
                21.8f to -84.9f, 23.1f to -82.4f
            )
        ),
        CountryOutlinePolygon(
            countryName = "Greenland",
            isoCode = "GRL",
            coordinatesLatLon = listOf(
                60.0f to -44.0f, 65.5f to -37.0f, 70.5f to -22.0f, 77.0f to -19.0f,
                82.5f to -30.0f, 81.5f to -60.0f, 76.0f to -69.0f, 69.0f to -53.5f,
                64.0f to -51.7f, 60.0f to -44.0f
            )
        ),

        // ================= SOUTH AMERICA =================
        CountryOutlinePolygon(
            countryName = "Colombia",
            isoCode = "COL",
            coordinatesLatLon = listOf(
                11.8f to -71.5f, 7.2f to -71.0f, 1.8f to -67.0f, -4.0f to -70.0f,
                -0.2f to -75.0f, 1.5f to -78.8f, 5.5f to -77.4f, 8.5f to -77.3f,
                10.9f to -74.8f, 11.8f to -71.5f
            )
        ),
        CountryOutlinePolygon(
            countryName = "Venezuela",
            isoCode = "VEN",
            coordinatesLatLon = listOf(
                11.8f to -71.5f, 10.6f to -66.9f, 10.5f to -62.0f, 8.5f to -60.0f,
                5.0f to -60.8f, 1.2f to -66.5f, 7.2f to -71.0f, 11.8f to -71.5f
            )
        ),
        CountryOutlinePolygon(
            countryName = "Brazil",
            isoCode = "BRA",
            coordinatesLatLon = listOf(
                4.2f to -51.5f, 0.0f to -49.5f, -2.5f to -44.2f, -5.8f to -35.2f,
                -8.0f to -34.9f, -13.0f to -38.5f, -20.3f to -40.3f, -23.0f to -43.2f,
                -25.5f to -48.5f, -32.0f to -52.1f, -30.2f to -57.6f, -25.6f to -54.6f,
                -19.0f to -57.8f, -11.0f to -68.5f, -7.5f to -73.8f, -4.0f to -70.0f,
                1.8f to -67.0f, 1.2f to -60.5f, 4.2f to -60.0f, 4.2f to -51.5f
            )
        ),
        CountryOutlinePolygon(
            countryName = "Peru",
            isoCode = "PER",
            coordinatesLatLon = listOf(
                -3.5f to -80.4f, -0.2f to -75.0f, -4.0f to -70.0f, -7.5f to -73.8f,
                -11.0f to -69.5f, -16.2f to -69.0f, -18.3f to -70.4f, -14.0f to -76.3f,
                -12.0f to -77.1f, -5.2f to -81.1f, -3.5f to -80.4f
            )
        ),
        CountryOutlinePolygon(
            countryName = "Bolivia",
            isoCode = "BOL",
            coordinatesLatLon = listOf(
                -11.0f to -68.8f, -10.5f to -65.4f, -14.0f to -60.4f, -19.0f to -57.8f,
                -22.2f to -62.7f, -22.8f to -67.8f, -16.5f to -69.0f, -11.0f to -68.8f
            )
        ),
        CountryOutlinePolygon(
            countryName = "Argentina",
            isoCode = "ARG",
            coordinatesLatLon = listOf(
                -22.2f to -65.5f, -22.2f to -62.7f, -25.5f to -57.6f, -27.4f to -55.6f,
                -30.2f to -57.6f, -34.6f to -58.4f, -38.0f to -57.5f, -41.0f to -63.0f,
                -46.0f to -67.5f, -51.6f to -69.2f, -54.8f to -68.3f, -50.0f to -73.0f,
                -41.0f to -71.8f, -33.0f to -70.0f, -27.0f to -68.5f, -22.2f to -65.5f
            )
        ),
        CountryOutlinePolygon(
            countryName = "Chile",
            isoCode = "CHL",
            coordinatesLatLon = listOf(
                -18.3f to -70.4f, -22.8f to -67.8f, -27.0f to -68.5f, -33.0f to -70.0f,
                -41.0f to -71.8f, -50.0f to -73.0f, -54.8f to -68.3f, -53.0f to -74.0f,
                -43.5f to -74.2f, -33.0f to -71.6f, -23.6f to -70.4f, -18.3f to -70.4f
            )
        ),

        // ================= EUROPE =================
        CountryOutlinePolygon(
            countryName = "United Kingdom & Ireland",
            isoCode = "GBR",
            coordinatesLatLon = listOf(
                50.1f to -5.2f, 50.8f to 0.5f, 52.8f to 1.7f, 55.0f to -1.5f,
                57.6f to -1.8f, 58.6f to -3.1f, 58.5f to -5.0f, 55.8f to -5.6f,
                53.4f to -3.0f, 51.6f to -5.0f, 50.1f to -5.2f
            )
        ),
        CountryOutlinePolygon(
            countryName = "Spain & Portugal",
            isoCode = "ESP",
            coordinatesLatLon = listOf(
                43.4f to -8.4f, 43.4f to -1.8f, 42.4f to 3.1f, 38.8f to 0.2f,
                36.7f to -2.2f, 36.0f to -5.6f, 37.0f to -8.9f, 38.7f to -9.4f,
                41.9f to -8.9f, 43.4f to -8.4f
            )
        ),
        CountryOutlinePolygon(
            countryName = "France",
            isoCode = "FRA",
            coordinatesLatLon = listOf(
                43.4f to -1.8f, 46.2f to -1.2f, 48.4f to -4.7f, 49.6f to -1.6f,
                51.0f to 2.5f, 49.0f to 8.1f, 47.6f to 7.6f, 45.8f to 6.8f,
                43.7f to 7.4f, 43.3f to 5.4f, 42.4f to 3.1f, 43.4f to -1.8f
            )
        ),
        CountryOutlinePolygon(
            countryName = "Germany & Low Countries",
            isoCode = "DEU",
            coordinatesLatLon = listOf(
                51.0f to 2.5f, 53.5f to 6.8f, 54.8f to 8.4f, 54.4f to 13.6f,
                51.2f to 15.0f, 49.0f to 13.5f, 47.5f to 10.2f, 47.6f to 7.6f,
                49.0f to 8.1f, 50.8f to 6.0f, 51.0f to 2.5f
            )
        ),
        CountryOutlinePolygon(
            countryName = "Italy",
            isoCode = "ITA",
            coordinatesLatLon = listOf(
                45.8f to 6.8f, 46.5f to 11.4f, 45.6f to 13.8f, 44.0f to 12.6f,
                41.9f to 16.0f, 40.1f to 18.5f, 38.0f to 15.6f, 41.2f to 13.5f,
                43.7f to 10.3f, 44.4f to 8.9f, 45.8f to 6.8f
            )
        ),
        CountryOutlinePolygon(
            countryName = "Scandinavia (Norway, Sweden, Finland)",
            isoCode = "SCN",
            coordinatesLatLon = listOf(
                58.0f to 7.5f, 62.0f to 5.2f, 68.0f to 14.0f, 71.1f to 25.8f,
                69.8f to 30.0f, 65.8f to 29.5f, 60.5f to 27.5f, 60.2f to 22.0f,
                63.8f to 20.5f, 59.3f to 18.1f, 55.4f to 13.0f, 58.9f to 11.0f,
                58.0f to 7.5f
            )
        ),
        CountryOutlinePolygon(
            countryName = "Poland & Central Europe",
            isoCode = "POL",
            coordinatesLatLon = listOf(
                54.4f to 13.6f, 54.5f to 19.6f, 54.0f to 23.5f, 50.5f to 24.1f,
                47.8f to 22.8f, 45.5f to 16.5f, 47.5f to 10.2f, 49.0f to 13.5f,
                51.2f to 15.0f, 54.4f to 13.6f
            )
        ),
        CountryOutlinePolygon(
            countryName = "Ukraine & Balkans",
            isoCode = "UKR",
            coordinatesLatLon = listOf(
                51.5f to 24.0f, 52.2f to 33.5f, 49.5f to 40.0f, 47.1f to 39.0f,
                45.3f to 36.5f, 44.4f to 33.8f, 46.4f to 30.7f, 44.2f to 28.6f,
                41.0f to 29.0f, 38.0f to 23.7f, 40.5f to 19.5f, 45.5f to 13.8f,
                47.8f to 22.8f, 51.5f to 24.0f
            )
        ),
        CountryOutlinePolygon(
            countryName = "Russia & Northern Eurasia",
            isoCode = "RUS",
            coordinatesLatLon = listOf(
                60.0f to 30.0f, 69.0f to 33.0f, 68.5f to 44.0f, 70.0f to 60.0f,
                73.5f to 80.0f, 77.5f to 104.0f, 73.0f to 140.0f, 69.5f to 170.0f,
                66.0f to 179.0f, 60.0f to 165.0f, 52.5f to 158.0f, 59.5f to 143.0f,
                48.5f to 135.0f, 43.1f to 131.9f, 50.0f to 127.5f, 53.3f to 121.0f,
                50.0f to 105.0f, 52.0f to 88.0f, 55.0f to 73.0f, 51.0f to 58.0f,
                48.0f to 46.0f, 43.5f to 40.0f, 47.2f to 39.0f, 52.2f to 33.5f,
                56.0f to 28.0f, 60.0f to 30.0f
            )
        ),

        // ================= AFRICA =================
        CountryOutlinePolygon(
            countryName = "Morocco & Algeria",
            isoCode = "DZA",
            coordinatesLatLon = listOf(
                35.8f to -5.8f, 36.8f to 3.1f, 37.0f to 8.5f, 30.2f to 9.5f,
                23.5f to 12.0f, 19.0f to 3.0f, 25.0f to -6.5f, 21.0f to -17.0f,
                28.0f to -12.5f, 33.6f to -7.6f, 35.8f to -5.8f
            )
        ),
        CountryOutlinePolygon(
            countryName = "Libya & Tunisia",
            isoCode = "LBY",
            coordinatesLatLon = listOf(
                37.0f to 8.5f, 37.2f to 11.0f, 32.9f to 13.2f, 31.2f to 20.0f,
                32.1f to 24.9f, 22.0f to 25.0f, 19.5f to 24.0f, 23.5f to 12.0f,
                30.2f to 9.5f, 37.0f to 8.5f
            )
        ),
        CountryOutlinePolygon(
            countryName = "Egypt & Sudan",
            isoCode = "EGY",
            coordinatesLatLon = listOf(
                31.6f to 25.2f, 31.3f to 32.3f, 28.0f to 34.5f, 22.0f to 36.9f,
                18.0f to 38.5f, 12.0f to 36.0f, 9.5f to 28.0f, 19.5f to 24.0f,
                22.0f to 25.0f, 31.6f to 25.2f
            )
        ),
        CountryOutlinePolygon(
            countryName = "West Africa & Nigeria",
            isoCode = "NGA",
            coordinatesLatLon = listOf(
                21.0f to -17.0f, 25.0f to -6.5f, 19.0f to 3.0f, 15.0f to 14.0f,
                6.4f to 9.5f, 4.3f to 7.0f, 6.4f to 3.4f, 4.7f to -2.0f,
                4.4f to -7.5f, 8.5f to -13.2f, 14.7f to -17.4f, 21.0f to -17.0f
            )
        ),
        CountryOutlinePolygon(
            countryName = "Horn of Africa (Ethiopia, Somalia, Kenya)",
            isoCode = "ETH",
            coordinatesLatLon = listOf(
                18.0f to 38.5f, 11.8f to 43.4f, 11.9f to 51.3f, 2.0f to 45.3f,
                -4.6f to 39.2f, -4.0f to 30.0f, 4.5f to 34.0f, 12.0f to 36.0f,
                18.0f to 38.5f
            )
        ),
        CountryOutlinePolygon(
            countryName = "Central Africa (DR Congo, Angola, Tanzania)",
            isoCode = "COD",
            coordinatesLatLon = listOf(
                6.4f to 9.5f, 15.0f to 14.0f, 9.5f to 28.0f, 4.5f to 34.0f,
                -4.6f to 39.2f, -11.0f to 40.4f, -15.0f to 30.0f, -17.5f to 22.0f,
                -17.2f to 11.8f, -8.8f to 13.2f, -4.3f to 11.8f, 0.4f to 9.4f,
                6.4f to 9.5f
            )
        ),
        CountryOutlinePolygon(
            countryName = "South Africa, Namibia & Mozambique",
            isoCode = "ZAF",
            coordinatesLatLon = listOf(
                -17.2f to 11.8f, -17.5f to 22.0f, -15.0f to 30.0f, -11.0f to 40.4f,
                -20.0f to 35.0f, -26.0f to 32.6f, -29.8f to 31.0f, -34.2f to 25.6f,
                -34.8f to 20.0f, -33.9f to 18.4f, -28.6f to 16.5f, -22.6f to 14.5f,
                -17.2f to 11.8f
            )
        ),
        CountryOutlinePolygon(
            countryName = "Madagascar",
            isoCode = "MDG",
            coordinatesLatLon = listOf(
                -12.0f to 49.3f, -16.0f to 50.2f, -25.0f to 47.0f, -25.5f to 45.1f,
                -20.0f to 44.3f, -15.7f to 46.3f, -12.0f to 49.3f
            )
        ),

        // ================= MIDDLE EAST & ASIA =================
        CountryOutlinePolygon(
            countryName = "Turkey",
            isoCode = "TUR",
            coordinatesLatLon = listOf(
                41.2f to 29.0f, 42.0f to 35.0f, 41.0f to 41.5f, 39.5f to 44.5f,
                37.0f to 42.5f, 36.8f to 36.2f, 36.1f to 32.8f, 36.8f to 27.4f,
                38.4f to 26.3f, 40.1f to 26.2f, 41.2f to 29.0f
            )
        ),
        CountryOutlinePolygon(
            countryName = "Saudi Arabia & Arabian Peninsula",
            isoCode = "SAU",
            coordinatesLatLon = listOf(
                29.5f to 35.0f, 32.0f to 39.0f, 29.4f to 47.8f, 25.3f to 51.5f,
                25.2f to 55.3f, 22.5f to 59.8f, 17.0f to 54.1f, 12.8f to 45.0f,
                16.9f to 42.6f, 21.5f to 39.2f, 29.5f to 35.0f
            )
        ),
        CountryOutlinePolygon(
            countryName = "Iran & Iraq",
            isoCode = "IRN",
            coordinatesLatLon = listOf(
                37.0f to 42.5f, 39.5f to 44.5f, 38.4f to 48.9f, 36.8f to 54.0f,
                37.9f to 58.4f, 35.6f to 61.2f, 29.5f to 60.9f, 25.3f to 61.5f,
                26.5f to 56.3f, 29.0f to 50.8f, 30.0f to 48.0f, 29.4f to 47.8f,
                32.0f to 39.0f, 37.0f to 42.5f
            )
        ),
        CountryOutlinePolygon(
            countryName = "Kazakhstan & Central Asia",
            isoCode = "KAZ",
            coordinatesLatLon = listOf(
                51.0f to 51.0f, 55.0f to 73.0f, 50.0f to 85.0f, 43.0f to 80.0f,
                37.5f to 68.0f, 37.9f to 58.4f, 42.0f to 52.5f, 46.5f to 51.0f,
                51.0f to 51.0f
            )
        ),
        CountryOutlinePolygon(
            countryName = "India",
            isoCode = "IND",
            coordinatesLatLon = listOf(
                34.5f to 74.5f, 32.0f to 78.5f, 28.0f to 88.5f, 27.5f to 96.0f,
                22.5f to 88.4f, 19.8f to 85.8f, 16.5f to 82.2f, 13.1f to 80.3f,
                8.1f to 77.5f, 10.0f to 76.2f, 15.4f to 73.8f, 19.0f to 72.8f,
                22.5f to 69.0f, 24.0f to 68.8f, 28.0f to 70.5f, 31.5f to 74.5f,
                34.5f to 74.5f
            )
        ),
        CountryOutlinePolygon(
            countryName = "Pakistan & Afghanistan",
            isoCode = "PAK",
            coordinatesLatLon = listOf(
                35.6f to 61.2f, 37.5f to 68.0f, 36.8f to 74.5f, 31.5f to 74.5f,
                28.0f to 70.5f, 24.0f to 68.8f, 24.9f to 67.0f, 25.3f to 61.5f,
                29.5f to 60.9f, 35.6f to 61.2f
            )
        ),
        CountryOutlinePolygon(
            countryName = "China",
            isoCode = "CHN",
            coordinatesLatLon = listOf(
                43.0f to 80.0f, 49.0f to 87.8f, 43.0f to 96.0f, 43.5f to 111.5f,
                49.5f to 117.5f, 53.3f to 121.5f, 48.4f to 134.5f, 43.0f to 131.0f,
                40.0f to 124.3f, 38.9f to 121.6f, 39.0f to 117.7f, 36.1f to 120.4f,
                31.2f to 121.5f, 24.5f to 118.1f, 22.3f to 114.2f, 20.0f to 110.2f,
                21.5f to 108.0f, 22.5f to 100.0f, 27.5f to 96.0f, 28.0f to 88.5f,
                32.0f to 78.5f, 36.8f to 75.5f, 40.0f to 74.0f, 43.0f to 80.0f
            )
        ),
        CountryOutlinePolygon(
            countryName = "Mongolia",
            isoCode = "MNG",
            coordinatesLatLon = listOf(
                49.0f to 87.8f, 51.5f to 100.0f, 50.0f to 115.0f, 46.5f to 119.8f,
                43.5f to 111.5f, 43.0f to 96.0f, 49.0f to 87.8f
            )
        ),
        CountryOutlinePolygon(
            countryName = "Korean Peninsula",
            isoCode = "KOR",
            coordinatesLatLon = listOf(
                40.0f to 124.3f, 42.3f to 130.6f, 38.0f to 128.8f, 35.1f to 129.0f,
                34.5f to 126.2f, 37.5f to 126.7f, 40.0f to 124.3f
            )
        ),
        CountryOutlinePolygon(
            countryName = "Japan",
            isoCode = "JPN",
            coordinatesLatLon = listOf(
                45.5f to 141.9f, 43.3f to 145.6f, 41.5f to 140.9f, 38.3f to 141.0f,
                35.7f to 140.8f, 34.7f to 135.5f, 33.6f to 130.4f, 31.6f to 130.6f,
                35.5f to 133.5f, 37.9f to 139.0f, 41.4f to 140.2f, 45.5f to 141.9f
            )
        ),
        CountryOutlinePolygon(
            countryName = "Southeast Asia (Myanmar, Thailand, Vietnam)",
            isoCode = "VNM",
            coordinatesLatLon = listOf(
                27.5f to 96.0f, 22.5f to 100.0f, 21.5f to 108.0f, 16.0f to 108.2f,
                10.8f to 106.7f, 8.6f to 104.7f, 13.7f to 100.5f, 7.0f to 100.5f,
                1.3f to 103.8f, 5.4f to 100.3f, 16.8f to 96.2f, 22.0f to 92.2f,
                27.5f to 96.0f
            )
        ),
        CountryOutlinePolygon(
            countryName = "Indonesia & Borneo",
            isoCode = "IDN",
            coordinatesLatLon = listOf(
                5.5f to 95.3f, 1.0f to 104.0f, -6.2f to 106.8f, -7.8f to 114.4f,
                -8.7f to 115.2f, -3.5f to 114.6f, 1.2f to 118.5f, 6.0f to 116.5f,
                1.5f to 110.3f, -3.0f to 104.8f, -5.9f to 105.8f, 5.5f to 95.3f
            )
        ),
        CountryOutlinePolygon(
            countryName = "Philippines",
            isoCode = "PHL",
            coordinatesLatLon = listOf(
                18.5f to 121.0f, 14.6f to 121.0f, 13.0f to 124.0f, 7.1f to 125.6f,
                6.9f to 122.1f, 10.3f to 123.9f, 16.0f to 120.0f, 18.5f to 121.0f
            )
        ),

        // ================= OCEANIA =================
        CountryOutlinePolygon(
            countryName = "Australia",
            isoCode = "AUS",
            coordinatesLatLon = listOf(
                -12.5f to 130.8f, -12.0f to 136.8f, -16.5f to 139.5f, -10.7f to 142.5f,
                -16.9f to 145.8f, -23.5f to 150.8f, -27.5f to 153.0f, -33.9f to 151.2f,
                -37.8f to 149.0f, -37.8f to 145.0f, -34.9f to 138.6f, -32.0f to 132.5f,
                -34.0f to 122.0f, -35.0f to 117.9f, -32.0f to 115.8f, -25.8f to 113.5f,
                -21.9f to 114.1f, -18.0f to 122.2f, -14.5f to 126.0f, -12.5f to 130.8f
            )
        ),
        CountryOutlinePolygon(
            countryName = "New Zealand",
            isoCode = "NZL",
            coordinatesLatLon = listOf(
                -34.4f to 172.7f, -36.8f to 174.8f, -39.5f to 176.9f, -41.3f to 174.8f,
                -43.5f to 172.6f, -46.4f to 168.3f, -44.6f to 167.9f, -40.5f to 172.7f,
                -39.0f to 174.1f, -34.4f to 172.7f
            )
        )
    )
}
