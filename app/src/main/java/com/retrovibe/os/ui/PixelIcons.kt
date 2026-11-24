package com.retrovibe.os.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private data class PixelSpec(val palette: Map<Char, Color>, val grid: List<String>)

private fun hex(v: Long) = Color(v)

private val ICONS: Map<String, PixelSpec> = mapOf(
    "computer" to PixelSpec(
        mapOf('k' to hex(0xFF111111), 'w' to hex(0xFFFFFFFF), 'g' to hex(0xFFC0C0C0), 'd' to hex(0xFF808080), 'b' to hex(0xFF3B6EA5), 'c' to hex(0xFF78B7E6), 'p' to hex(0xFFF29EB6), 'y' to hex(0xFF7BF088)),
        listOf(
            "................", "..kkkkkkkkkkkk..", ".kwwwwwwwwwwwwk.", ".kwkkkkkkkkkkdk.",
            ".kwkbbbbbbbbkdk.", ".kwkbwbbbbwbkdk.", ".kwkbbccccbbkdk.", ".kwkbbppppbbkdk.",
            ".kwkbbbbbbbbkdk.", ".kwkkkkkkkkkkdk.", ".kddddddddddydk.", "..kkkkkkkkkkkk..",
            ".....kggggk.....", "..kkkkkkkkkkkk..", ".kwwwwwwwwwwwwk.", ".kkkkkkkkkkkkkk."
        )
    ),
    "folder" to PixelSpec(
        mapOf('k' to hex(0xFF111111), 'y' to hex(0xFFF5D796), 'l' to hex(0xFFFFEFC2), 'd' to hex(0xFFC9A35B), 'p' to hex(0xFFE882A2)),
        listOf(
            "................", "................", ".kkkkkk.........", "kllllllk........",
            "klyyyyykkkkkkkk.", "klllllllllllllk.", "klyyyyyyyyyyyyk.", "klyyyyyyyyyyyyk.",
            "klyyyyyypypyyyk.", "klyyyyyypppyyyk.", "klyyyyyyypyyyyk.", "klyyyyyyyyyyyyk.",
            "kdddddddddddddk.", ".kkkkkkkkkkkkk..", "................", "................"
        )
    ),
    "note" to PixelSpec(
        mapOf('k' to hex(0xFF111111), 'w' to hex(0xFFFFFDF5), 'g' to hex(0xFFC8C4B8), 't' to hex(0xFF3B6EA5), 'l' to hex(0xFF6CA0D6), 'p' to hex(0xFFE67E9F), 'd' to hex(0xFF7A766C)),
        listOf(
            "..k.k.k.k.k.k...", "..kkkkkkkkkkk...", ".ktttttttttttk..", ".klllllllllllk..",
            ".kwwwwwwwwwwwk..", ".kwdddddddddwk..", ".kwwwwwwwwwwwk..", ".kwdddddddddgk..",
            ".kwwwwwwwwwwwk..", ".kwdddddddddwk..", ".kwwwwwwwwwwwk..", ".kwpppppwwwwgk..",
            ".kwwwwwwwwwwwk..", ".kgggggggggggk..", "..kkkkkkkkkkk...", "................"
        )
    ),
    "gallery" to PixelSpec(
        mapOf('k' to hex(0xFF111111), 'f' to hex(0xFFF2B1C4), 's' to hex(0xFF2C4A7A), 'm' to hex(0xFFFFE875), 'c' to hex(0xFFD8E6F5), 'g' to hex(0xFF6CB878)),
        listOf(
            "................", ".kkkkkkkkkkkkkk.", "kffffffffffffffk", "kfkkkkkkkkkkkkfk",
            "kfksssssssssskfk", "kfksmsmsmsssskfk", "kfkssmmsssssskfk", "kfksssssssssskfk",
            "kfkssccsssssskfk", "kfkscggcssccskfk", "kfkgcggggcgggkfk", "kfkggggggggggkfk",
            "kfkkkkkkkkkkkkfk", "kffffffffffffffk", ".kkkkkkkkkkkkkk.", "................"
        )
    ),
    "music" to PixelSpec(
        mapOf('k' to hex(0xFF111111), 'w' to hex(0xFFF8F4EB), 'g' to hex(0xFFC0C0C0), 'p' to hex(0xFFE26A95), 'l' to hex(0xFFF7A8C4)),
        listOf(
            "..kkkkkkkkk.....", "..kwwwwwwwkk....", "..kwwwwwwwkgk...", "..kwwwwwwwkggk..",
            "..kwwwwppkkkkk..", "..kwwwpppwwwwk..", "..kwwwpwwpwwwk..", "..kwwwpwwpwwwk..",
            "..kwwwpwwpwwwk..", "..kwpppwwpwwwk..", "..kwplpwppwwwk..", "..kwpppwplpwwk..",
            "..kwwwwwpppwwk..", "..kggggggggggk..", "..kkkkkkkkkkkk..", "................"
        )
    ),
    "paint" to PixelSpec(
        mapOf(
            'k' to hex(0xFF111111), 'w' to hex(0xFFFFFFFF), 'l' to hex(0xFFDCDCDC),
            'g' to hex(0xFFA6A6A6), 'd' to hex(0xFF686868), 'm' to hex(0xFFD820C8),
            'r' to hex(0xFFE01818), 'c' to hex(0xFF00B8D8), 'y' to hex(0xFFE8D818),
            'b' to hex(0xFF1838D0)
        ),
        listOf(
            "........kk......", ".....kk.kk......", ".kk..kg.kg......", "..kmkrrkcck.kk..",
            "...mkrrkcckykgk.", "...bkrrkcckykbk.", "....krrkccyykb..", "...dddddddddd...",
            "..dwwwwlllggdk..", "..dggggddddddk..", "...dwwwlllggdk..", "...dwwwlllggdk..",
            "...dwwwlllggdk..", "...dwwwlllggdk..", "...dwwwlllggdk..", "....kkkkkkkkk..."
        )
    ),
    "calendar" to PixelSpec(
        mapOf('k' to hex(0xFF111111), 'w' to hex(0xFFFFFFFF), 'b' to hex(0xFF2E4C89), 'g' to hex(0xFFC0C0C0), 'p' to hex(0xFFE67E9F), 'd' to hex(0xFF666666)),
        listOf(
            "...kk......kk...", "..kggk....kggk..", ".kkkkkkkkkkkkkk.", ".kbbbbbbbbbbbbk.",
            ".kbbbbbbbbbbbbk.", ".kkkkkkkkkkkkkk.", ".kwwwwwwwwwwwwk.", ".kwddwddwddwddk.",
            ".kwwwwwwwwwwwwk.", ".kwddwppwppwddk.", ".kwwwwppppwwwwk.", ".kwddwwppwwddwk.",
            ".kwwwwwwwwwwwwk.", ".kggggggggggggk.", ".kkkkkkkkkkkkkk.", "................"
        )
    ),
    "reminder" to PixelSpec(
        mapOf('k' to hex(0xFF111111), 'y' to hex(0xFFF7D259), 'l' to hex(0xFFFFF2A6), 'd' to hex(0xFFC49622)),
        listOf(
            ".......kk.......", "......kyyk......", ".....kyllkk.....", "....kylyyyyk....",
            "....kylyyyyk....", "...kylyyyyyyk...", "...kylyyyyyyk...", "...kyyyyyyyyk...",
            "..kyyyyyyyyyyk..", "..kddddddddddk..", ".kkkkkkkkkkkkkk.", "......kddk......",
            ".......kk.......", "................", "................", "................"
        )
    ),
    "habits" to PixelSpec(
        mapOf('k' to hex(0xFF111111), 'b' to hex(0xFFC48F5A), 'd' to hex(0xFF8F6033), 'g' to hex(0xFFC0C0C0), 'w' to hex(0xFFFFFDF8), 'p' to hex(0xFFE66A94), 'l' to hex(0xFF6A6A6A)),
        listOf(
            ".....kkkkkk.....", "....kggggggk....", "..kkkggggggkkk..", ".kbbkkkkkkkkbbk.",
            ".kbkwwwwwwwwkbk.", ".kbkwpwwllllkbk.", ".kbkwppwwwwwkbk.", ".kbkwwwwwwwwkbk.",
            ".kbkwpwwllllkbk.", ".kbkwppwwwwwkbk.", ".kbkwwwwwwwwkbk.", ".kbkwkwwllllkbk.",
            ".kbkwwwwwwwwkbk.", ".kddkkkkkkkkddk.", "..kkkkkkkkkkkk..", "................"
        )
    ),
    "mic" to PixelSpec(
        mapOf('k' to hex(0xFF111111), 'g' to hex(0xFFC0C0C0), 'd' to hex(0xFF707070), 'w' to hex(0xFFFFFFFF)),
        listOf(
            "......kkkk......", ".....kwwwwk.....", "....kwgggggk....", "....kwgdgdgk....",
            "....kwgggggk....", "....kwgdgdgk....", "..kkkggggggkkk..", "..k.kkddddkk.k..",
            "..k...kkkk...k..", "...kk......kk...", "....kkkkkkkk....", ".......kk.......",
            ".......kk.......", "....kkkkkkkk....", "....kggggggk....", "....kkkkkkkk...."
        )
    ),
    "recycle" to PixelSpec(
        mapOf('k' to hex(0xFF111111), 'w' to hex(0xFFFFFFFF), 'g' to hex(0xFFD0D8DE), 'd' to hex(0xFF849099), 'b' to hex(0xFFA8B4BE), 'e' to hex(0xFF2E9E44), 'l' to hex(0xFF62D176)),
        listOf(
            "................", "..kkkkkkkkkkkk..", ".kwwwwwwwwwwwwk.", ".kbbbbbbbbbbbbk.",
            "..kkkkkkkkkkkk..", "..kwggwggwggdk..", "..kwggeeggwgdk..", "..kwgellleggdk..",
            "..kwgegggeggdk..", "..kwgellleggdk..", "...kwgeeeegdk...", "...kwggggggdk...",
            "...kbbggggddk...", "...kddddddddk...", "....kkkkkkkk....", "................"
        )
    ),
    "globe" to PixelSpec(
        mapOf('k' to hex(0xFF111111), 'b' to hex(0xFF4A8CD4), 'l' to hex(0xFF8AC4F5), 'g' to hex(0xFF52B862), 'd' to hex(0xFF2C5C94)),
        listOf(
            ".....kkkkkk.....", "...kkllbbggkk...", "..kllbbggggbbk..", ".klbbbggggbbbbk.",
            ".kbbgggbbbbbbbk.", "kbbbggbbbbggggbk", "kbbbbbbbbgggggbk", "kbbbbbbbbggggdbk",
            "kbbbggbbbbggddbk", "kbbggggbbbbdddbk", ".kbbggbbbbddddk.", ".kbbbbbbddddddk.",
            "..kbbbbddddddk..", "...kkddddddkk...", ".....kkkkkk.....", "................"
        )
    ),
    "mail" to PixelSpec(
        mapOf('k' to hex(0xFF111111), 'w' to hex(0xFFFFFDF6), 'g' to hex(0xFFD6D0C4)),
        listOf(
            "................", "................", "................", ".kkkkkkkkkkkkkk.",
            "kwwwwwwwwwwwwwwk", "kwkwwwwwwwwwwkwk", "kwwkwwwwwwwwkwwk", "kwwwkwwwwwwkwwwk",
            "kwwwwkwwwwkwwwwk", "kwwwwwkkkkwwwwwk", "kwwwkkwwwwkkwwwk", "kwwkggggggggkwwk",
            "kggggggggggggggk", ".kkkkkkkkkkkkkk.", "................", "................"
        )
    ),
    "settings" to PixelSpec(
        mapOf('k' to hex(0xFF111111), 'w' to hex(0xFFFFFFFF), 'g' to hex(0xFFD6D2C4), 'd' to hex(0xFF808080), 'c' to hex(0xFF48A8D8), 'y' to hex(0xFFFFD84D), 'p' to hex(0xFFF06292), 'b' to hex(0xFF2E5CB8)),
        listOf(
            "................", ".kkkkkkkkkkkkkk.", "kwwwwwwwwwwwwwwk", "kwkkkkkkkkkkkkdk",
            "kwkccccyyyycckdk", "kwkcccyyyyycckdk", "kwkccppppppcckdk", "kwkccppbbppcckdk",
            "kwkccppbbppcckdk", "kwkkkkkkkkkkkkdk", "kgggggggggggggdk", ".kkkkkkkkkkkkkk.",
            ".....kggggk.....", "...kkkkkkkkkk...", "..kggggggggggk..", "..kkkkkkkkkkkk.."
        )
    ),
    "heart" to PixelSpec(
        mapOf('k' to hex(0xFF111111), 'p' to hex(0xFFF29EB6), 'l' to hex(0xFFFFD6E2), 'd' to hex(0xFFD46A8C), 'w' to hex(0xFFFFFFFF)),
        listOf(
            "................", "..kkkk....kkkk..", ".kppppk..kppppk.", "kplwpppkpppppddk",
            "kpwwpppppppppddk", "kppppppppppppddk", "kppppppppppppddk", ".kppppppppppddk.",
            "..kppppppppddk..", "...kppppppddk...", "....kppppddk....", ".....kppddk.....",
            "......kddk......", ".......kk.......", "................", "................"
        )
    ),
    "star" to PixelSpec(
        mapOf('k' to hex(0xFF111111), 'y' to hex(0xFFFFD84D), 'l' to hex(0xFFFFF4A3), 'd' to hex(0xFFD49E1C)),
        listOf(
            ".......kk.......", "......klyk......", "......klyk......", ".....klyyyk.....",
            ".kkkkklyyykkkkk.", "klllyyyyyyyyyddk", ".klyyyyyyyyyddk.", "..klyyyyyyyddk..",
            "...klyyyyyddk...", "...klyyyyyddk...", "..klyykkkkdddk..", "..klyk....kddk..",
            ".klyk......kddk.", ".kkk........kkk.", "................", "................"
        )
    ),
    "cake" to PixelSpec(
        mapOf('k' to hex(0xFF111111), 'y' to hex(0xFFFFB833), 'b' to hex(0xFF6BA4E0), 'w' to hex(0xFFFFFFFF), 'p' to hex(0xFFF5B0C6), 'd' to hex(0xFFD97E9C)),
        listOf(
            ".......kk.......", "......kyyk......", ".......kk.......", "......kbbk......",
            "......kbbk......", "..kkkkkkkkkkkk..", ".kwwwwwwwwwwwwk.", ".kwpwwpwwpwwpwk.",
            ".kppppppppppppk.", ".kppppppppppppk.", ".kddddddddddddk.", ".kppppppppppppk.",
            ".kddddddddddddk.", ".kkkkkkkkkkkkkk.", "................", "................"
        )
    ),
    "sparkle" to PixelSpec(
        mapOf('k' to hex(0xFF111111), 'c' to hex(0xFF8CE4FF), 'w' to hex(0xFFFFFFFF), 'b' to hex(0xFF4AA8E0)),
        listOf(
            ".......kk.......", "......kcck......", "......kcwk......", ".....kccwck.....",
            "....kccwwcbk....", "...kccwwwwcbk...", ".kkccwwwwwwcbkk.", "kccwwwwwwwwwwcbk",
            ".kkccwwwwwwcbkk.", "...kccwwwwcbk...", "....kccwwcbk....", ".....kccwck.....",
            "......kcbk......", "......kbbk......", ".......kk.......", "................"
        )
    ),
    "speakerOn" to PixelSpec(
        mapOf('k' to hex(0xFF111111), 'g' to hex(0xFFC0C0C0), 'd' to hex(0xFF666666), 'b' to hex(0xFF2E4C89)),
        listOf(
            "................", ".......kk.......", "......kgk...b...", ".....kggk....b..",
            "..kkkgggk.b...b.", "..kgggggk..b..b.", "..kgggggk..b..b.", "..kgggggk..b..b.",
            "..kkkdddk.b...b.", ".....kddk....b..", "......kdk...b...", ".......kk.......",
            "................", "................", "................", "................"
        )
    ),
    "speakerOff" to PixelSpec(
        mapOf('k' to hex(0xFF111111), 'g' to hex(0xFFC0C0C0), 'd' to hex(0xFF666666), 'r' to hex(0xFFD62828)),
        listOf(
            "................", ".......kk.......", "......kgk.......", ".....kggk.r...r.",
            "..kkkgggk..r.r..", "..kgggggk...r...", "..kgggggk..r.r..", "..kgggggk.r...r.",
            "..kkkdddk.......", ".....kddk.......", "......kdk.......", ".......kk.......",
            "................", "................", "................", "................"
        )
    ),
    "pin" to PixelSpec(
        mapOf('k' to hex(0xFF111111), 'r' to hex(0xFFE04868), 'l' to hex(0xFFFF9EB4), 'g' to hex(0xFF808080)),
        listOf(
            "................", "......kkkk......", ".....kllrrk.....", ".....krrrrk.....",
            "......krrk......", "......krrk......", "....kkrrrrkk....", "...kllrrrrrrk...",
            "...kkkkkkkkkk...", ".......kk.......", ".......kg.......", ".......kg.......",
            ".......kg.......", ".......k........", "................", "................"
        )
    ),
    "trash" to PixelSpec(
        mapOf('k' to hex(0xFF111111), 'g' to hex(0xFFC0C0C0), 'd' to hex(0xFF707070)),
        listOf(
            ".....kkkkkk.....", "..kkkkkkkkkkkk..", "..kggggggggggk..", "...kkkkkkkkkk...",
            "...kgdgkgdgdk...", "...kgdgkgdgdk...", "...kgdgkgdgdk...", "...kgdgkgdgdk...",
            "...kgdgkgdgdk...", "...kgdgkgdgdk...", "...kgdgkgdgdk...", "...kddddddddk...",
            "....kkkkkkkk....", "................", "................", "................"
        )
    ),
    "plus" to PixelSpec(
        mapOf('k' to hex(0xFF111111)),
        listOf(
            "................", "................", "......kkkk......", "......kkkk......",
            "......kkkk......", "......kkkk......", "..kkkkkkkkkkkk..", "..kkkkkkkkkkkk..",
            "..kkkkkkkkkkkk..", "..kkkkkkkkkkkk..", "......kkkk......", "......kkkk......",
            "......kkkk......", "......kkkk......", "................", "................"
        )
    ),
    "edit" to PixelSpec(
        mapOf('k' to hex(0xFF111111), 'y' to hex(0xFFF2C94C), 'p' to hex(0xFFF29EB6), 'g' to hex(0xFFC0C0C0)),
        listOf(
            "..........kkk...", ".........kpppk..", "........kppkpk..", ".......kggkpk...",
            "......kyygkk....", ".....kyyyyk.....", "....kyyyyk......", "...kyyyyk.......",
            "..kyyyyk........", "..kggyk.........", ".kgggk..........", ".kkkg...........",
            ".kk.............", "................", "................", "................"
        )
    ),
    "winFlag" to PixelSpec(
        mapOf('k' to hex(0xFF111111), 'r' to hex(0xFFE04040), 'g' to hex(0xFF40B048), 'b' to hex(0xFF3070D8), 'y' to hex(0xFFF0C830)),
        listOf(
            "................", "....kkkk.kkkk...", "...krrrk.kgggk..", "...krrrk.kgggk..",
            "...krrrk.kgggk..", "...kkkkk.kkkkk..", "................", "..kkkkk.kkkkk...",
            "..kbbbk.kyyyk...", "..kbbbk.kyyyk...", "..kbbbk.kyyyk...", "..kkkkk.kkkkk...",
            "................", "................", "................", "................"
        )
    ),
    "cat" to PixelSpec(
        mapOf('k' to hex(0xFF111111), 'o' to hex(0xFFF4B878), 'l' to hex(0xFFFFE2BD), 'p' to hex(0xFFF28FA8)),
        listOf(
            "................", "..kk........kk..", ".kpok......kopk.", ".koookkkkkkoook.",
            ".kooooooooooook.", ".kookoookoookok.", ".kooopoooopoook.", ".kooooookoooook.",
            "..kllllllllllk..", "..koooooooooook.", "..koooooooooook.", "...klllllllllk..",
            "...kkkkkkkkkkk..", "................", "................", "................"
        )
    ),
    "calc" to PixelSpec(
        mapOf('k' to hex(0xFF111111), 'g' to hex(0xFFC0C0C0), 'd' to hex(0xFF707070), 'c' to hex(0xFF9CD6B8), 'r' to hex(0xFFD83040), 'b' to hex(0xFF2B5CC4), 'w' to hex(0xFFFFFFFF)),
        listOf(
            "..kkkkkkkkkkkk..", ".kggggggggggggk.", ".kgkkkkkkkkkkgk.", ".kgkcccccccckgk.",
            ".kgkkkkkkkkkkgk.", ".kggggggggggggk.", ".kgbbwbbwbbwrrk.", ".kgbbwbbwbbwrrk.",
            ".kggggggggggggk.", ".kgbbwbbwbbwddk.", ".kgbbwbbwbbwddk.", ".kggggggggggggk.",
            ".kgbbwbbwbbwddk.", ".kddddddddddddk.", "..kkkkkkkkkkkk..", "................"
        )
    ),
    "terminal" to PixelSpec(
        mapOf('k' to hex(0xFF111111), 'b' to hex(0xFF2E4C89), 'g' to hex(0xFF40F070), 'd' to hex(0xFFC0C0C0)),
        listOf(
            "................", ".kkkkkkkkkkkkkk.", ".kbbbbbbbbbbbbk.", ".kkkkkkkkkkkkkk.",
            ".kddddddddddddk.", ".kdkkkkkkkkkkdk.", ".kdkgkkkkkkkkdk.", ".kdkkggkkgggkdk.",
            ".kdkgkkkkkkkkdk.", ".kdkkkkkkkkkkdk.", ".kdkggggggkkkdk.", ".kdkkkkkkkkkkdk.",
            ".kddddddddddddk.", ".kkkkkkkkkkkkkk.", "................", "................"
        )
    ),
    "run" to PixelSpec(
        mapOf('k' to hex(0xFF111111), 'b' to hex(0xFF3070D8), 'w' to hex(0xFFFFFFFF), 'g' to hex(0xFFC0C0C0), 'y' to hex(0xFFFFD440)),
        listOf(
            "................", ".....kkkkkkkkkk.", "....kbbbbbbbbbk.", "....kwwwwwwwwwk.",
            ".kkk.kwwwwwwwwk.", "kyyykkwwwwwggwk.", "kggyyyyywwwggwk.", ".kkkkkkyywwwwwk.",
            "....kwyywwwwwwk.", "....kwwwwwwwwwk.", "....kgggggggggk.", "....kkkkkkkkkkk.",
            "................", "................", "................", "................"
        )
    ),
    "taskmgr" to PixelSpec(
        mapOf('k' to hex(0xFF111111), 'd' to hex(0xFF102818), 'g' to hex(0xFF3CF068), 'c' to hex(0xFFC0C0C0), 'b' to hex(0xFF2E4C89)),
        listOf(
            "................", ".kkkkkkkkkkkkkk.", ".kbbbbbbbbbbbbk.", ".kcccccccccccck.",
            ".kckddddddddkck.", ".kckddddddgdkck.", ".kckdddddgddkck.", ".kckddgddgddkck.",
            ".kckdgddgddgkck.", ".kckgddgddddkck.", ".kckddddddddkck.", ".kckkkkkkkkkkck.",
            ".kcccccccccccck.", ".kkkkkkkkkkkkkk.", "................", "................"
        )
    ),
    "shutdown" to PixelSpec(
        mapOf('k' to hex(0xFF111111), 'r' to hex(0xFFE03850)),
        listOf(
            "......kkkk......", "......krrk......", "...kk.krrk.kk...", "..krrkkrrkkrrk..",
            ".krrk.krrk.krrk.", ".krk..krrk..krk.", "krk...krrk...krk", "krk...kkkk...krk",
            "krk..........krk", ".krk........krk.", ".krrk......krrk.", "..krrkkkkkkrrk..",
            "...krrrrrrrrk...", "....kkkkkkkk....", "................", "................"
        )
    ),
    "showDesktop" to PixelSpec(
        mapOf('k' to hex(0xFF111111), 't' to hex(0xFF008080), 'w' to hex(0xFFFFFFFF), 'b' to hex(0xFF2E4C89)),
        listOf(
            "................", ".kkkkkkkkkkkkkk.", ".kttttttttttttk.", ".kttttttttttttk.",
            ".kttkkkkkkttttk.", ".kttkbbbbkttttk.", ".kttkwwwwkttttk.", ".kttkwwwwkttttk.",
            ".kttkkkkkkttttk.", ".kttttttttttttk.", ".kttttttttttttk.", ".kwwwwwwwwwwwwk.",
            ".kbbwwwwwwwwwwk.", ".kkkkkkkkkkkkkk.", "................", "................"
        )
    ),
    "flag" to PixelSpec(
        mapOf('k' to hex(0xFF3B0A22), 'd' to hex(0xFF8A1A48), 'p' to hex(0xFFFF2D6F), 'l' to hex(0xFFFF6EA0), 'w' to hex(0xFFFFFFFF), 'y' to hex(0xFFFFD700)),
        listOf(
            "........yy......", ".......kk.......", "......pkk.......", "....pppkk.......",
            "..ppplpkk.......", ".ppplwpkk.......", "..pppppkk.......", "....pppkk.......",
            "......pkk.......", ".......kk.......", ".......kk.......", ".....dddddd.....",
            "...kkkkkkkkkk...", "...kkkkkkkkkk...", "................", "................"
        )
    ),
    "brokenHeart" to PixelSpec(
        mapOf('k' to hex(0xFF45081E), 'p' to hex(0xFFFF2D6F), 'd' to hex(0xFFC2185B), 'w' to hex(0xFFFFFFFF)),
        listOf(
            "................", "..kkkk....kkkk..", ".kppppk..kddddk.", "kpwpppk..kdddddk",
            "kppppk..kddddddk", "kpppppk..kdddddk", "kppppk..kddddddk", ".kppppk..kddddk.",
            "..kpppk..kdddk..", "...kpppk..kdk...", "....kppk.kdk....", ".....kpk.kk.....",
            "......kk.k......", "................", "................", "................"
        )
    ),
    "wonHeart" to PixelSpec(
        mapOf('k' to hex(0xFF3B0A22), 'p' to hex(0xFFFF2D6F), 'w' to hex(0xFFFFFFFF), 'y' to hex(0xFFFFD700)),
        listOf(
            "...........yy...", "..kkkk....yyyy..", ".kppppk..kyyyyk.", "kpwpppppkkpyyppk",
            "kppppppppppppppk", "kppppppppppppppk", "kppppppppppppppk", ".kppppppppppppk.",
            "..kppppppppppk..", "...kppppppppk...", "....kppppppk....", ".....kppppk.....",
            "......kppk......", ".......kk.......", "................", "................"
        )
    ),
    "wrongFlag" to PixelSpec(
        mapOf('k' to hex(0xFF7A3350), 'p' to hex(0xFFD87A9B), 'r' to hex(0xFFD81B3A)),
        listOf(
            ".rr..........rr.", "..rrkk....kkrr..", "..krrpk..kprrk..", ".kppprrkkrpppk..",
            ".kpppprrrrpppk..", ".kppppprrppppk..", ".kpppprrrrpppk..", "..kpprrkkrrpk...",
            "..kprrk..krrk...", "..rrppk..kpprr..", ".rr.kppkkppk.rr.", ".....kppppk.....",
            "......kppk......", ".......kk.......", "................", "................"
        )
    ),
    // All 16 MS Paint tool icons
    "toolFreeSelect" to PixelSpec(mapOf('k' to Color.Black), listOf("................", ".......k........", "......k.k.......", ".....k...k......", ".k.k.k...k.k.k..", "..k.........k...", "...k.......k....", "....k.....k.....", ".....k...k......", "....k.....k.....", "...k..k.k..k....", "..k.k.....k.k...", "..k.........k...", "................", "................", "................")),
    "toolSelect" to PixelSpec(mapOf('k' to Color.Black), listOf("................", "................", "..kk.kk.kk.kk...", "..k.........k...", "................", "..k.........k...", "..k.........k...", "................", "..k.........k...", "..k.........k...", "................", "..k.........k...", "..kk.kk.kk.kk...", "................", "................", "................")),
    "toolEraser" to PixelSpec(mapOf('k' to Color.Black, 'y' to hex(0xFFF5EB64), 'w' to Color.White, 'g' to hex(0xFFC0C0C0)), listOf("................", "................", "................", "......kkkkkk....", ".....kyyyyyyk...", "....kyyyyyyyk...", "...kyyyyyyygk...", "..kwwwwwwwggk...", ".kwwwwwwwggk....", ".kwwwwwwggk.....", ".kgggggggk......", "..kkkkkkk.......", "................", "................", "................", "................")),
    "toolFill" to PixelSpec(mapOf('k' to Color.Black, 'g' to hex(0xFFC0C0C0), 'c' to hex(0xFF28C0C8), 'w' to Color.White), listOf("......kk........", ".....k..k.......", "....k...k.......", "....k..kkk......", "...kk.kwwwk.....", "..kcckwwgggk....", ".kccckccgggk....", ".kcckcccggk.....", ".kcc.kccgk......", ".kcc..kggk......", "..kc...kk.......", "..kc............", "..kc............", "................", "................", "................")),
    "toolPicker" to PixelSpec(mapOf('k' to Color.Black, 'c' to hex(0xFF28C0C8), 'w' to Color.White, 'd' to hex(0xFF303030)), listOf("..........kkk...", ".........kdddk..", ".........kdddk..", "........kkddk...", ".......kwwkk....", "......kwwk......", ".....kwwk.......", "....kwwk........", "...kwwk.........", "..kwwk..........", "..kwk...........", ".kck............", ".cc.cccc........", "................", "................", "................")),
    "toolMagnifier" to PixelSpec(mapOf('k' to Color.Black, 'w' to Color.White, 'g' to hex(0xFFD0D8E0), 'b' to hex(0xFF3040C8)), listOf("................", "....kkkkkk......", "...kwwwwwwk.....", "..kwwggggwwk....", "..kwgwwwwgwk....", "..kwgwwwwgwk....", "..kwwggggwwk....", "...kwwwwwwk.....", "....kkkkkkbk....", ".........kbbk...", "..........kbbk..", "...........kbbk.", "............kbbk", ".............kk.", "................", "................")),
    "toolPencil" to PixelSpec(mapOf('k' to Color.Black, 'y' to hex(0xFFF0E040), 'p' to hex(0xFFE888A4), 'w' to Color.White), listOf("...........kk...", "..........kppk..", ".........kpppk..", "........kkykk...", ".......kyyyk....", "......kyyyk.....", ".....kyyyk......", "....kyyyk.......", "...kyyyk........", "..kwwwk.........", "..kwwk..........", ".kwwk...........", ".kkk............", ".k..............", "................", "................")),
    "toolBrush" to PixelSpec(mapOf('k' to Color.Black, 'y' to hex(0xFFD4C428), 'g' to hex(0xFFC0C0C0), 'w' to Color.White, 'd' to hex(0xFF808080)), listOf(".......kk.......", "......kyyk......", "......kyyk......", "......kyyk......", "......kyyk......", ".....kgyygk.....", "....kggggggk....", "....kkkkkkkk....", "....kwwwwwwk....", "....kwgwwgwk....", "....kwwwwwwk....", "....kwgwwgwk....", "....kddddddk....", "....kkkkkkkk....", "................", "................")),
    "toolSpray" to PixelSpec(mapOf('k' to Color.Black, 'b' to hex(0xFF2B38C0), 'g' to hex(0xFFC0C0C0), 'l' to hex(0xFF6B78E8)), listOf(".l.l.l..........", "..l.l...........", ".l.l...kkk......", "....l.kgggk.....", "..l...kggkkk....", ".......kkbbbk...", "......kbbbbbbk..", "......kbbbbbbk..", ".......kbbbbbbk.", "........kbbbbbk.", ".........kbbbbk.", "..........kbbk..", "...........kk...", "................", "................", "................")),
    "toolText" to PixelSpec(mapOf('k' to Color.Black), listOf("................", "................", ".......kk.......", "......kkkk......", "......k..k......", ".....kk..kk.....", ".....k....k.....", "....kk....kk....", "....kkkkkkkk....", "...kk......kk...", "...k........k...", "..kkk......kkk..", "..kkk......kkk..", "................", "................", "................")),
    "toolLine" to PixelSpec(mapOf('k' to Color.Black), listOf("................", "................", "..kk............", "...kk...........", "....kk..........", ".....kk.........", "......kk........", ".......kk.......", "........kk......", ".........kk.....", "..........kk....", "...........kk...", "............kk..", "................", "................", "................")),
    "toolCurve" to PixelSpec(mapOf('k' to Color.Black), listOf("................", ".........kk.....", "........k..k....", ".......k....k...", ".......k........", "........k.......", ".........kk.....", "...........k....", "...........k....", "..........k.....", "........kk......", ".......k........", ".......k....k...", "........kkkk....", "................", "................")),
    "toolRect" to PixelSpec(mapOf('k' to Color.Black), listOf("................", "................", "................", "..kkkkkkkkkkkk..", "..k..........k..", "..k..........k..", "..k..........k..", "..k..........k..", "..k..........k..", "..k..........k..", "..k..........k..", "..kkkkkkkkkkkk..", "................", "................", "................", "................")),
    "toolPolygon" to PixelSpec(mapOf('k' to Color.Black), listOf("................", "................", ".....kkkkkk.....", ".....k....k.....", "....k....k......", "....k...k.......", "...k....k.......", "...k.....kkkk...", "..k.........k...", "..k.........k...", "..kkkkkkkkkkk...", "................", "................", "................", "................", "................")),
    "toolEllipse" to PixelSpec(mapOf('k' to Color.Black), listOf("................", "................", "................", "................", "....kkkkkkkk....", "..kk........kk..", ".k............k.", ".k............k.", ".k............k.", ".k............k.", "..kk........kk..", "....kkkkkkkk....", "................", "................", "................", "................")),
    "toolRoundRect" to PixelSpec(mapOf('k' to Color.Black), listOf("................", "................", "................", "...kkkkkkkkkk...", "..k..........k..", ".k............k.", ".k............k.", ".k............k.", ".k............k.", ".k............k.", ".k............k.", "..k..........k..", "...kkkkkkkkkk...", "................", "................", "................"))
)

