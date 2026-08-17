package com.example.audioambientglow.audio

data class AudioFeatures(
    val rawRms: Float = 0f,
    val bassEnergy: Float = 0f,       // 20Hz ~ 250Hz (Drives acceleration & pulse)
    val midEnergy: Float = 0f,        // 250Hz ~ 2000Hz (Drives dynamic hue)
    val trebleEnergy: Float = 0f,     // 2000Hz ~ 16000Hz (Drives sparkle/waves)
    val spectrumBands: FloatArray = FloatArray(32) { 0f } // 32 Equalizer display bands
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AudioFeatures
        return rawRms == other.rawRms &&
                bassEnergy == other.bassEnergy &&
                midEnergy == other.midEnergy &&
                trebleEnergy == other.trebleEnergy &&
                spectrumBands.contentEquals(other.spectrumBands)
    }

    override fun hashCode(): Int {
        var result = rawRms.hashCode()
        result = 31 * result + bassEnergy.hashCode()
        result = 31 * result + midEnergy.hashCode()
        result = 31 * result + trebleEnergy.hashCode()
        result = 31 * result + spectrumBands.contentHashCode()
        return result
    }
}
