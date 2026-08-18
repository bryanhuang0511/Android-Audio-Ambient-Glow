-- Factory.lua
-- Rainbow moving bars visualizer for Rainmeter
-- Author: OpenAI adaptation for user

local totalBars = 64     -- 總 bar 數
local speed = 0.5        -- 變色速度（可調）

-- HSV轉RGB函數
local function HSVtoRGB(h, s, v)
    local r, g, b

    local i = math.floor(h * 6)
    local f = h * 6 - i
    local p = v * (1 - s)
    local q = v * (1 - f * s)
    local t = v * (1 - (1 - f) * s)

    i = i % 6

    if i == 0 then r, g, b = v, t, p
    elseif i == 1 then r, g, b = q, v, p
    elseif i == 2 then r, g, b = p, v, t
    elseif i == 3 then r, g, b = p, q, v
    elseif i == 4 then r, g, b = t, p, v
    elseif i == 5 then r, g, b = v, p, q
    end

    return math.floor(r * 255), math.floor(g * 255), math.floor(b * 255)
end

function Initialize()
    -- 可以放初始化設定
end

function Update()
    local t = os.clock() * speed  -- 全局時間增量

    for i = 0, totalBars-1 do
        local hueStep = 1 / totalBars
        local hue = (i * hueStep + t) % 1
        local r, g, b = HSVtoRGB(hue, 1, 1)

        local colorStr = string.format("%d,%d,%d,255", r, g, b)
        local sectionName = "MeterBar" .. i

        SKIN:Bang('[!SetOption "'..sectionName..'" "BarColor" "'..colorStr..'"][!UpdateMeter "'..sectionName..'"]')
    end
end
