[MeasureAudioRaw]
Measure=Plugin
Plugin=AudioLevel
Port=Output
FFTSize=1024
FFTAttack=#AudioAttack#
FFTDecay=#AudioDecay#
Bands=4
FreqMin=20
FreqMax=16000
Sensitivity=#AudioSensitivity#

[MeasureBass]
Measure=Plugin
Plugin=AudioLevel
Parent=MeasureAudioRaw
Type=Band
BandIdx=0

[MeasureMid]
Measure=Plugin
Plugin=AudioLevel
Parent=MeasureAudioRaw
Type=Band
BandIdx=1

[MeasureHigh]
Measure=Plugin
Plugin=AudioLevel
Parent=MeasureAudioRaw
Type=Band
BandIdx=2

; Weighted Audio Energy
[MeasureAudioEnergy]
Measure=Calc
Formula=Clamp(([MeasureBass] * 0.85 + [MeasureMid] * 0.15), 0, 1.0)
DynamicVariables=1

; Smooth Energy
[MeasureSmoothEnergy]
Measure=Calc
Formula=MeasureAudioEnergy
AverageSize=2

; Dynamic Core Glow Depth
[MeasureCurrentDepth]
Measure=Calc
Formula=Trunc(#GlowBaseDepth# + [MeasureSmoothEnergy] * #GlowMaxExpand#)
DynamicVariables=1

; Dynamic Ambient Glow Depth
[MeasureCurrentWideDepth]
Measure=Calc
Formula=Trunc([MeasureCurrentDepth] * #GlowDiffusionScale#)
DynamicVariables=1