private val ICON_ALIASES = mapOf(
    "ie" to "globe",
    "habit" to "habits",
    "recorder" to "mic",
    "sweeper" to "heart",
    "speaker" to "speakerOn",
    "speakerMute" to "speakerOff",
    "status" to "taskmgr",
    "flag_heart" to "flag",
    "mine_heart" to "brokenHeart",
    "paint_freeselect" to "toolFreeSelect",
    "paint_select" to "toolSelect",
    "paint_eraser" to "toolEraser",
    "paint_bucket" to "toolFill",
    "paint_picker" to "toolPicker",
    "paint_zoom" to "toolMagnifier",
    "paint_pencil" to "toolPencil",
    "paint_brush" to "toolBrush",
    "paint_airbrush" to "toolSpray",
    "paint_text" to "toolText",
    "paint_line" to "toolLine",
    "paint_curve" to "toolCurve",
    "paint_rect" to "toolRect",
    "paint_polygon" to "toolPolygon",
    "paint_ellipse" to "toolEllipse",
    "paint_roundrect" to "toolRoundRect"
)

@Composable
fun PixelIcon(name: String, size: Dp = 32.dp, modifier: Modifier = Modifier) {
    val resolved = ICON_ALIASES[name] ?: name
    val spec = ICONS[resolved] ?: return
    Canvas(modifier = modifier.size(size)) {
        val h = spec.grid.size
        val w = spec.grid.firstOrNull()?.length ?: 16
        val cw = this.size.width / w
        val ch = this.size.height / h
        for (y in 0 until h) {
            val row = spec.grid[y]
            for (x in row.indices) {
                val color = spec.palette[row[x]] ?: continue
                drawRect(
                    color = color,
                    topLeft = Offset(x * cw, y * ch),
                    size = Size(cw + 0.5f, ch + 0.5f)
                )
            }
        }
    }
}
